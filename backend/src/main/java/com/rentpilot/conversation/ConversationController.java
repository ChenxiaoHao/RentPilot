package com.rentpilot.conversation;

import com.rentpilot.api.ApiException;
import com.rentpilot.member.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ConversationController {
    private final JdbcTemplate db; private final AuthService auth;
    public ConversationController(JdbcTemplate db, AuthService auth) { this.db=db; this.auth=auth; }
    private String member(String token) { return auth.requireMember(token); }
    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","房源或会话不存在",Map.of()); }
    private void listing(String id) { if (db.queryForObject("SELECT COUNT(*) FROM listing WHERE id=?",Integer.class,id)==0) throw missing(); }
    @GetMapping("/api/demo/listings/{id}/owner")
    public Map<String,Object> owner(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id) {
        String me=member(token); listing(id);
        var rows=db.queryForList("SELECT o.owner_id, a.nickname FROM demo_listing_owner o JOIN member_account a ON a.id=o.owner_id WHERE o.listing_id=?",id);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("claimed",!rows.isEmpty());
        result.put("ownerId",rows.isEmpty()?null:rows.getFirst().get("owner_id")); result.put("ownerNickname",rows.isEmpty()?null:rows.getFirst().get("nickname")); result.put("isMine",!rows.isEmpty() && me.equals(rows.getFirst().get("owner_id"))); return result;
    }
    @PostMapping("/api/demo/listings/{id}/claim")
    public Map<String,Object> claim(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id) {
        String me=member(token); listing(id);
        try { db.update("INSERT INTO demo_listing_owner(listing_id,owner_id) VALUES(?,?)",id,me); }
        catch(DataIntegrityViolationException e) { var state=owner(token,id); if (!Boolean.TRUE.equals(state.get("isMine"))) throw new ApiException(HttpStatus.CONFLICT,"ALREADY_CLAIMED","该模拟房源已有演示发布方",Map.of()); }
        return owner(token,id);
    }
    public record Start(String listingId) {} public record Input(String content) {}
    private Map<String,Object> conversation(String id,String me) {
        var rows=db.queryForList("SELECT c.id AS \"id\", c.listing_id AS \"listingId\", c.tenant_id AS \"tenantId\", c.owner_id AS \"ownerId\", o.nickname AS \"ownerNickname\", t.nickname AS \"tenantNickname\" FROM demo_conversation c JOIN member_account o ON o.id=c.owner_id JOIN member_account t ON t.id=c.tenant_id WHERE c.id=? AND (c.tenant_id=? OR c.owner_id=?)",id,me,me);
        if(rows.isEmpty()) throw missing(); return new LinkedHashMap<>(rows.getFirst());
    }
    @PostMapping("/api/conversations")
    public Map<String,Object> start(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Start input) {
        String me=member(token); if(input.listingId()==null) throw ApiException.invalid("listingId","请选择房源"); listing(input.listingId());
        var owners=db.queryForList("SELECT owner_id FROM demo_listing_owner WHERE listing_id=?",input.listingId());
        if(owners.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"NO_DEMO_OWNER","该模拟房源暂无演示发布方",Map.of());
        String owner=(String)owners.getFirst().get("owner_id"); if(owner.equals(me)) throw new ApiException(HttpStatus.CONFLICT,"OWN_LISTING","不能联系自己",Map.of());
        String id=UUID.randomUUID().toString(); try { db.update("INSERT INTO demo_conversation(id,listing_id,tenant_id,owner_id) VALUES(?,?,?,?)",id,input.listingId(),me,owner); } catch(DataIntegrityViolationException e) { id=db.queryForObject("SELECT id FROM demo_conversation WHERE listing_id=? AND tenant_id=?",String.class,input.listingId(),me); } return conversation(id,me);
    }
    @GetMapping("/api/conversations")
    public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false) String token) {
        String me=member(token); return db.queryForList("SELECT c.id AS \"id\", c.listing_id AS \"listingId\", c.tenant_id AS \"tenantId\", c.owner_id AS \"ownerId\", o.nickname AS \"ownerNickname\", t.nickname AS \"tenantNickname\" FROM demo_conversation c JOIN member_account o ON o.id=c.owner_id JOIN member_account t ON t.id=c.tenant_id WHERE c.tenant_id=? OR c.owner_id=? ORDER BY c.id",me,me);
    }
    @GetMapping("/api/conversations/{id}")
    public Map<String,Object> detail(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id) {
        String me=member(token); var result=conversation(id,me); result.put("messages",db.queryForList("SELECT id AS \"id\", sender_id AS \"senderId\", content AS \"content\", created_at AS \"createdAt\" FROM demo_message WHERE conversation_id=? ORDER BY created_at,id",id)); return result;
    }
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/api/conversations/{id}/messages")
    public Map<String,Object> send(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody Input input) {
        String me=member(token); conversation(id,me); String content=input.content()==null?"":input.content().strip(); if(content.isEmpty() || content.length()>2000) throw ApiException.invalid("content","消息长度须为1至2000个字符");
        db.queryForObject("SELECT id FROM demo_conversation WHERE id=? FOR UPDATE",String.class,id);
        var previous=db.query("SELECT MAX(created_at) FROM demo_message WHERE conversation_id=?",rs -> {rs.next(); return rs.getObject(1,java.time.OffsetDateTime.class);},id);
        var now=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS); if(previous!=null && !now.isAfter(previous)) now=previous.plusNanos(1000);
        String message=UUID.randomUUID().toString(); db.update("INSERT INTO demo_message(id,conversation_id,sender_id,content,created_at) VALUES(?,?,?,?,?)",message,id,me,content,now); return db.queryForMap("SELECT id AS \"id\", sender_id AS \"senderId\", content AS \"content\", created_at AS \"createdAt\" FROM demo_message WHERE id=?",message);
    }
}


