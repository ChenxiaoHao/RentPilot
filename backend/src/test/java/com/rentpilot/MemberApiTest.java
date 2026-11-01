package com.rentpilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:member-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class MemberApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final String HOUSE="SH-MOCK-000001";
    private String register() throws Exception {
        var response=mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"u"+UUID.randomUUID().toString().replace("-", "").substring(0,12)+"\",\"password\":\"Test-pass-123\",\"nickname\":\"租客\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(response).get("token").asText();
    }
    @Test void loginRequiredAndFavoritesArePrivateAndIdempotent() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        String a=register(),b=register();
        for(int i=0;i<2;i++) mvc.perform(put("/api/me/favorites/"+HOUSE).header("Authorization",a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/favorites").header("Authorization",a)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].listing.id").value(HOUSE));
        mvc.perform(get("/api/me/favorites").header("Authorization",b)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(put("/api/me/favorites/missing").header("Authorization",a)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/favorites/"+HOUSE).header("Authorization",a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization",a)).andExpect(jsonPath("$.favoritesCount").value(0));
    }
    @Test void historyIsUniqueAndClearOnlyAffectsCurrentUser() throws Exception {
        String a=register(),b=register();
        for(String token:new String[]{a,a,b}) mvc.perform(put("/api/me/history/"+HOUSE).header("Authorization",token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/history").header("Authorization",a)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(delete("/api/me/history").header("Authorization",a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/history").header("Authorization",a)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/me/history").header("Authorization",b)).andExpect(jsonPath("$.total").value(1));
    }
    @Test void viewingLifecycleRejectsCrossUserAndInvalidTransitions() throws Exception {
        String a=register(),b=register();
        String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
        String body="{\"listingId\":\""+HOUSE+"\",\"date\":\""+date+"\",\"slot\":\"AFTERNOON\"}";
        String response=mvc.perform(post("/api/me/viewings").header("Authorization",a).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("id").asText();
        mvc.perform(post("/api/me/viewings").header("Authorization",a).contentType("application/json").content(body)).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/me/viewings").header("Authorization",a)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(patch("/api/me/viewings/"+id+"/status").header("Authorization",b).contentType("application/json").content("{\"status\":\"CANCELLED\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/me/viewings/"+id+"/status").header("Authorization",a).contentType("application/json").content("{\"status\":\"COMPLETED\"}")).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(put("/api/me/viewings/"+id).header("Authorization",a).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/me").header("Authorization",a)).andExpect(jsonPath("$.plannedCount").value(0));
    }
    @Test void rejectsBadDatesAndInvalidBody() throws Exception {
        String a=register();
        mvc.perform(post("/api/me/viewings").header("Authorization",a).contentType("application/json").content("{\"listingId\":\""+HOUSE+"\",\"date\":\"2000-01-01\",\"slot\":\"AFTERNOON\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/me").header("Authorization",a).contentType("application/json").content("{\"nickname\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"x\",\"password\":\"123\"}")).andExpect(status().isBadRequest());
    }
    @Test void passwordsAndSessionsAreHashedAndLogoutRevokesOnlyItsSession() throws Exception {
        String first=register();
        var profile=json.readTree(mvc.perform(get("/api/me").header("Authorization",first)).andReturn().getResponse().getContentAsString());
        String username=profile.get("username").asText();
        String encoded=jdbc.queryForObject("SELECT password_hash FROM member_account WHERE username=?",String.class,username);
        assertThat(encoded).isNotEqualTo("Test-pass-123");
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("Test-pass-123",encoded)).isTrue();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM member_session WHERE member_id=?",String.class,profile.get("id").asText())).isNotEqualTo(first.substring(7)).hasSize(64);
        String loginBody=json.writeValueAsString(java.util.Map.of("username",username.toUpperCase(java.util.Locale.ROOT),"password","Test-pass-123"));
        String second="Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody)).andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist()).andReturn().getResponse().getContentAsString()).get("token").asText();
        assertThat(second).isNotEqualTo(first);
        mvc.perform(post("/api/auth/logout").header("Authorization",first)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization",first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization",second)).andExpect(status().isOk());
    }
    @Test void duplicateUsernameWrongPasswordAndExpiredTokenAreRejected() throws Exception {
        String token=register();
        var me=json.readTree(mvc.perform(get("/api/me").header("Authorization",token)).andReturn().getResponse().getContentAsString());
        String username=me.get("username").asText();
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username",username.toUpperCase(java.util.Locale.ROOT),"password","Test-pass-123")))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username",username,"password","wrong-password")))).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE member_session SET expires_at=? WHERE member_id=?",java.time.OffsetDateTime.now().minusDays(1),me.get("id").asText());
        mvc.perform(get("/api/me/favorites").header("Authorization",token)).andExpect(status().isUnauthorized());
    }
    @Test void statusFiltersAndEditsUseRealOwnedPlanData() throws Exception {
        String token=register(),date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2).toString();
        String body=json.writeValueAsString(java.util.Map.of("listingId",HOUSE,"date",date,"slot","MORNING"));
        var created=json.readTree(mvc.perform(post("/api/me/viewings").header("Authorization",token).contentType("application/json").content(body)).andReturn().getResponse().getContentAsString());
        String id=created.get("id").asText();
        mvc.perform(put("/api/me/viewings/"+id).header("Authorization",token).contentType("application/json").content(body.replace("MORNING","EVENING"))).andExpect(status().isOk()).andExpect(jsonPath("$.slot").value("EVENING"));
        mvc.perform(patch("/api/me/viewings/"+id+"/status").header("Authorization",token).contentType("application/json").content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/me/viewings?status=PLANNED").header("Authorization",token)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/me/viewings?status=CANCELLED").header("Authorization",token)).andExpect(jsonPath("$.items[0].slot").value("EVENING"));
        mvc.perform(get("/api/me/viewings?status=UNKNOWN").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/favorites?size=0").header("Authorization",token)).andExpect(status().isBadRequest());
    }
    @Test void concurrentFavoritesAndPlansStayUnique() throws Exception {
        String token=register();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var jobs=new java.util.ArrayList<java.util.concurrent.Callable<Integer>>();
            for(int i=0;i<6;i++)jobs.add(()->mvc.perform(put("/api/me/favorites/"+HOUSE).header("Authorization",token)).andReturn().getResponse().getStatus());
            for(var future:executor.invokeAll(jobs))assertThat(future.get()).isEqualTo(204);
            mvc.perform(get("/api/me/favorite-ids").header("Authorization",token)).andExpect(jsonPath("$.length()").value(1));
            String body=json.writeValueAsString(java.util.Map.of("listingId",HOUSE,"date",LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString(),"slot","EVENING"));
            jobs.clear();for(int i=0;i<6;i++)jobs.add(()->mvc.perform(post("/api/me/viewings").header("Authorization",token).contentType("application/json").content(body)).andReturn().getResponse().getStatus());
            for(var future:executor.invokeAll(jobs))assertThat(future.get()).isEqualTo(200);
            mvc.perform(get("/api/me/viewings").header("Authorization",token)).andExpect(jsonPath("$.total").value(1));
        } finally {executor.shutdownNow();}
    }
}
