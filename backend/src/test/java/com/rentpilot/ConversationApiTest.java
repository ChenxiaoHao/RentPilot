package com.rentpilot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.concurrent.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:conversation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class ConversationApiTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 String register() throws Exception { var r=mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"u"+UUID.randomUUID().toString().replace("-","").substring(0,12)+"\",\"password\":\"Test-pass-123\",\"nickname\":\"演示用户\"}")).andExpect(status().isCreated()).andReturn(); return "Bearer "+json.readTree(r.getResponse().getContentAsString()).get("token").asText(); }
 @Test void manualMessagingAndIsolation() throws Exception {
  String owner=register(), tenant=register(), stranger=register(), listing="SH-MOCK-000001", path="/api/demo/listings/"+listing;
  mvc.perform(get(path+"/owner")).andExpect(status().isUnauthorized());
  mvc.perform(get(path+"/owner").header("Authorization",tenant)).andExpect(jsonPath("$.claimed").value(false));
  mvc.perform(post("/api/conversations").header("Authorization",tenant).contentType("application/json").content("{\"listingId\":\""+listing+"\"}")).andExpect(status().isConflict());
  mvc.perform(post(path+"/claim").header("Authorization",owner)).andExpect(jsonPath("$.isMine").value(true));
  mvc.perform(post(path+"/claim").header("Authorization",owner)).andExpect(status().isOk());
  mvc.perform(post(path+"/claim").header("Authorization",stranger)).andExpect(status().isConflict());
  String body="{\"listingId\":\""+listing+"\"}";
  mvc.perform(post("/api/conversations").header("Authorization",owner).contentType("application/json").content(body)).andExpect(status().isConflict());
  var created=mvc.perform(post("/api/conversations").header("Authorization",tenant).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
  String id=json.readTree(created.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(post("/api/conversations").header("Authorization",tenant).contentType("application/json").content(body)).andExpect(jsonPath("$.id").value(id));
  mvc.perform(get("/api/conversations/"+id).header("Authorization",tenant)).andExpect(jsonPath("$.messages.length()").value(0));
  mvc.perform(post("/api/conversations/"+id+"/messages").header("Authorization",tenant).contentType("application/json").content("{\"content\":\"你好\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/conversations/"+id).header("Authorization",owner)).andExpect(jsonPath("$.messages.length()").value(1));
  mvc.perform(post("/api/conversations/"+id+"/messages").header("Authorization",owner).contentType("application/json").content("{\"content\":\"你好，请协商时间\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/conversations/"+id).header("Authorization",tenant)).andExpect(jsonPath("$.messages.length()").value(2));
  mvc.perform(get("/api/conversations/"+id).header("Authorization",stranger)).andExpect(status().isNotFound());
  mvc.perform(post("/api/conversations/"+id+"/messages").header("Authorization",stranger).contentType("application/json").content("{\"content\":\"越权\"}")).andExpect(status().isNotFound());
  mvc.perform(get("/api/conversations").header("Authorization",stranger)).andExpect(jsonPath("$.length()").value(0));
 }
 @Test void concurrentClaimsHaveOneWinner() throws Exception {
  String a=register(),b=register(),path="/api/demo/listings/SH-MOCK-000002/claim";
  var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
  try { var x=pool.submit(()->{gate.await(); return mvc.perform(post(path).header("Authorization",a)).andReturn().getResponse().getStatus();}); var y=pool.submit(()->{gate.await(); return mvc.perform(post(path).header("Authorization",b)).andReturn().getResponse().getStatus();}); gate.countDown(); assertThat(java.util.List.of(x.get(),y.get())).containsExactlyInAnyOrder(200,409); } finally { pool.shutdownNow(); }
 }
}
