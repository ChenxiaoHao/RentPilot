package com.rentpilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rentpilot.ai.AiSearchService;
import com.rentpilot.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:plan-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class PlanApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AiSearchService search;

    private String register() throws Exception {
        var response=mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"p"+UUID.randomUUID().toString().replace("-","").substring(0,12)+"\",\"password\":\"Test-pass-123\",\"nickname\":\"租客\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(response).get("token").asText();
    }

    private ObjectNode searchResult(String listingId,int rent,int minutes,String coverage){
        var root=json.createObjectNode();
        root.put("status","MATCHES");root.put("dataKind","SIMULATED");root.put("engine","SPRING_AI");
        root.putObject("criteria").put("maxRent",6000).put("bedrooms",1);
        root.putObject("source").put("id","sh-1500").put("rowCount",1500).put("sha256","07FF43A3600E34CFFBDC0DE8751406CAC33F6CFC330C169472AE739ED8ABDFA8");
        var commute=root.putObject("commute");
        commute.put("state","CHECKED").put("mode","CYCLING").put("maxMinutes",30).put("checkedCount",12)
                .put("candidateCount",30).put("unverifiedCount",18).put("coverage",coverage).put("estimated",true);
        var recommendations=(ArrayNode)root.putArray("recommendations");
        var recommendation=recommendations.addObject();
        var listing=recommendation.putObject("listing");
        listing.put("id",listingId).put("name","模拟小区 "+listingId.substring(listingId.length()-3))
                .put("district","闵行区").put("town","吴泾镇").put("community","吴泾社区")
                .put("rent",rent).put("deposit",rent).put("paymentMethod","押一付三")
                .put("propertyFee",200).put("propertyFeePeriod","UNSPECIFIED");
        recommendation.putArray("reasons").add("月租 "+rent+" 元").add("预计骑行 "+minutes+" 分钟");
        recommendation.putObject("commute").put("mode","CYCLING").put("minutes",minutes).put("durationSeconds",minutes*60)
                .put("distanceMeters",3000).put("source","AMAP").put("estimated",true).put("checkedAt","2026-10-31T08:00:00Z");
        recommendation.putObject("insight").putObject("costs").put("monthlyRent",rent).put("deposit",rent)
                .put("paymentMethod","押一付三").put("prepaidMonths",3).put("firstRentPayment",rent*3).put("knownInitialPayment",rent*4);
        root.putArray("limitations").add("房源为模拟数据，地铁步行时间不代表工作地通勤时间。");
        return root;
    }

    private String save(String token,String body) throws Exception {
        return mvc.perform(post("/api/me/plans").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test void loginRequiredAndSnapshotMustStillExist() throws Exception {
        mvc.perform(get("/api/me/plans")).andExpect(status().isUnauthorized());
        when(search.snapshot(anyString())).thenThrow(new ApiException(HttpStatus.CONFLICT,"PLAN_SOURCE_EXPIRED","搜索结果已过期，请重新检索后再保存",Map.of()));
        String a=register();
        mvc.perform(post("/api/me/plans").header("Authorization",a).contentType(MediaType.APPLICATION_JSON).content("{\"searchToken\":\"expired-token\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_SOURCE_EXPIRED"));
    }

    @Test void savesVerifiedHomesAndKeepsAccountsSeparate() throws Exception {
        when(search.snapshot("token-a")).thenReturn(searchResult("SH-MOCK-000617",2400,11,"PARTIAL"));
        String a=register(),b=register();
        String saved=save(a,"{\"searchToken\":\"token-a\",\"title\":\"吴泾通勤方案\",\"listingIds\":[\"SH-MOCK-000617\"]}");
        var root=json.readTree(saved);
        String planId=root.path("plan").path("id").asText();
        org.assertj.core.api.Assertions.assertThat(planId).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(root.path("plan").path("versionNo").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(root.path("plan").path("primaryRent").decimalValue()).isEqualByComparingTo("2400");
        org.assertj.core.api.Assertions.assertThat(root.path("plan").path("coverage").asText()).isEqualTo("PARTIAL");
        org.assertj.core.api.Assertions.assertThat(root.path("snapshot").path("realtime").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(root.path("snapshot").path("snapshotKind").asText()).isEqualTo("HISTORICAL");

        mvc.perform(get("/api/me/plans").header("Authorization",a)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("吴泾通勤方案"))
                .andExpect(jsonPath("$[0].homeCount").value(1))
                .andExpect(jsonPath("$[0].primaryCommuteMinutes").value(11));
        mvc.perform(get("/api/me/plans").header("Authorization",b)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/me/plans/"+planId).header("Authorization",b)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/plans/"+planId).header("Authorization",b)).andExpect(status().isNotFound());
    }

    @Test void rejectsHomesThatWereNotPresented() throws Exception {
        when(search.snapshot("token-a")).thenReturn(searchResult("SH-MOCK-000617",2400,11,"COMPLETE"));
        String a=register();
        mvc.perform(post("/api/me/plans").header("Authorization",a).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"searchToken\":\"token-a\",\"listingIds\":[\"SH-MOCK-999999\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLAN_HOME_NOT_FOUND"));
    }

    @Test void appendsVersionsAndCanRestoreAnOlderOne() throws Exception {
        when(search.snapshot("token-a")).thenReturn(searchResult("SH-MOCK-000617",2400,11,"PARTIAL"));
        when(search.snapshot("token-b")).thenReturn(searchResult("SH-MOCK-000618",2600,9,"COMPLETE"));
        String a=register();
        String first=save(a,"{\"searchToken\":\"token-a\",\"title\":\"第一版\"}");
        String planId=json.readTree(first).path("plan").path("id").asText();
        String firstVersionId=json.readTree(first).path("plan").path("versionId").asText();
        String second=save(a,"{\"searchToken\":\"token-b\",\"title\":\"第二版\",\"planId\":\""+planId+"\"}");
        org.assertj.core.api.Assertions.assertThat(json.readTree(second).path("plan").path("versionNo").asInt()).isEqualTo(2);

        mvc.perform(get("/api/me/plans/"+planId+"/versions").header("Authorization",a)).andExpect(status().isOk())
                .andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].versionNo").value(2));
        mvc.perform(put("/api/me/plans/"+planId+"/active").header("Authorization",a).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionId\":\""+firstVersionId+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.versionNo").value(1))
                .andExpect(jsonPath("$.plan.primaryRent").value(2400))
                .andExpect(jsonPath("$.snapshot.realtime").value(false));
        mvc.perform(delete("/api/me/plans/"+planId).header("Authorization",a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/plans").header("Authorization",a)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(delete("/api/me/plans/"+planId).header("Authorization",a)).andExpect(status().isNotFound());
    }

    @Test void comparesFactsWithoutInventingMissingValues() throws Exception {
        when(search.snapshot("token-a")).thenReturn(searchResult("SH-MOCK-000617",2400,11,"PARTIAL"));
        when(search.snapshot("token-b")).thenReturn(searchResult("SH-MOCK-000618",2600,9,"COMPLETE"));
        String a=register();
        String left=json.readTree(save(a,"{\"searchToken\":\"token-a\"}")).path("plan").path("id").asText();
        String right=json.readTree(save(a,"{\"searchToken\":\"token-b\"}")).path("plan").path("id").asText();
        mvc.perform(get("/api/me/plans/compare").param("left",left).param("right",right).header("Authorization",a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentDifference").value(-200))
                .andExpect(jsonPath("$.commuteMinutesDifference").value(2))
                .andExpect(jsonPath("$.left.partialCoverage").value(true))
                .andExpect(jsonPath("$.notes[0]").isString());
        mvc.perform(get("/api/me/plans/compare").param("left",left).param("right",left).header("Authorization",a)).andExpect(status().isBadRequest());
        String b=register();
        mvc.perform(get("/api/me/plans/compare").param("left",left).param("right",right).header("Authorization",b)).andExpect(status().isNotFound());
    }
}