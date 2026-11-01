package com.rentpilot.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentpilot.listing.Listing;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class RecommendationFactsTest {
 private final ObjectMapper json=new ObjectMapper();
 private Listing listing(String payment,String deposit) throws Exception {return json.readValue("""
 {"id":"1","district":"浦东","rent":3000,"area":30,"deposit":%s,"paymentMethod":"%s","propertyFee":100,"propertyFeePeriod":"UNSPECIFIED","metroWalkMinutes":5,"amenities":{"elevator":"UNKNOWN"}}
 """.formatted(deposit,payment),Listing.class);}
 @Test void costsRespectExplicitPaymentAndDeposit() throws Exception {
  for(var entry:Map.of("押一付一",1,"押一付三",3,"押二付三",3).entrySet()) {
   var c=RecommendationFacts.build(listing(entry.getKey(),"0"),Map.of(),null).costs();
   assertEquals(entry.getValue(),c.prepaidMonths()); assertEquals(0,c.knownInitialPayment().compareTo(new java.math.BigDecimal(3000*entry.getValue())));
   assertEquals("UNSPECIFIED",c.propertyFeePeriod());
  }
  var c=RecommendationFacts.build(listing("未知","3000"),Map.of(),null).costs();
  assertNull(c.prepaidMonths()); assertNull(c.knownInitialPayment()); assertNull(c.firstRentPayment());
 }
 @Test void evidenceUsesCriteriaUnknownAndActualRoute() throws Exception {
  var result=RecommendationFacts.build(listing("押一付一","3000"),Map.of("maxRent",4000,"elevator","YES"),Map.of("durationSeconds",901));
  assertTrue(result.evidence().stream().anyMatch(e->e.text().contains("1000")));
  assertTrue(result.evidence().stream().anyMatch(e->e.status().equals("UNVERIFIED")&&e.fields().contains("amenities.elevator")));
  assertTrue(result.evidence().stream().anyMatch(e->e.source().equals("AMAP")&&e.text().contains("16")));
  assertFalse(RecommendationFacts.build(listing("押一付一","3000"),Map.of(),null).evidence().stream().anyMatch(e->e.source().equals("AMAP")));
 }
 @Test void areaEvidenceDescribesActualAndThreshold() throws Exception {var facts=RecommendationFacts.build(listing("押一付一","0"),Map.of("minArea",20),null);assertTrue(facts.evidence().stream().anyMatch(e->e.id().equals("minArea")&&e.text().contains("30 平方米")&&e.text().contains("20 平方米")));}
}
