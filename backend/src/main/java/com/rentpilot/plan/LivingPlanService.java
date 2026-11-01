package com.rentpilot.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rentpilot.ai.AiSearchService;
import com.rentpilot.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static com.rentpilot.plan.PlanModels.*;

@Service
public class LivingPlanService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiSearchService search;
    public LivingPlanService(JdbcTemplate jdbc,ObjectMapper json,AiSearchService search){this.jdbc=jdbc;this.json=json;this.search=search;}

    public List<PlanSummary> list(String memberId){
        return jdbc.query("""
                SELECT p.id,p.title,p.updated_at,v.id AS version_id,v.version_no,v.primary_listing_id,
                       v.primary_home_name,v.primary_district,v.primary_town,v.primary_rent,
                       v.primary_commute_minutes,v.home_count,v.coverage,v.captured_at
                  FROM living_plan p JOIN living_plan_version v ON v.id=p.active_version_id
                 WHERE p.member_id=? ORDER BY p.updated_at DESC,p.id
                """,(rs,n)->new PlanSummary(rs.getString("id"),rs.getString("version_id"),rs.getInt("version_no"),
                rs.getString("title"),rs.getString("primary_listing_id"),rs.getString("primary_home_name"),
                rs.getString("primary_district"),rs.getString("primary_town"),rs.getBigDecimal("primary_rent"),
                (Integer)rs.getObject("primary_commute_minutes"),rs.getInt("home_count"),rs.getString("coverage"),
                rs.getObject("captured_at",OffsetDateTime.class).toString(),rs.getObject("updated_at",OffsetDateTime.class).toString()),
                memberId);
    }

    public PlanDetail detail(String memberId,String planId){
        var plan=planRow(memberId,planId);
        var snapshot=readSnapshot(plan.versionId());
        return new PlanDetail(plan,versions(memberId,planId),snapshot);
    }

    public List<VersionSummary> versions(String memberId,String planId){
        requirePlan(memberId,planId);
        return jdbc.query("SELECT id,version_no,home_count,coverage,captured_at FROM living_plan_version WHERE plan_id=? ORDER BY version_no DESC",
                (rs,n)->new VersionSummary(rs.getString("id"),rs.getInt("version_no"),rs.getInt("home_count"),
                        rs.getString("coverage"),rs.getObject("captured_at",OffsetDateTime.class).toString()),planId);
    }

    @Transactional
    public PlanDetail save(String memberId,SaveInput input){
        JsonNode source=search.snapshot(input.searchToken());
        var all=(ArrayNode)source.path("recommendations");
        var selected=pick(all,input.listingIds());
        var now=OffsetDateTime.now();
        var snapshot=buildSnapshot(source,selected,now);
        var primary=selected.get(0);
        var listing=primary.path("listing");
        String title=title(input.title(),listing.path("community").asText(listing.path("name").asText("AI 方案")),selected.size());
        String versionId=UUID.randomUUID().toString();
        String planId=input.planId();
        String coverage=coverage(snapshot);

        if(planId==null||planId.isBlank()){
            planId=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO living_plan(id,member_id,title,active_version_id) VALUES(?,?,?,?)",planId,memberId,title,versionId);
            jdbc.update("""
                    INSERT INTO living_plan_version(id,plan_id,version_no,snapshot_json,primary_listing_id,primary_home_name,
                     primary_district,primary_town,primary_rent,primary_commute_minutes,home_count,coverage,captured_at)
                    VALUES(?,?,1,?,?,?,?,?,?,?,?,?,?)
                    """,versionId,planId,write(snapshot),listing.path("id").asText(),
                    listing.path("name").asText(),listing.path("district").asText(),listing.path("town").asText(),
                    decimal(listing,"rent"),commuteMinutes(primary),selected.size(),coverage,now);
        } else {
            var existing=jdbc.queryForList("SELECT title FROM living_plan WHERE id=? AND member_id=? FOR UPDATE",planId,memberId);
            if(existing.isEmpty())throw notFound();
            title=input.title()==null||input.title().isBlank()?(String)existing.getFirst().get("title"):title;
            int versionNo=Objects.requireNonNull(jdbc.queryForObject(
                    "SELECT COALESCE(MAX(version_no),0)+1 FROM living_plan_version WHERE plan_id=?",Integer.class,planId));
            jdbc.update("""
                    INSERT INTO living_plan_version(id,plan_id,version_no,snapshot_json,primary_listing_id,primary_home_name,
                     primary_district,primary_town,primary_rent,primary_commute_minutes,home_count,coverage,captured_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,versionId,planId,versionNo,write(snapshot),listing.path("id").asText(),
                    listing.path("name").asText(),listing.path("district").asText(),listing.path("town").asText(),
                    decimal(listing,"rent"),commuteMinutes(primary),selected.size(),coverage,now);
            jdbc.update("UPDATE living_plan SET active_version_id=?,title=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND member_id=?",versionId,title,planId,memberId);
            return detail(memberId,planId);
        }
        return detail(memberId,planId);
    }

    @Transactional
    public PlanDetail activate(String memberId,String planId,String versionId){
        requirePlan(memberId,planId);
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM living_plan_version WHERE id=? AND plan_id=?",Integer.class,versionId,planId);
        if(count==null||count==0)throw new ApiException(HttpStatus.NOT_FOUND,"PLAN_VERSION_NOT_FOUND","未找到这个方案版本",Map.of());
        jdbc.update("UPDATE living_plan SET active_version_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND member_id=?",versionId,planId,memberId);
        return detail(memberId,planId);
    }

    @Transactional
    public void delete(String memberId,String planId){
        int removed=jdbc.update("DELETE FROM living_plan WHERE id=? AND member_id=?",planId,memberId);
        if(removed==0)throw notFound();
    }

    public PlanComparison compare(String memberId,String leftId,String rightId){
        if(leftId.equals(rightId))throw ApiException.invalid("right","请选择两个不同的方案");
        var left=side(memberId,leftId);
        var right=side(memberId,rightId);
        var notes=new ArrayList<String>();
        if(!left.capturedAt().equals(right.capturedAt()))notes.add("两个方案保存于不同时间，数值是各自保存时的快照，不代表当前实时结果。");
        if(left.partialCoverage()||right.partialCoverage())notes.add("至少一侧只核验了部分候选房源，结果不代表全量。");
        if(left.commuteMinutes()==null||right.commuteMinutes()==null)notes.add("至少一侧没有已核验的通勤时间，无法比较通勤差。");
        if(left.knownInitialPayment()==null||right.knownInitialPayment()==null)notes.add("至少一侧缺少已确认的首期费用，无法比较首期差。");
        notes.add("房源为模拟数据，室内照片与小区位置均为示意。");
        return new PlanComparison(left,right,
                left.rent()==null||right.rent()==null?null:left.rent().subtract(right.rent()),
                left.commuteMinutes()==null||right.commuteMinutes()==null?null:left.commuteMinutes()-right.commuteMinutes(),
                left.knownInitialPayment()==null||right.knownInitialPayment()==null?null:left.knownInitialPayment().subtract(right.knownInitialPayment()),
                List.copyOf(notes));
    }

    private CompareSide side(String memberId,String planId){
        var plan=planRow(memberId,planId);
        var snapshot=readSnapshot(plan.versionId());
        var primary=snapshot.path("recommendations").path(0);
        var listing=primary.path("listing");
        var commute=snapshot.path("commute");
        var costs=primary.path("insight").path("costs");
        Integer minutes=commuteMinutes(primary);
        String mode=commute.path("mode").asText(null);
        if(mode==null)mode=primary.path("commute").path("mode").asText(null);
        String checkedAt=primary.path("commute").path("checkedAt").asText(null);
        return new CompareSide(plan.id(),plan.versionId(),plan.versionNo(),plan.title(),plan.capturedAt(),
                listing.path("id").asText(),listing.path("name").asText(),listing.path("district").asText(),
                listing.path("town").asText(),decimal(listing,"rent"),decimal(listing,"deposit"),
                listing.path("paymentMethod").asText(null),decimal(listing,"propertyFee"),
                listing.path("propertyFeePeriod").asText(null),decimal(costs,"knownInitialPayment"),
                mode,minutes,checkedAt,plan.coverage(),plan.homeCount(),"PARTIAL".equals(plan.coverage()));
    }

    private PlanSummary planRow(String memberId,String planId){
        var rows=jdbc.query("""
                SELECT p.id,p.title,p.updated_at,v.id AS version_id,v.version_no,v.primary_listing_id,
                       v.primary_home_name,v.primary_district,v.primary_town,v.primary_rent,
                       v.primary_commute_minutes,v.home_count,v.coverage,v.captured_at
                  FROM living_plan p JOIN living_plan_version v ON v.id=p.active_version_id
                 WHERE p.id=? AND p.member_id=?
                """,(rs,n)->new PlanSummary(rs.getString("id"),rs.getString("version_id"),rs.getInt("version_no"),
                rs.getString("title"),rs.getString("primary_listing_id"),rs.getString("primary_home_name"),
                rs.getString("primary_district"),rs.getString("primary_town"),rs.getBigDecimal("primary_rent"),
                (Integer)rs.getObject("primary_commute_minutes"),rs.getInt("home_count"),rs.getString("coverage"),
                rs.getObject("captured_at",OffsetDateTime.class).toString(),rs.getObject("updated_at",OffsetDateTime.class).toString()),
                planId,memberId);
        if(rows.isEmpty())throw notFound();
        return rows.getFirst();
    }

    private void requirePlan(String memberId,String planId){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM living_plan WHERE id=? AND member_id=?",Integer.class,planId,memberId);
        if(count==null||count==0)throw notFound();
    }

    private JsonNode readSnapshot(String versionId){
        String raw=jdbc.queryForObject("SELECT snapshot_json FROM living_plan_version WHERE id=?",String.class,versionId);
        try{return json.readTree(raw);}catch(Exception e){throw new IllegalStateException("Stored plan snapshot is invalid",e);}
    }

    private ArrayNode pick(ArrayNode all,List<String> requested){
        if(all==null||all.isEmpty())throw new ApiException(HttpStatus.CONFLICT,"PLAN_SOURCE_NOT_SAVEABLE","当前搜索结果没有可保存的房源",Map.of());
        var wanted=requested==null||requested.isEmpty()?null:new HashSet<>(requested);
        var selected=json.createArrayNode();
        for(var node:all){
            if(wanted==null||wanted.contains(node.path("listing").path("id").asText()))selected.add(node.deepCopy());
        }
        if(wanted!=null){
            var seen=new HashSet<String>();
            for(var node:selected)seen.add(node.path("listing").path("id").asText());
            if(seen.size()!=wanted.size())throw new ApiException(HttpStatus.BAD_REQUEST,"PLAN_HOME_NOT_FOUND","只能保存本次结果中已展示的房源",Map.of());
        }
        if(selected.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"PLAN_HOME_NOT_FOUND","请选择至少一套房源",Map.of());
        return selected;
    }

    private ObjectNode buildSnapshot(JsonNode source,ArrayNode selected,OffsetDateTime now){
        var doc=(ObjectNode)source.deepCopy();
        doc.set("recommendations",selected);
        doc.put("capturedAt",now.toString());
        doc.put("snapshotKind","HISTORICAL");
        doc.put("realtime",false);
        if(!doc.has("commute")){
            var limits=new ArrayList<String>();
            doc.path("limitations").forEach(n->limits.add(n.asText()));
            if(limits.stream().noneMatch(x->x.contains("通勤")))limits.add("本方案未核验工作地通勤时间。");
            doc.set("limitations",json.valueToTree(limits));
        }
        return doc;
    }

    private String coverage(JsonNode snapshot){
        return "PARTIAL".equals(snapshot.path("commute").path("coverage").asText())?"PARTIAL":"COMPLETE";
    }

    private Integer commuteMinutes(JsonNode recommendation){
        var node=recommendation.path("commute").path("minutes");
        return node.isNumber()?node.asInt():null;
    }

    private BigDecimal decimal(JsonNode node,String field){
        var value=node.path(field);
        return value.isNumber()?value.decimalValue():null;
    }

    private String write(JsonNode node){
        try{return json.writeValueAsString(node);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Unable to store plan snapshot",e);}
    }

    private String title(String requested,String fallback,int homes){
        String base=requested==null?"":requested.strip();
        if(base.isEmpty())base=homes>1?fallback+" 等 "+homes+" 套":fallback;
        return base.length()>60?base.substring(0,60):base;
    }

    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"PLAN_NOT_FOUND","未找到这个方案",Map.of());}
}