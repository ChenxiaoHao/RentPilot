package com.rentpilot.listing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:contact-roundtrip;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class ContactRoundTripTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.rentpilot.listing.MetroLineService metro;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired org.springframework.jdbc.core.JdbcTemplate db;
 String account() throws Exception {var r=mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"u"+UUID.randomUUID().toString().replace("-","").substring(0,12)+"\",\"password\":\"Test-pass-123\",\"nickname\":\"用户\"}")).andExpect(status().isCreated()).andReturn();return "Bearer "+json.readTree(r.getResponse().getContentAsString()).get("token").asText();}
 @Test void accountsExchangeOnlyTheirOwnMessages() throws Exception {
  String owner=account(),tenant=account(),other=account(),listing="SH-MOCK-000001";
  mvc.perform(post("/api/demo/listings/"+listing+"/claim").header("Authorization",owner)).andExpect(status().isOk());
  var r=mvc.perform(post("/api/conversations").header("Authorization",tenant).contentType("application/json").content("{\"listingId\":\""+listing+"\"}")).andExpect(status().isOk()).andReturn();
  String id=json.readTree(r.getResponse().getContentAsString()).get("id").asText(), path="/api/conversations/"+id;
  mvc.perform(post(path+"/messages").header("Authorization",tenant).contentType("application/json").content("{\"content\":\"请问何时看房？\"}")).andExpect(status().isOk());
  mvc.perform(get(path).header("Authorization",owner)).andExpect(jsonPath("$.messages.length()").value(1)).andExpect(jsonPath("$.messages[0].content").value("请问何时看房？"));
  db.update("UPDATE demo_message SET created_at=? WHERE conversation_id=?", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(1),id);
  mvc.perform(post(path+"/messages").header("Authorization",owner).contentType("application/json").content("{\"content\":\"明天可以\"}")).andExpect(status().isOk());
  mvc.perform(get(path).header("Authorization",tenant)).andExpect(jsonPath("$.messages.length()").value(2)).andExpect(jsonPath("$.messages[1].content").value("明天可以"));
  mvc.perform(get(path).header("Authorization",other)).andExpect(status().isNotFound());
  mvc.perform(get("/api/conversations").header("Authorization",other)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
 }
}