package com.rentpilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rentpilot.ai.AiSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:lifesim-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class LifesimApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AiSearchService search;

    private String register() throws Exception {
        var response=mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"username\":\"l"+UUID.randomUUID().toString().replace("-","").substring(0,12)+"\",\"password\":\"Test-pass-123\",\"nickname\":\"租客\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(response).get("token").asText();
    }

    private ObjectNode searchResult(String listingId,int rent,Integer minutes){
        var root=json.createObjectNode();
        root.put("status","MATCHES");root.put("dataKind","SIMULATED");root.put("engine","SPRING_AI");
        root.putObject("criteria").put("maxRent",6000);
        root.putObject("source").put("id","sh-1500").put("rowCount",1500)
                .put("sha256","07FF43A3600E34CFFBDC0DE8751406CAC33F6CFC330C169472AE739ED8ABDFA8");
        if(minutes!=null){
            var commute=root.putObject("commute");
            commute.put("state","CHECKED").put("mode","CYCLING").put("maxMinutes",30).put("checkedCount",12)
                    .put("candidateCount",30).put("unverifiedCount",18).put("coverage","PARTIAL").put("estimated",true);
        }
        var recommendation=root.putArray("recommendations").addObject();
        var listing=recommendation.putObject("listing");
        listing.put("id",listingId).put("name","模拟小区 001").put("district","闵行区").put("town","吴泾镇").put("community","吴泾社区")
                .put("rent",rent).put("deposit",rent).put("paymentMethod","押一付三")
                .put("propertyFee",200).put("propertyFeePeriod","MONTHLY");
        recommendation.putArray("reasons").add("月租 "+rent+" 元");
        if(minutes!=null)recommendation.putObject("commute").put("mode","CYCLING").put("minutes",minutes)
                .put("durationSeconds",minutes*60).put("distanceMeters",3000).put("source","AMAP")
                .put("estimated",true).put("checkedAt","2026-10-31T08:00:00Z");
        recommendation.putObject("insight").putObject("costs").put("monthlyRent",rent).put("deposit",rent)
                .put("paymentMethod","押一付三").put("propertyFee",200).put("propertyFeePeriod","MONTHLY")
                .put("prepaidMonths",3).put("firstRentPayment",rent*3).put("knownInitialPayment",rent*4);
        root.putArray("limitations").add("房源为模拟数据。");
        return root;
    }

    private String createPlan(String token,int rent,Integer minutes) throws Exception {
        when(search.snapshot(anyString())).thenReturn(searchResult("SH-MOCK-000617",rent,minutes));
        String body=mvc.perform(post("/api/me/plans").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"searchToken\":\"token-a\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("plan").path("id").asText();
    }

    @Test void loginAndOwnershipAreRequired() throws Exception {
        mvc.perform(get("/api/me/plans/x/lifesim")).andExpect(status().isUnauthorized());
        String owner=register(),other=register();
        String planId=createPlan(owner,2400,11);
        mvc.perform(get("/api/me/plans/"+planId+"/lifesim").header("Authorization",other)).andExpect(status().isNotFound());
    }

    @Test void emptyAnswersStillExposeVerifiedFacts() throws Exception {
        String token=register();
        String planId=createPlan(token,2400,11);
        mvc.perform(get("/api/me/plans/"+planId+"/lifesim").header("Authorization",token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.commuteMinutes").value(11))
                .andExpect(jsonPath("$.sources.commute").value("FACT"))
                .andExpect(jsonPath("$.sources.workStart").value("ASSUMED"))
                .andExpect(jsonPath("$.sources.dailySpend").value("UNKNOWN"))
                .andExpect(jsonPath("$.timeline.length()").value(6))
                .andExpect(jsonPath("$.timeline[0].time").value("08:49"))
                .andExpect(jsonPath("$.timeline[0].source").value("ASSUMED"));
    }

    @Test void answersProduceTimelineAndCostsOnTheServer() throws Exception {
        String token=register();
        String planId=createPlan(token,2400,11);
        String saved=mvc.perform(put("/api/me/plans/"+planId+"/lifesim").header("Authorization",token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workStart\":\"09:00\",\"sleepTime\":\"23:30\",\"overtimeDays\":2,\"overtimeEnd\":\"21:00\",\"dailySpendBand\":\"1000_2000\",\"fixedCost\":500,\"cookingDays\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.timeline[0].key").value("leave"))
                .andExpect(jsonPath("$.timeline[0].time").value("08:49"))
                .andExpect(jsonPath("$.timeline[3].time").value("21:11"))
                .andExpect(jsonPath("$.costs.monthlyKnown").value(3100))
                .andExpect(jsonPath("$.costs.monthlyLow").value(4100))
                .andExpect(jsonPath("$.costs.monthlyHigh").value(5100))
                .andExpect(jsonPath("$.sources.workStart").value("USER"))
                .andExpect(jsonPath("$.sources.fixedCost").value("USER"))
                .andExpect(jsonPath("$.sources.propertyFee").value("FACT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(saved).path("updatedAt").asText()).isNotBlank();
        mvc.perform(get("/api/me/plans/"+planId+"/lifesim").header("Authorization",token))
                .andExpect(jsonPath("$.answers.workStart").value("09:00"))
                .andExpect(jsonPath("$.costs.dailySpendLabel").value("1000–2000 元"));
    }

    @Test void skippedSpendingIsUnknownAndNotEstimated() throws Exception {
        String token=register();
        String planId=createPlan(token,2400,11);
        mvc.perform(put("/api/me/plans/"+planId+"/lifesim").header("Authorization",token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workStart\":\"09:00\",\"sleepTime\":\"23:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sources.dailySpend").value("UNKNOWN"))
                .andExpect(jsonPath("$.costs.dailySpendLabel").value("未填写"))
                .andExpect(jsonPath("$.costs.monthlyKnown").value(2600))
                .andExpect(jsonPath("$.costs.monthlyLow").value(2600));
    }

    @Test void unverifiedCommuteBlocksTheTimeline() throws Exception {
        String token=register();
        String planId=createPlan(token,2400,null);
        mvc.perform(get("/api/me/plans/"+planId+"/lifesim").header("Authorization",token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_COMMUTE"))
                .andExpect(jsonPath("$.sources.commute").value("UNKNOWN"))
                .andExpect(jsonPath("$.timeline").isEmpty());
    }
}