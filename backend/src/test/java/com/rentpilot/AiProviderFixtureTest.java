package com.rentpilot;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:ai-fixture;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class AiProviderFixtureTest {
 static volatile java.util.concurrent.CountDownLatch entered, release; static java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger(); static java.util.concurrent.atomic.AtomicInteger httpStatus=new java.util.concurrent.atomic.AtomicInteger(200); static HttpServer server; static AtomicReference<String> result=new AtomicReference<>(), received=new AtomicReference<>(), auth=new AtomicReference<>(), finish=new AtomicReference<>("stop");
 static { try {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.createContext("/custom/chat", exchange->{calls.incrementAndGet(); if(entered!=null){entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}} auth.set(exchange.getRequestHeaders().getFirst("Authorization")); received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); var body=new ObjectMapper().writeValueAsBytes(java.util.Map.of("id","fixture","object","chat.completion","created",1,"model","fixture","choices",java.util.List.of(java.util.Map.of("index",0,"message",java.util.Map.of("role","assistant","content",result.get()),"finish_reason",finish.get())))); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(httpStatus.get(),body.length); exchange.getResponseBody().write(body);exchange.close();});server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r->{var t=new Thread(r);t.setDaemon(true);return t;}));server.start();}catch(Exception e){throw new RuntimeException(e);} }
 @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("rentpilot.ai.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort());p.add("rentpilot.ai.api-key",()->"fixture-secret");p.add("rentpilot.ai.model",()->"fixture");p.add("rentpilot.ai.completions-path",()->"/custom/chat");}
 @org.springframework.boot.test.mock.mockito.MockBean com.rentpilot.commute.CommuteGateway commuteGateway;
 @Autowired MockMvc mvc;
 @Autowired org.springframework.context.ApplicationContext context;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Test void explanationValidatesAndCaches() throws Exception {
  result.set("{\"criteria\":{\"district\":\"闵行区\",\"maxRent\":3000},\"unsupported\":false,\"commuteRequested\":false}");
  var search=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"search\"}]}")).andExpect(status().isOk()).andReturn();
  var tree=new ObjectMapper().readTree(search.getResponse().getContentAsString());var token=tree.path("searchToken").asText();var id=tree.path("recommendations").get(0).path("listing").path("id").asText();var request="{\"searchToken\":\""+token+"\",\"listingId\":\""+id+"\"}";
  mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"searchToken\":\""+token+"\",\"listingId\":\""+id+"\",\"rent\":1}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"searchToken\":\"bad\",\"listingId\":\""+id+"\"}")).andExpect(status().isConflict());
  mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"searchToken\":\""+token+"\",\"listingId\":\"not-present\"}")).andExpect(status().isBadRequest());
  for(var bad:java.util.List.of("{}","{\"evidenceIds\":[]}","{\"evidenceIds\":[\"rent\",\"rent\"]}","{\"evidenceIds\":[\"fake\"]}","{\"evidenceIds\":[\"propertyFee\"]}","{\"evidenceIds\":[\"rent\"],\"summary\":\"100000\"}","{\"evidenceIds\":[\"rent\"]} true","{\"evidenceIds\":[\"rent\"],\"evidenceIds\":[\"rent\"]}")){result.set(bad);mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_EXPLANATION_UNAVAILABLE"));}
  result.set("{\"evidenceIds\":[\"rent\"]}");var response=mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.engine").value("SPRING_AI")).andReturn().getResponse().getContentAsString();result.set("invalid");assertThat(mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(response);
 }
 @Test void explanationIsIsolatedAcrossTicketsAndDoesNotHoldGlobalLock() throws Exception {
  String search="{\"messages\":[{\"role\":\"user\",\"content\":\"search\"}]}";
  result.set("{\"criteria\":{\"district\":\"闵行区\",\"maxRent\":3000},\"unsupported\":false,\"commuteRequested\":false}");
  var first=new ObjectMapper().readTree(mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(search)).andReturn().getResponse().getContentAsString());
  var token=first.path("searchToken").asText();var id=first.path("recommendations").get(0).path("listing").path("id").asText();var request="{\"searchToken\":\""+token+"\",\"listingId\":\""+id+"\"}";
  int before=calls.get();mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"searchToken\":\""+token+"\",\"listingId\":\"missing\"}")).andExpect(status().isBadRequest());assertThat(calls.get()).isEqualTo(before);
  result.set("{\"evidenceIds\":[\"rent\"]}");entered=new java.util.concurrent.CountDownLatch(1);release=new java.util.concurrent.CountDownLatch(1);
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();try {var pending=executor.submit(()->mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content(request)).andReturn());assertThat(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();entered=null;
   result.set("{\"criteria\":{\"district\":\"闵行区\",\"maxRent\":4000},\"unsupported\":false,\"commuteRequested\":false}");
   var second=new ObjectMapper().readTree(mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(search)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(second.path("searchToken").asText()).isNotEqualTo(token);
   result.set("{\"evidenceIds\":[\"rent\"]}");release.countDown();assertThat(pending.get(3,java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
   var secondId=second.path("recommendations").get(0).path("listing").path("id").asText();before=calls.get();mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"searchToken\":\""+second.path("searchToken").asText()+"\",\"listingId\":\""+secondId+"\"}")).andExpect(status().isOk());assertThat(calls.get()).isEqualTo(before+1);
  } finally {release.countDown();entered=null;executor.shutdownNow();}
 }
 @Test void blockedContinuationDoesNotBlockOtherTickets() throws Exception {
  var place=new com.rentpilot.commute.Workplace("lock-place","工作地","上海闵行","闵行区","310112",121.47,31.04);
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(place));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now())));
  result.set("{\"criteria\":{\"maxRent\":3000},\"unsupported\":false,\"workplace\":\"工作地\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  String search="{\"messages\":[{\"role\":\"user\",\"content\":\"search\"}]}";var first=new ObjectMapper().readTree(mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(search)).andReturn().getResponse().getContentAsString());assertThat(first.path("hasMore").asBoolean()).isTrue();
  result.set("{\"criteria\":{\"district\":\"闵行区\",\"maxRent\":3000},\"unsupported\":false,\"commuteRequested\":false}");var other=new ObjectMapper().readTree(mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(search)).andReturn().getResponse().getContentAsString());
  var enteredRoute=new java.util.concurrent.CountDownLatch(1);var releaseRoute=new java.util.concurrent.CountDownLatch(1);
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenAnswer(inv->{enteredRoute.countDown();releaseRoute.await(5,java.util.concurrent.TimeUnit.SECONDS);return java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now()));});
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try {var continued=pool.submit(()->mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"action\":{\"type\":\"CONTINUE\",\"token\":\""+first.path("searchToken").asText()+"\"}}")).andReturn());assertThat(enteredRoute.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();var independent=pool.submit(()->{var a=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(search)).andReturn();var b=mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"searchToken\":\""+other.path("searchToken").asText()+"\",\"listingId\":\"missing\"}")).andReturn();return a.getResponse().getStatus()==200&&b.getResponse().getStatus()==400;});assertThat(independent.get(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();releaseRoute.countDown();assertThat(continued.get(5,java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);}finally{releaseRoute.countDown();pool.shutdownNow();}
 }
 @Test void commuteRequiresModeAndResolvesAmbiguousWorkplace() throws Exception {
  var a=new com.rentpilot.commute.Workplace("a","上海交通大学","上海闵行","闵行区","310112",121.47,31.04);
  var b=new com.rentpilot.commute.Workplace("b","上海交通大学","上海徐汇","徐汇区","310104",121.43,31.2);
  result.set("{\"criteria\":{\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"上海交通大学\",\"maxCommuteMinutes\":15}");
  var input="{\"messages\":[{\"role\":\"user\",\"content\":\"上海交通大学上班，预算3000，15分钟\"}]}";
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.commute.state").value("NEEDS_MODE"));
  org.mockito.Mockito.verifyNoInteractions(commuteGateway);
  result.set("{\"criteria\":{\"town\":\"吴泾镇\",\"maxRent\":3000},\"unsupported\":false,\"commuteRequested\":false,\"maxCommuteMinutes\":15}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.commute.state").value("NEEDS_MODE"));
  result.set("{\"criteria\":{\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"上海交通大学\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(a,b));
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.workplaceCandidates.length()").value(2)).andExpect(jsonPath("$.commute.state").value("NEEDS_WORKPLACE"));
  org.mockito.Mockito.when(commuteGateway.detail("a")).thenReturn(java.util.Optional.of(a));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.eq(a),org.mockito.ArgumentMatchers.eq(com.rentpilot.commute.TravelMode.CYCLING))).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(900,1000,java.time.Instant.now())));
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input.substring(0,input.length()-1)+",\"workplaceId\":\"a\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.recommendations[0].commute.durationSeconds").value(900)).andExpect(jsonPath("$.recommendations[0].listing.rentalType").value("ENTIRE")).andExpect(jsonPath("$.recommendations[0].listing.bedrooms").value(1)).andExpect(jsonPath("$.recommendations[0].listing.rent").value(org.hamcrest.Matchers.lessThanOrEqualTo(3000.0))).andExpect(jsonPath("$.commute.checkedCount").value(org.hamcrest.Matchers.greaterThan(12))).andExpect(jsonPath("$.commute.candidateCount").value(414)).andExpect(jsonPath("$.commute.coverage").value("PARTIAL"));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.eq(a),org.mockito.ArgumentMatchers.eq(com.rentpilot.commute.TravelMode.CYCLING))).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(901,1000,java.time.Instant.now())));
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input.substring(0,input.length()-1)+",\"workplaceId\":\"a\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_MATCH")).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.commute.coverage").value("PARTIAL"));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.eq(a),org.mockito.ArgumentMatchers.eq(com.rentpilot.commute.TravelMode.CYCLING))).thenThrow(new com.rentpilot.commute.CommuteException("AMAP_INVALID_RESPONSE"));
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input.substring(0,input.length()-1)+",\"workplaceId\":\"a\"}")) .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AMAP_UNAVAILABLE"));

 }
 @Test void continuationAccumulatesAndIsIdempotent() throws Exception {
  var workplace=new com.rentpilot.commute.Workplace("a","工作地","上海闵行","闵行区","310112",121.47,31.04);
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(workplace));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now())));
  result.set("{\"criteria\":{\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"工作地\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  var first=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"工作地15分钟\"}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true)).andReturn();
  var tree=new ObjectMapper().readTree(first.getResponse().getContentAsString());
  var request="{\"action\":{\"type\":\"CONTINUE\",\"token\":\""+tree.path("searchToken").asText()+"\"}}";
  result.set("not valid json");
  var next=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(new ObjectMapper().readTree(next).path("total").asInt()).isGreaterThan(tree.path("total").asInt());
  var repeat=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(repeat).isEqualTo(next);
 }
 @Test void confirmedTimeAdjustmentUsesObservedRoutes() throws Exception {
  var workplace=new com.rentpilot.commute.Workplace("a","工作地","上海闵行","闵行区","310112",121.47,31.04);
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(workplace));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(901,1000,java.time.Instant.now())));
  result.set("{\"criteria\":{\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"工作地\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  var first=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"工作地15分钟\"}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.adjustments[?(@.previewKind == 'VERIFIED_ROUTE')]").exists()).andReturn();
  var tree=new ObjectMapper().readTree(first.getResponse().getContentAsString()); var option=tree.path("adjustments").findValues("previewKind");
  var choice=tree.path("adjustments").get(0); if(!choice.path("previewKind").asText().equals("VERIFIED_ROUTE")) choice=tree.path("adjustments").get(1);
  var request="{\"action\":{\"type\":\"APPLY_ADJUSTMENT\",\"token\":\""+tree.path("searchToken").asText()+"\",\"adjustmentId\":\""+choice.path("id").asText()+"\"}}";
  result.set("invalid"); mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.commute.maxMinutes").value(16)).andExpect(jsonPath("$.criteria.maxRent").value(3000)).andExpect(jsonPath("$.criteria.bedrooms").value(1));
 }
 @Test void unknownTokenExpires() throws Exception {mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"action\":{\"type\":\"CONTINUE\",\"token\":\"unknown\"}}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_SEARCH_EXPIRED"));}
 @Test void filterOnlyBudgetAdjustmentPreservesOtherCriteria() throws Exception {
  result.set("{\"criteria\":{\"district\":\"\u95f5\u884c\u533a\",\"town\":\"\u5434\u6cfe\u9547\",\"maxRent\":1,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"commuteRequested\":false}");
  var first=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"find a home\"}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_MATCH")).andExpect(jsonPath("$.adjustments[0].previewKind").value("FILTER_ONLY")).andReturn();
  var tree=new ObjectMapper().readTree(first.getResponse().getContentAsString());var action="{\"action\":{\"type\":\"APPLY_ADJUSTMENT\",\"token\":\""+tree.path("searchToken").asText()+"\",\"adjustmentId\":\"rent\"}}";result.set("invalid");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(action)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.criteria.district").value("\u95f5\u884c\u533a")).andExpect(jsonPath("$.criteria.town").value("\u5434\u6cfe\u9547")).andExpect(jsonPath("$.criteria.rentalType").value("ENTIRE")).andExpect(jsonPath("$.criteria.bedrooms").value(1));
 }
 @Test void malformedActionsAreRejected() throws Exception {
  for(var action:java.util.List.of("{\"type\":\"CONTINUE\"}","{\"type\":\"UNKNOWN\",\"token\":\"abc\"}","{\"type\":\"CONTINUE\",\"token\":\"abc\",\"adjustmentId\":\"rent\"}","{\"type\":\"APPLY_ADJUSTMENT\",\"token\":\"abc\"}")) mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"action\":"+action+"}")).andExpect(status().isBadRequest());
 }
 @Test void commuteBudgetConfirmationPreservesWorkplaceAndFilters() throws Exception {
  var place=new com.rentpilot.commute.Workplace("a","work","address","\u95f5\u884c\u533a","310112",121.47,31.04);
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(place));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now())));
  result.set("{\"criteria\":{\"district\":\"\u95f5\u884c\u533a\",\"town\":\"\u5434\u6cfe\u9547\",\"maxRent\":1,\"rentalType\":\"ENTIRE\",\"bedrooms\":1,\"minArea\":1},\"unsupported\":false,\"workplace\":\"work\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  var response=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"search\"}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.adjustments[0].previewKind").value("FILTER_ONLY")).andReturn();
  var token=new ObjectMapper().readTree(response.getResponse().getContentAsString()).path("searchToken").asText();
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"action\":{\"type\":\"APPLY_ADJUSTMENT\",\"token\":\""+token+"\",\"adjustmentId\":\"bogus\"}}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"action\":{\"type\":\"APPLY_ADJUSTMENT\",\"token\":\""+token+"\",\"adjustmentId\":\"rent\"}}")).andExpect(status().isOk()).andExpect(jsonPath("$.criteria.district").value("\u95f5\u884c\u533a")).andExpect(jsonPath("$.criteria.town").value("\u5434\u6cfe\u9547")).andExpect(jsonPath("$.criteria.rentalType").value("ENTIRE")).andExpect(jsonPath("$.criteria.bedrooms").value(1)).andExpect(jsonPath("$.criteria.minArea").value(1)).andExpect(jsonPath("$.commute.mode").value("CYCLING")).andExpect(jsonPath("$.commute.maxMinutes").value(15)).andExpect(jsonPath("$.commute.workplace.id").value("a"));
 }
 @Test void expiredTicketReturnsConflict() throws Exception {
  result.set("{\"criteria\":{\"town\":\"\u5434\u6cfe\u9547\",\"maxRent\":1},\"unsupported\":false,\"commuteRequested\":false}");
  var response=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"search\"}]}")).andReturn();var token=new ObjectMapper().readTree(response.getResponse().getContentAsString()).path("searchToken").asText();
  var service=context.getBean("aiSearchService");var tickets=(java.util.Map<?,?>)org.springframework.test.util.ReflectionTestUtils.getField(service,"tickets");org.springframework.test.util.ReflectionTestUtils.setField(tickets.get(token),"expires",0L);
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"action\":{\"type\":\"APPLY_ADJUSTMENT\",\"token\":\""+token+"\",\"adjustmentId\":\"rent\"}}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AI_SEARCH_EXPIRED"));
 }
 @Test void plainConfirmationDoesNotApplyBudgetSuggestion() throws Exception {
  result.set("{\"criteria\":{\"town\":\"\u5434\u6cfe\u9547\",\"maxRent\":1},\"unsupported\":false,\"commuteRequested\":false}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"search\"},{\"role\":\"assistant\",\"content\":\"budget suggestion\"},{\"role\":\"user\",\"content\":\"\u53ef\u4ee5\"}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_MATCH")).andExpect(jsonPath("$.criteria.maxRent").value(1));
 }
 @Test @org.springframework.transaction.annotation.Transactional void failedOriginRemainsAvailableForRetryWithoutRepeatingSuccess() throws Exception {
  jdbc.update("UPDATE listing SET longitude=121.4, latitude=31.1, location_status='COMMUNITY_REFERENCE' WHERE district=? AND town=?", "\u95f5\u884c\u533a", "\u5434\u6cfe\u9547");
  var ids=jdbc.queryForList("SELECT id FROM listing WHERE district=? AND town=? AND rental_type='ENTIRE' AND bedrooms=1 AND rent<=3000 ORDER BY id",String.class,"\u95f5\u884c\u533a","\u5434\u6cfe\u9547");
  jdbc.update("UPDATE listing SET longitude=121.5 WHERE id=?",ids.getFirst());
  var place=new com.rentpilot.commute.Workplace("a","work","address","\u95f5\u884c\u533a","310112",121.47,31.04);
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(place));
  var failureCalls=new java.util.concurrent.atomic.AtomicInteger();var successCalls=new java.util.concurrent.atomic.AtomicInteger();
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenAnswer(inv->{double lon=inv.getArgument(0);if(lon>121.45&&failureCalls.getAndIncrement()==0)throw new com.rentpilot.commute.CommuteException("AMAP_UNAVAILABLE");if(lon<121.45)successCalls.incrementAndGet();return java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now()));});
  result.set("{\"criteria\":{\"town\":\"\u5434\u6cfe\u9547\",\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"work\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  var first=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"search\"}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.commute.coverage").value("PARTIAL")).andExpect(jsonPath("$.hasMore").value(true)).andReturn();
  var firstTree=new ObjectMapper().readTree(first.getResponse().getContentAsString());var action="{\"action\":{\"type\":\"CONTINUE\",\"token\":\""+firstTree.path("searchToken").asText()+"\"}}";
  var next=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(action)).andExpect(status().isOk()).andExpect(jsonPath("$.commute.coverage").value("COMPLETE")).andExpect(jsonPath("$.hasMore").value(false)).andExpect(jsonPath("$.total").value(ids.size())).andReturn().getResponse().getContentAsString();
  var again=mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(action)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(again).isEqualTo(next);assertThat(failureCalls.get()).isEqualTo(2);assertThat(successCalls.get()).isEqualTo(1);
 }
 @Test @org.springframework.transaction.annotation.Transactional void missingCoordinatesRemainUnverified() throws Exception {
  jdbc.update("UPDATE listing SET longitude=NULL, latitude=NULL, location_status='UNVERIFIED' WHERE district=? AND town=?", "闵行区", "吴泾镇");
  var workplace=new com.rentpilot.commute.Workplace("a","工作地","上海闵行","闵行区","310112",121.47,31.04);
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(workplace));
  result.set("{\"criteria\":{\"town\":\"吴泾镇\",\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"工作地\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"吴泾镇骑行15分钟\"}]}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_MATCH")).andExpect(jsonPath("$.commute.checkedCount").value(0)).andExpect(jsonPath("$.commute.unverifiedCount").value(3)).andExpect(jsonPath("$.hasMore").value(false)).andExpect(jsonPath("$.commute.coverage").value("PARTIAL"));
  org.mockito.Mockito.verify(commuteGateway,org.mockito.Mockito.never()).route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
 }
 @Test void priorWorkplaceIsReusedOnlyWhenExtractedNameMatches() throws Exception {
  var previous=new com.rentpilot.commute.Workplace("old","吴泾宝龙广场","闵行区龙吴路","闵行区","310112",121.47,31.04);
  var replacement=new com.rentpilot.commute.Workplace("new","徐家汇","徐汇区","徐汇区","310104",121.43,31.2);
  org.mockito.Mockito.when(commuteGateway.detail("old")).thenReturn(java.util.Optional.of(previous));
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.eq("徐家汇"),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(replacement));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now())));
  var input="{\"messages\":[{\"role\":\"user\",\"content\":\"改为步行通勤，其他不变\"}],\"previousWorkplaceId\":\"old\"}";
  result.set("{\"criteria\":{\"town\":\"吴泾镇\",\"maxRent\":3000},\"unsupported\":false,\"workplace\":\"吴泾宝龙广场\",\"commuteMode\":\"WALKING\",\"maxCommuteMinutes\":15}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.commute.workplace.id").value("old"));
  assertThat(received.get()).contains("吴泾宝龙广场","闵行区龙吴路");
  org.mockito.Mockito.verify(commuteGateway,org.mockito.Mockito.never()).search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
  result.set("{\"criteria\":{\"town\":\"吴泾镇\",\"maxRent\":3000},\"unsupported\":false,\"workplace\":\"徐家汇\",\"commuteMode\":\"WALKING\",\"maxCommuteMinutes\":15}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk()).andExpect(jsonPath("$.commute.workplace.id").value("new"));
  org.mockito.Mockito.verify(commuteGateway).search(org.mockito.ArgumentMatchers.eq("徐家汇"),org.mockito.ArgumentMatchers.anyString());
 }
 @Test void sameNameInDifferentDistrictResolvesAgain() throws Exception {
  var old=new com.rentpilot.commute.Workplace("old","广场","闵行区","闵行区","310112",121.47,31.04);
  var replacement=new com.rentpilot.commute.Workplace("new","广场","浦东新区","浦东新区","310115",121.55,31.2);
  org.mockito.Mockito.when(commuteGateway.detail("old")).thenReturn(java.util.Optional.of(old));
  org.mockito.Mockito.when(commuteGateway.search("广场","浦东")).thenReturn(java.util.List.of(replacement));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now())));
  result.set("{\"criteria\":{\"maxRent\":3000},\"unsupported\":false,\"workplace\":\"广场\",\"workplaceDistrict\":\"浦东\",\"commuteMode\":\"WALKING\"}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"换到浦东的广场\"}],\"previousWorkplaceId\":\"old\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.commute.workplace.id").value("new"));
 }
 @Test void sameNameInDifferentTownResolvesWithTownContext() throws Exception {
  var old=new com.rentpilot.commute.Workplace("old","广场","闵行区吴泾镇","闵行区","310112",121.47,31.04);
  var replacement=new com.rentpilot.commute.Workplace("new","广场","闵行区莘庄镇","闵行区","310112",121.4,31.1);
  org.mockito.Mockito.when(commuteGateway.detail("old")).thenReturn(java.util.Optional.of(old));
  org.mockito.Mockito.when(commuteGateway.search("莘庄 广场","闵行")).thenReturn(java.util.List.of(replacement));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(600,1000,java.time.Instant.now())));
  result.set("{\"criteria\":{\"maxRent\":3000},\"unsupported\":false,\"workplace\":\"广场\",\"workplaceDistrict\":\"闵行\",\"workplaceTown\":\"莘庄\",\"commuteMode\":\"WALKING\"}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"换到莘庄的广场\"}],\"previousWorkplaceId\":\"old\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.commute.workplace.id").value("new"));
 }
 @Test void sharedCommunityCoordinatesUseOneRouteAndCountEveryListing() throws Exception {
  var workplace=new com.rentpilot.commute.Workplace("a","工作地","上海闵行","闵行区","310112",121.47,31.04);
  org.mockito.Mockito.when(commuteGateway.search(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(workplace));
  org.mockito.Mockito.when(commuteGateway.route(org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.anyDouble(),org.mockito.ArgumentMatchers.eq(workplace),org.mockito.ArgumentMatchers.eq(com.rentpilot.commute.TravelMode.CYCLING))).thenReturn(java.util.Optional.of(new com.rentpilot.commute.RouteEstimate(900,1000,java.time.Instant.now())));
  result.set("{\"criteria\":{\"town\":\"吴泾镇\",\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"工作地\",\"commuteMode\":\"CYCLING\",\"maxCommuteMinutes\":15}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"吴泾镇骑行15分钟\"}]}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.commute.checkedCount").value(3)).andExpect(jsonPath("$.commute.coverage").value("COMPLETE"));
  org.mockito.Mockito.verify(commuteGateway,org.mockito.Mockito.times(1)).route(org.mockito.ArgumentMatchers.eq(121.47),org.mockito.ArgumentMatchers.eq(31.04),org.mockito.ArgumentMatchers.eq(workplace),org.mockito.ArgumentMatchers.eq(com.rentpilot.commute.TravelMode.CYCLING));
 }
 @Test void providerAndGroundedResults() throws Exception {result.set("{\"criteria\":{\"district\":\"浦东新区\",\"rentalType\":\"ENTIRE\",\"maxRent\":3000},\"unsupported\":false}"); mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"assistant\",\"content\":\"spoof\"},{\"role\":\"user\",\"content\":\"浦东3000以内\"}]}")) .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.recommendations[0].listing.district").value("浦东新区")).andExpect(jsonPath("$.recommendations[0].listing.rentalType").value("ENTIRE")).andExpect(jsonPath("$.recommendations[0].listing.rent").value(org.hamcrest.Matchers.lessThanOrEqualTo(3000.0))); assertThat(auth.get()).isEqualTo("Bearer fixture-secret");assertThat(received.get()).doesNotContain("spoof").contains("system","浦东"); }
 @Test void noMatchAndUnsupported() throws Exception {result.set("{\"criteria\":{\"district\":\"浦东新区\",\"maxRent\":1},\"unsupported\":false}"); mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"浦东1元\"}]}")) .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_MATCH")); result.set("{\"criteria\":{\"district\":\"浦东新区\",\"maxRent\":3000},\"unsupported\":true}");mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"浦东3000，上班半小时\"}]}")) .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_INPUT")); }
 @Test void handlesWorkplaceAndFlexibleJson() throws Exception {
  result.set("```json\n{\"criteria\":{\"district\":null,\"maxRent\":\"3000\",\"rentalType\":\"ENTIRE\"},\"unsupported\":true,\"workplace\":\"吴泾宝龙广场\",\"workplaceTown\":\"吴泾\",\"maxCommuteMinutes\":15}\n```");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"我在闵行吴泾宝龙广场上班，月租预算3000元，想整租一居，通勤15分钟以内。\"}]}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_INPUT")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("其他硬性要求"))).andExpect(jsonPath("$.criteria.maxRent").value(3000));
 }
 @Test void changingWorkplaceAndExplicitlyRelaxingCommute() throws Exception {
  var messages="{\"messages\":[{\"role\":\"user\",\"content\":\"我在闵行上海交大附近上班，月租预算3000元，想整租一居，通勤15分钟以内。\"},{\"role\":\"assistant\",\"content\":\"请确认\"},{\"role\":\"user\",\"content\":\"我在闵行吴泾宝龙广场上班，月租预算3000元，想整租一居，通勤15分钟以内。\"}]}";
  result.set("{\"criteria\":{\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":true,\"workplace\":\"吴泾宝龙广场\",\"workplaceTown\":\"吴泾\",\"maxCommuteMinutes\":15}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(messages)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_INPUT")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("其他硬性要求"))).andExpect(jsonPath("$.criteria.town").doesNotExist());
  assertThat(received.get()).contains("上海交大","吴泾宝龙广场");
  result.set("{\"criteria\":{\"district\":\"闵行\",\"town\":\"吴泾\",\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false,\"workplace\":\"吴泾宝龙广场\",\"commuteRequested\":false,\"commuteMode\":\"CYCLING\"}");
  messages=messages.substring(0,messages.length()-2)+",{\"role\":\"user\",\"content\":\"先按吴泾镇找，通勤暂时不限\"}]}";
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(messages)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.criteria.district").value("闵行区")).andExpect(jsonPath("$.criteria.town").value("吴泾镇"));
 }
 @Test void emptyProviderResponseHasDistinctError() throws Exception {
  try {finish.set("length"); result.set(""); mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"找房\"}]}")) .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_TRUNCATED_RESPONSE")); finish.set("stop"); mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"找房\"}]}")) .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_EMPTY_RESPONSE"));} finally {finish.set("stop");}
 }
 @Test void shortConfirmationAndDeclineUseOnlyRecognizedPendingQuestion() throws Exception {
  var mapper=new ObjectMapper();
  var original=java.util.Map.of("role","user","content","我在闵行吴泾宝龙广场上班，月租预算3000元，想整租一居，通勤15分钟以内。");
  var question=java.util.Map.of("role","assistant","content","已了解。是否先按吴泾镇搜索，并暂时不限通勤？");
  result.set("{\"criteria\":{\"district\":\"闵行\",\"town\":\"吴泾\",\"maxRent\":3000,\"rentalType\":\"ENTIRE\",\"bedrooms\":1},\"unsupported\":false}");
  for(String reply:java.util.List.of("是的","可以","好的")) {
   var messages=java.util.List.of(original,question,java.util.Map.of("role","user","content",reply));
   mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("messages",messages))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.criteria.maxRent").value(3000)).andExpect(jsonPath("$.criteria.rentalType").value("ENTIRE")).andExpect(jsonPath("$.criteria.bedrooms").value(1));
   assertThat(received.get()).contains("同意先按吴泾镇搜索，暂时不限通勤，其余条件保留");
  }
  var oldHistory=java.util.List.of(original,question,java.util.Map.of("role","user","content","是的"),question,java.util.Map.of("role","user","content","可以"));
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("messages",oldHistory)))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES"));
  for(String reply:java.util.List.of("不行","不放宽通勤","还是要15分钟","可以但还是15分钟")) {
   var messages=java.util.List.of(original,question,java.util.Map.of("role","user","content",reply));
   mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("messages",messages)))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_INPUT")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("保留通勤条件")));
   assertThat(received.get()).doesNotContain("同意先按吴泾镇搜索");
  }
  result.set("{\"criteria\":{},\"unsupported\":false}");
  for(String prompt:java.util.List.of("你好","是否先按不存在镇搜索，并暂时不限通勤？")) {
   var messages=java.util.List.of(java.util.Map.of("role","assistant","content",prompt),java.util.Map.of("role","user","content","是的"));
   mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("messages",messages)))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_INPUT"));
   assertThat(received.get()).doesNotContain("同意先按");
  }
 }
 @Test void malformedResponseIsSanitized() throws Exception {result.set("secret invalid");mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"找房\"}]}")) .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));}
 @Test void laterChangesAndUnlimitedBudget() throws Exception {
  result.set("{\"criteria\":{\"district\":\"浦东新区\"},\"unsupported\":false,\"budgetUnrestricted\":true}");
  mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"浦东预算3000\"},{\"role\":\"assistant\",\"content\":\"好的\"},{\"role\":\"user\",\"content\":\"预算不限\"}]}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES")).andExpect(jsonPath("$.criteria.maxRent").doesNotExist());
  assertThat(received.get()).contains("3000","预算不限").doesNotContain("好的");
 } @Test void providerErrorsAreSanitized() throws Exception {
  try {for(int code:new int[]{401,500}) {httpStatus.set(code);result.set("fixture-secret");mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"找房\"}]}")) .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-secret"))));}} finally {httpStatus.set(200);}
 }
 @Test void rejectsUnknownAndWrongTypes() throws Exception {
  for(String answer:new String[]{"{\"criteria\":{\"unexpected\":1},\"unsupported\":false}","{\"criteria\":{\"maxRent\":true},\"unsupported\":false}"}) {result.set(answer);mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"找房\"}]}")) .andExpect(status().isBadGateway());}
  result.set("{\"criteria\":{\"district\":\"不存在区\",\"maxRent\":3000},\"unsupported\":false}");mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[{\"role\":\"user\",\"content\":\"找房\"}]}")) .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_INPUT"));
 }}






