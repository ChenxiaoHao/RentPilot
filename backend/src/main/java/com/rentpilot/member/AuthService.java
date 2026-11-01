package com.rentpilot.member;

import com.rentpilot.api.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import static com.rentpilot.member.MemberModels.*;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final SecureRandom random=new SecureRandom();
    private final String dummyHash=passwords.encode("timing-only-not-an-account");
    public AuthService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public Session register(Credentials input) {
        validatePassword(input.password());
        String username=input.username().toLowerCase(Locale.ROOT),id=UUID.randomUUID().toString();
        String nickname=input.nickname()==null || input.nickname().isBlank()?"租客":input.nickname().strip();
        try { jdbc.update("INSERT INTO member_account(id,username,password_hash,nickname) VALUES(?,?,?,?)",id,username,passwords.encode(input.password()),nickname); }
        catch(DataIntegrityViolationException e){throw new ApiException(HttpStatus.CONFLICT,"USERNAME_TAKEN","这个用户名已被使用",Map.of("username","请换一个用户名"));}
        return issueSession(id);
    }
    @Transactional
    public Session login(Credentials input) {
        validatePassword(input.password());
        var rows=jdbc.queryForList("SELECT id,password_hash FROM member_account WHERE username=?",input.username().toLowerCase(Locale.ROOT));
        String encoded=rows.isEmpty()?dummyHash:(String)rows.getFirst().get("password_hash");
        boolean matches=passwords.matches(input.password(),encoded);
        if(rows.isEmpty() || !matches)throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","用户名或密码不正确",Map.of());
        return issueSession((String)rows.getFirst().get("id"));
    }
    private Session issueSession(String memberId) {
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expires=OffsetDateTime.now().plusDays(30);
        jdbc.update("DELETE FROM member_session WHERE expires_at<CURRENT_TIMESTAMP");
        jdbc.update("INSERT INTO member_session(token_hash,member_id,expires_at) VALUES(?,?,?)",hash(token),memberId,expires);
        return new Session(token,expires,profile(memberId));
    }
    public String requireMember(String authorization) {
        String token=readToken(authorization);
        var rows=jdbc.queryForList("SELECT member_id FROM member_session WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP",hash(token));
        if(rows.isEmpty())throw new ApiException(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED","登录已失效，请重新登录",Map.of());
        return (String)rows.getFirst().get("member_id");
    }
    public void logout(String authorization) {jdbc.update("DELETE FROM member_session WHERE token_hash=?",hash(readToken(authorization)));}
    private String readToken(String authorization) {
        if(authorization==null || !authorization.matches("Bearer [A-Za-z0-9_-]{43}"))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"LOGIN_REQUIRED","请先登录",Map.of());
        return authorization.substring(7);
    }
    public Profile profile(String memberId) {
        return jdbc.queryForObject("""
                SELECT a.*,(SELECT COUNT(*) FROM favorite_listing f WHERE f.member_id=a.id) AS favorites,
                (SELECT COUNT(*) FROM browsing_history h WHERE h.member_id=a.id) AS history,
                (SELECT COUNT(*) FROM viewing_plan v WHERE v.member_id=a.id AND v.status='PLANNED') AS planned
                FROM member_account a WHERE id=?
                """,(rs,n)->new Profile(rs.getString("id"),rs.getString("username"),rs.getString("nickname"),rs.getObject("created_at",OffsetDateTime.class),rs.getInt("favorites"),rs.getInt("history"),rs.getInt("planned")),memberId);
    }
    public Profile rename(String memberId,String nickname){jdbc.update("UPDATE member_account SET nickname=? WHERE id=?",nickname.strip(),memberId);return profile(memberId);}
    private void validatePassword(String password) {
        if(password.codePointCount(0,password.length())<8 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw ApiException.invalid("password","密码至少8个字符，UTF-8编码不超过72字节");
    }
    private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
