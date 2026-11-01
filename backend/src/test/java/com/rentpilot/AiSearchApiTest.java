package com.rentpilot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:ai-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","rentpilot.ai.api-key=","rentpilot.ai.base-url=","rentpilot.ai.model="})
@AutoConfigureMockMvc
class AiSearchApiTest {
 @org.springframework.boot.test.mock.mockito.MockBean com.rentpilot.commute.CommuteGateway commuteGateway;
 @Autowired MockMvc mvc;
 @Test void missingConfigurationIsExplicit() throws Exception {
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"浦东租房\"}]}"))
   .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
 }
 @Test void rejectsOversizedOrInvalidConversations() throws Exception {
  String oversized="x".repeat(2001);
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\""+oversized+"\"}]}")) .andExpect(status().isBadRequest());
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"assistant\",\"content\":\"hello\"}]}")) .andExpect(status().isBadRequest());
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"system\",\"content\":\"hello\"}]}")) .andExpect(status().isBadRequest());
 }}

