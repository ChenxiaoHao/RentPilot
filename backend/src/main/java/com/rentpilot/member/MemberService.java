package com.rentpilot.member;

import com.rentpilot.api.ApiException;
import com.rentpilot.listing.ListingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.rentpilot.member.MemberModels.*;

@Service
public class MemberService {
    private final JdbcTemplate jdbc;
    private final ListingRepository listings;
    public MemberService(JdbcTemplate jdbc,ListingRepository listings){this.jdbc=jdbc;this.listings=listings;}
    private String table(String kind){return switch(kind){case "favorites"->"favorite_listing";case "history"->"browsing_history";default->throw new IllegalArgumentException("Unknown collection");};}
    private void lock(String memberId){jdbc.queryForObject("SELECT id FROM member_account WHERE id=? FOR UPDATE",String.class,memberId);}
    public Page<SavedListing> saved(String memberId,String kind,int page,int size){
        String table=table(kind);
        int total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE member_id=?",Integer.class,memberId));
        var rows=jdbc.query("SELECT listing_id,recorded_at FROM "+table+" WHERE member_id=? ORDER BY recorded_at DESC,listing_id ASC LIMIT ? OFFSET ?",
                (rs,n)->Map.entry(rs.getString("listing_id"),rs.getObject("recorded_at",OffsetDateTime.class)),memberId,size,(long)page*size);
        var homes=listings.byIds(rows.stream().map(Map.Entry::getKey).toList());
        var times=new HashMap<String,OffsetDateTime>();rows.forEach(r->times.put(r.getKey(),r.getValue()));
        return new Page<>(homes.stream().map(h->new SavedListing(h,times.get(h.id()))).toList(),page,size,total,(total+size-1)/size);
    }
    @Transactional public void save(String memberId,String kind,String listingId){
        listings.detail(listingId);lock(memberId);
        String table=table(kind);
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE member_id=? AND listing_id=?",Integer.class,memberId,listingId);
        if(count!=null && count>0){if(kind.equals("history"))jdbc.update("UPDATE browsing_history SET recorded_at=CURRENT_TIMESTAMP WHERE member_id=? AND listing_id=?",memberId,listingId);return;}
        jdbc.update("INSERT INTO "+table+"(member_id,listing_id) VALUES(?,?)",memberId,listingId);
    }
    @Transactional public void unsave(String memberId,String listingId){lock(memberId);jdbc.update("DELETE FROM favorite_listing WHERE member_id=? AND listing_id=?",memberId,listingId);}
    @Transactional public void clearHistory(String memberId){lock(memberId);jdbc.update("DELETE FROM browsing_history WHERE member_id=?",memberId);}
    public List<String> favoriteIds(String memberId){return jdbc.queryForList("SELECT listing_id FROM favorite_listing WHERE member_id=? ORDER BY recorded_at DESC,listing_id",String.class,memberId);}
    public Page<Viewing> viewings(String memberId,int page,int size,String status){
        String where=" WHERE member_id=?"+(status==null?"":" AND status=?");
        var args=new ArrayList<Object>();args.add(memberId);if(status!=null)args.add(status);
        int total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM viewing_plan"+where,Integer.class,args.toArray()));
        args.add(size);args.add((long)page*size);
        var items=jdbc.query("""
                SELECT * FROM viewing_plan
                """+where+"""
                 ORDER BY CASE WHEN status='PLANNED' THEN 0 ELSE 1 END,
                CASE WHEN status='PLANNED' THEN planned_date ELSE NULL END ASC,updated_at DESC,id LIMIT ? OFFSET ?
                """,(rs,n)->new Viewing(rs.getString("id"),listings.detail(rs.getString("listing_id")),rs.getObject("planned_date",LocalDate.class),rs.getString("slot"),rs.getString("status"),rs.getObject("updated_at",OffsetDateTime.class)),args.toArray());
        return new Page<>(items,page,size,total,(total+size-1)/size);
    }
    @Transactional public Viewing plan(String memberId,ViewingInput input){
        validateDate(input);listings.detail(input.listingId());lock(memberId);
        var existing=jdbc.queryForList("SELECT id FROM viewing_plan WHERE member_id=? AND listing_id=? AND status='PLANNED'",memberId,input.listingId());
        String id=existing.isEmpty()?UUID.randomUUID().toString():(String)existing.getFirst().get("id");
        if(existing.isEmpty())jdbc.update("INSERT INTO viewing_plan(id,member_id,listing_id,planned_date,slot,status) VALUES(?,?,?,?,?,'PLANNED')",id,memberId,input.listingId(),input.date(),input.slot());
        else jdbc.update("UPDATE viewing_plan SET planned_date=?,slot=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.date(),input.slot(),id);
        return viewing(memberId,id);
    }
    @Transactional public Viewing edit(String memberId,String id,ViewingInput input){
        validateDate(input);lock(memberId);Viewing current=viewing(memberId,id);
        if(!current.status().equals("PLANNED"))throw conflict();
        if(!current.listing().id().equals(input.listingId()))throw ApiException.invalid("listingId","不能修改计划对应的房源");
        jdbc.update("UPDATE viewing_plan SET planned_date=?,slot=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND member_id=?",input.date(),input.slot(),id,memberId);
        return viewing(memberId,id);
    }
    @Transactional public Viewing status(String memberId,String id,String next){
        lock(memberId);Viewing current=viewing(memberId,id);
        if(current.status().equals(next))return current;
        if(!current.status().equals("PLANNED"))throw conflict();
        jdbc.update("UPDATE viewing_plan SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND member_id=?",next,id,memberId);
        return viewing(memberId,id);
    }
    private Viewing viewing(String memberId,String id){
        var rows=jdbc.query("SELECT * FROM viewing_plan WHERE id=? AND member_id=?",(rs,n)->new Viewing(rs.getString("id"),listings.detail(rs.getString("listing_id")),rs.getObject("planned_date",LocalDate.class),rs.getString("slot"),rs.getString("status"),rs.getObject("updated_at",OffsetDateTime.class)),id,memberId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"VIEWING_NOT_FOUND","未找到这条看房计划",Map.of());
        return rows.getFirst();
    }
    private ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"VIEWING_CLOSED","这条计划已结束，请重新安排",Map.of());}
    private void validateDate(ViewingInput input){
        ZonedDateTime now=ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));LocalDate today=now.toLocalDate();
        if(input.date().isBefore(today)||input.date().isAfter(today.plusDays(60)))throw ApiException.invalid("date","请选择今天起60天内的日期");
        LocalTime time=switch(input.slot()){case "MORNING"->LocalTime.of(10,0);case "AFTERNOON"->LocalTime.of(14,0);case "EVENING"->LocalTime.of(18,30);default->throw ApiException.invalid("slot","不支持的时段");};
        if(input.date().equals(today)&&!time.isAfter(now.toLocalTime()))throw ApiException.invalid("slot","该时段已过去，请选择其他时间");
    }
}
