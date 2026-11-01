package com.rentpilot.ai;

import com.fasterxml.jackson.databind.*;
import com.rentpilot.api.ApiException;
import com.rentpilot.dataset.DatasetService;
import com.rentpilot.listing.*;
import com.rentpilot.commute.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.ai.openai.*;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import java.util.*;

@org.springframework.stereotype.Service
public class AiSearchService {
 private final ListingRepository listings; private final DatasetService datasets; private final ObjectMapper json;
 private final AiExtractionClient extraction; private final CommuteGateway gateway; private final int candidateCap;
 private final Semaphore routePhase = new Semaphore(1);
 private final ThreadPoolExecutor routeExecutor = new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16), r->{var t=new Thread(r,"commute-route");t.setDaemon(true);return t;});
 public AiSearchService(ListingRepository listings, DatasetService datasets, ObjectMapper json,AiExtractionClient extraction, CommuteGateway gateway, @Value("${rentpilot.commute.max-candidates:12}") int candidateCap){this.listings=listings;this.datasets=datasets;this.json=json;this.extraction=extraction;this.gateway=gateway;this.candidateCap=Math.max(1,Math.min(candidateCap,16));}
 record Message(String role,String content) {} record Action(String type,String token,String adjustmentId,Number to) {} record Request(List<Message> messages,String workplaceId,String previousWorkplaceId,Action action) { Request(List<Message> m,String w,String p){this(m,w,p,null);} }
 record Recommendation(Listing listing,List<String> reasons, Object commute, RecommendationFacts.Insight insight) { Recommendation(Listing l,List<String> r){this(l,r,null,null);} Recommendation(Listing l,List<String> r,Object c){this(l,r,c,null);} }
 record SortRule(String key,String label,boolean partial) {}
 record Response(String status,String message,Map<String,Object> criteria,List<Recommendation> recommendations,long total,List<String> limitations,String dataKind,Object source,String engine,Object commute,List<Workplace> workplaceCandidates,String searchToken,Boolean hasMore,List<Adjustment> adjustments,SortRule sortRule) { Response(String s,String m,Map<String,Object> c,List<Recommendation> r,long t,List<String> l,String d,Object src,String e,Object co,List<Workplace> wc,String token,Boolean more,List<Adjustment> adj){this(s,m,c,r,t,l,d,src,e,co,wc,token,more,adj,null);} Response(String s,String m,Map<String,Object> c,List<Recommendation> r,long t,List<String> l,String d,Object src,String e,Object co,List<Workplace> wc){this(s,m,c,r,t,l,d,src,e,co,wc,null,null,null);} Response(String s,String m,Map<String,Object> c,List<Recommendation> r,long t,List<String> l,String d,Object src,String e){this(s,m,c,r,t,l,d,src,e,null,null);} }

 record Change(String field,Number from,Number to) {} record Adjustment(String id,String label,String description,List<Change> changes,int previewCount,String previewKind) {}
 private static class Ticket { long expires=System.currentTimeMillis()+600_000; List<List<Listing>> groups; List<Listing> all; Workplace place; TravelMode mode; Integer max; Map<String,Object> criteria; List<String> limitations; LinkedMultiValueMap<String,String> values; int offset, checked; List<Recommendation> matches=new ArrayList<>(); List<Adjustment> adjustments=List.of(); Map<String,Long> observed=new HashMap<>(); Map<String,Response> replies=new HashMap<>(); List<Recommendation> presented=List.of(); Map<String,ExplainResponse> explanations=new HashMap<>(); }
 private final LinkedHashMap<String,Ticket> tickets=new LinkedHashMap<>(); private final java.security.SecureRandom random=new java.security.SecureRandom();
 private synchronized String save(Ticket t){tickets.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis()); while(tickets.size()>=128) tickets.remove(tickets.keySet().iterator().next()); byte[] b=new byte[32]; random.nextBytes(b); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(b); tickets.put(token,t); return token;}
 private Response action(Action a){if(a.type()==null || !Set.of("CONTINUE","APPLY_ADJUSTMENT").contains(a.type()) || a.token()==null || !a.token().matches("[A-Za-z0-9_-]{1,100}") || ("CONTINUE".equals(a.type()) && a.adjustmentId()!=null) || ("APPLY_ADJUSTMENT".equals(a.type()) && (a.adjustmentId()==null || !a.adjustmentId().matches("[A-Za-z0-9_-]{1,100}")))) throw ApiException.invalid("action","\u641c\u7d22\u64cd\u4f5c\u65e0\u6548"); Ticket t; synchronized(this){t=tickets.get(a.token()); if(t==null||t.expires<System.currentTimeMillis()) {tickets.remove(a.token()); throw error(HttpStatus.CONFLICT,"AI_SEARCH_EXPIRED","搜索已过期，请重新搜索");}} synchronized(t){String key=a.type()+":"+a.adjustmentId()+":"+(a.to()==null?"":a.to()); if(t.replies.containsKey(key)) return t.replies.get(key); Response r; if("CONTINUE".equals(a.type()) && t.offset<t.groups.size()) r=runBatch(copy(t)); else if("APPLY_ADJUSTMENT".equals(a.type())) {var choice=t.adjustments.stream().filter(x->x.id().equals(a.adjustmentId())).findFirst().orElseThrow(()->ApiException.invalid("adjustmentId","\u8c03\u6574\u9009\u9879\u65e0\u6548"));var n=copy(t);var c=choice.changes().getFirst();var target=a.to()==null?c.to():a.to();if(c.field().equals("maxCommuteMinutes")){int minutes=target.intValue();if(minutes<1||minutes>180||minutes<=c.from().intValue())throw ApiException.invalid("to","通勤上限需在 1–180 分钟之间且高于当前值");n.max=minutes;}else {var rent=new java.math.BigDecimal(target.toString());if(rent.compareTo(new java.math.BigDecimal(c.from().toString()))<=0||rent.compareTo(new java.math.BigDecimal("1000000"))>0)throw ApiException.invalid("to","预算上限需高于当前值且不超过 1000000 元");var criteria=new LinkedHashMap<>(n.criteria);criteria.put("maxRent",rent);n.criteria=Map.copyOf(criteria);n.values=new LinkedMultiValueMap<>(t.values);n.values.set("maxRent",rent.toPlainString());n.all=loadAll(n.values);n.groups=n.place==null?List.of():group(n.all,n.place);}n.offset=0;n.checked=0;n.matches=new ArrayList<>();n.observed=new HashMap<>();r=n.place==null?filterResponse(n):runBatch(n);} else throw ApiException.invalid("action","无效的搜索操作"); t.replies.put(key,r); return r;}}
 record ExplainResponse(String listingId,String summary,List<String> evidenceIds,String engine,String dataKind) {}
 ExplainResponse explain(String token,String listingId){
  if(token==null||!token.matches("[A-Za-z0-9_-]{1,100}")||listingId==null||listingId.isBlank()||listingId.length()>100)throw ApiException.invalid("listingId","解读请求无效");
  Ticket t; Recommendation recommendation;
  synchronized(this){t=tickets.get(token);if(t==null||t.expires<System.currentTimeMillis()){tickets.remove(token);throw error(HttpStatus.CONFLICT,"AI_SEARCH_EXPIRED","搜索已过期，请重新搜索");}recommendation=t.presented.stream().filter(r->r.listing().id().equals(listingId)).findFirst().orElseThrow(()->ApiException.invalid("listingId","房源未在当前结果中展示"));}
  synchronized(t){var cached=t.explanations.get(listingId);if(cached!=null)return cached;
   try{var evidence=recommendation.insight().evidence();String output=extraction.selectEvidence(evidence);var root=json.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(output);if(root==null||!root.isObject()||root.size()!=1||!root.has("evidenceIds")||!root.get("evidenceIds").isArray()||root.get("evidenceIds").size()<1||root.get("evidenceIds").size()>3)throw new IllegalArgumentException();var selected=new ArrayList<RecommendationFacts.Evidence>();var ids=new LinkedHashSet<String>();for(var node:root.get("evidenceIds")){if(!node.isTextual()||!ids.add(node.asText()))throw new IllegalArgumentException();selected.add(evidence.stream().filter(e->e.id().equals(node.asText())).findFirst().orElseThrow());}if(selected.stream().noneMatch(e->e.status().equals("MATCHED")))throw new IllegalArgumentException();var result=new ExplainResponse(listingId,String.join("；",selected.stream().map(RecommendationFacts.Evidence::text).toList()),List.copyOf(ids),"SPRING_AI","SIMULATED");t.explanations.put(listingId,result);return result;}catch(Exception e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"AI_EXPLANATION_UNAVAILABLE","AI 解读暂不可用，请重试");}
  }
  }
  public com.fasterxml.jackson.databind.JsonNode snapshot(String token){
   if(token==null||!token.matches("[A-Za-z0-9_-]{1,100}"))throw ApiException.invalid("searchToken","搜索票据无效");
   Ticket t;
   synchronized(this){t=tickets.get(token);if(t==null||t.expires<System.currentTimeMillis()){tickets.remove(token);throw error(HttpStatus.CONFLICT,"PLAN_SOURCE_EXPIRED","搜索结果已过期，请重新检索后再保存");}}
   synchronized(t){
    if(t.presented.isEmpty())throw error(HttpStatus.CONFLICT,"PLAN_SOURCE_NOT_SAVEABLE","当前搜索结果没有可保存的房源");
    var root=json.createObjectNode();
    root.put("status","MATCHES");root.put("dataKind","SIMULATED");root.put("engine","SPRING_AI");
    root.set("criteria",json.valueToTree(t.criteria));
    root.set("recommendations",json.valueToTree(t.presented));
    root.put("matchedCount",t.matches.size());
    root.set("source",json.valueToTree(datasets.info()));
    if(t.place!=null){
     var info=new LinkedHashMap<String,Object>();
     info.put("state","CHECKED");info.put("mode",t.mode);if(t.max!=null)info.put("maxMinutes",t.max);
     info.put("workplace",t.place);info.put("checkedCount",t.checked);info.put("candidateCount",t.all.size());
     info.put("unverifiedCount",t.all.size()-t.checked);info.put("coverage",t.checked<t.all.size()?"PARTIAL":"COMPLETE");info.put("estimated",true);
     root.set("commute",json.valueToTree(info));
     var limits=new ArrayList<>(t.limitations);
     limits.add("路线为社区坐标至工作地点的预计时间，不保证门到门或高峰耗时。");
     if(t.checked<t.all.size())limits.add("仅核验了部分候选房源，其余尚未核验。");
     root.set("limitations",json.valueToTree(limits));
    } else root.set("limitations",json.valueToTree(t.limitations));
    return root;
   }
  }

 private Ticket copy(Ticket t){var n=new Ticket(); n.groups=t.groups==null?null:new ArrayList<>(t.groups);n.all=t.all;n.place=t.place;n.mode=t.mode;n.max=t.max;n.criteria=t.criteria;n.limitations=t.limitations;n.values=t.values;n.offset=t.offset;n.checked=t.checked;n.matches=new ArrayList<>(t.matches);n.observed=new HashMap<>(t.observed); return n;}
 public Response search(Request request) {
  if(request.action()!=null) return action(request.action());
  if(request.workplaceId()!=null && (request.workplaceId().isBlank() || request.workplaceId().length()>100 || !request.workplaceId().matches("[A-Za-z0-9_-]+"))) throw ApiException.invalid("workplaceId","工作地点无效");
  if(request.previousWorkplaceId()!=null && (request.previousWorkplaceId().isBlank() || request.previousWorkplaceId().length()>100 || !request.previousWorkplaceId().matches("[A-Za-z0-9_-]+"))) throw ApiException.invalid("previousWorkplaceId","工作地点无效");
  if(request.messages()==null||request.messages().isEmpty()||request.messages().size()>20) throw ApiException.invalid("messages","消息数量无效");
  int length=0; var human=new ArrayList<String>();
  for(var m:request.messages()) { if(m==null||m.content()==null||m.content().isBlank()||m.content().length()>2000||(!"user".equals(m.role())&&!"assistant".equals(m.role()))) throw ApiException.invalid("messages","消息格式无效"); length+=m.content().length(); if("user".equals(m.role())) human.add(m.content()); }
  if(length>12000||!"user".equals(request.messages().getLast().role())) throw ApiException.invalid("messages","消息长度或顺序无效");
  // Only the exact confirmation produced by this service can give a short reply meaning.
  var messages=request.messages(); String pending=null; boolean declined=false;
  if(messages.size()>=2 && "assistant".equals(messages.get(messages.size()-2).role())) {
   var question=messages.get(messages.size()-2).content();
   var match=java.util.regex.Pattern.compile("是否先按([^，。！？]{1,30})搜索，并暂时不限通勤？$").matcher(question);
   if(match.find()) {
    var proposed=match.group(1); var options=listings.filters();
    boolean canonical=options.towns().stream().anyMatch(o->o.value().equals(proposed)) || options.districts().stream().anyMatch(o->o.value().equals(proposed));
    if(canonical) {
     var reply=messages.getLast().content().strip();
     if(reply.matches("(?i)^(是的|是|可以|好的|好|行|同意|没问题|ok)[。！!，,\\s]*$")) pending=proposed;
     else if(reply.matches(".*(不行|不要|不放宽|不可以|不同意|还是.{0,8}分钟|仍然.{0,8}分钟).*")) declined=true;
    }
   }
  }
  if(pending!=null) human.set(human.size()-1,"同意先按"+pending+"搜索，暂时不限通勤，其余条件保留。");
  Workplace previous=null;
  if(request.previousWorkplaceId()!=null) { try {previous=gateway.detail(request.previousWorkplaceId()).filter(w->w.adcode()!=null && w.adcode().startsWith("31")).orElse(null);} catch(CommuteException e){previous=null;} }
  String answer=extraction.extract(human,previous);
  try {
   answer=answer.strip(); if(answer.startsWith("```")) {answer=answer.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");}
   var root=json.readTree(answer); if(root==null||!root.isObject()||!root.path("criteria").isObject()||!root.path("unsupported").isBoolean()) throw new IllegalArgumentException();
   var topAllowed=Set.of("criteria","unsupported","budgetUnrestricted","workplace","workplaceDistrict","workplaceTown","maxCommuteMinutes","commuteMode","commuteRequested");
   var topFields=root.fieldNames(); while(topFields.hasNext()) if(!topAllowed.contains(topFields.next())) throw new IllegalArgumentException();
   if(root.hasNonNull("commuteRequested")&&!root.get("commuteRequested").isBoolean()) throw new IllegalArgumentException();
   if(root.hasNonNull("commuteMode")&&(!root.get("commuteMode").isTextual()||!Set.of("WALKING","CYCLING","TRANSIT","DRIVING").contains(root.get("commuteMode").asText()))) throw new IllegalArgumentException();
   if(root.hasNonNull("budgetUnrestricted")&&!root.get("budgetUnrestricted").isBoolean()) throw new IllegalArgumentException();
   for(String field:List.of("workplace","workplaceDistrict","workplaceTown")) if(root.hasNonNull(field)&&(!root.get(field).isTextual()||root.get(field).asText().length()>120)) throw new IllegalArgumentException();
   if(root.hasNonNull("maxCommuteMinutes")&&(!root.get("maxCommuteMinutes").asText().matches("[0-9]{1,3}")||root.get("maxCommuteMinutes").asInt()<1||root.get("maxCommuteMinutes").asInt()>180)) throw new IllegalArgumentException();
   var values=new LinkedMultiValueMap<String,String>(); var criteria=new LinkedHashMap<String,Object>();
   var allowed=Set.of("district","town","rentalType","bedrooms","minRent","maxRent","minArea","maxArea","maxMetroWalkMinutes","metroLine","nearestMetro","elevator","petsAllowed","privateBathroom");
   var options=listings.filters();
   var fields=root.path("criteria").fields(); while(fields.hasNext()) { var f=fields.next(); if(!allowed.contains(f.getKey())) throw new IllegalArgumentException(); if(f.getValue().isNull()) continue; if(!f.getValue().isValueNode()||f.getValue().isBoolean()) throw new IllegalArgumentException(); boolean numeric=Set.of("bedrooms","minRent","maxRent","minArea","maxArea","maxMetroWalkMinutes").contains(f.getKey()); if(numeric ? !(f.getValue().isNumber()||f.getValue().isTextual()) : !f.getValue().isTextual()) throw new IllegalArgumentException(); var value=f.getValue().asText().strip(); if(value.isEmpty()) throw new IllegalArgumentException();
    if(f.getKey().equals("district")) {var v=value; var matches=options.districts().stream().map(o->o.value()).filter(x->x.equals(v)||x.equals(v+"区")).toList(); if(matches.size()==1) value=matches.getFirst();}
    if(f.getKey().equals("town")) {var v=value; var matches=options.towns().stream().map(o->o.value()).distinct().filter(x->x.equals(v)||x.equals(v+"镇")||x.equals(v+"街道")).toList(); if(matches.size()==1) value=matches.getFirst();}
    values.add(f.getKey(),value); criteria.put(f.getKey(),numeric?new java.math.BigDecimal(value):value); }
   values.add("size","6"); values.set("sort","rentAsc"); ListingQuery query; try {query=ListingQuery.parse(values);} catch(ApiException e){throw new IllegalArgumentException();}
   var limitations=new ArrayList<String>(); limitations.add("房源为模拟数据，地铁步行时间不代表工作地通勤时间。");
   if(declined) { limitations.add("当前数据不能核验工作地通勤时间或其他不可筛选的要求。"); return new Response("NEEDS_INPUT","已保留通勤条件；当前无法核验，因此暂不能确认符合通勤要求的房源。可以自行修改条件后再检索。",criteria,List.of(),0,limitations,"SIMULATED",datasets.info(),"SPRING_AI"); }
   if(root.path("unsupported").asBoolean()) { limitations.add("部分硬性要求暂无法核验。"); return new Response("NEEDS_INPUT","有其他硬性要求暂时无法核验，请明确修改或取消该要求后再搜索。",criteria,List.of(),0,limitations,"SIMULATED",datasets.info(),"SPRING_AI"); }
   boolean unknown=(criteria.containsKey("district") && options.districts().stream().noneMatch(o->o.value().equals(criteria.get("district"))))
      ||(criteria.containsKey("town") && options.towns().stream().noneMatch(o->o.value().equals(criteria.get("town")) && (!criteria.containsKey("district") || o.district().equals(criteria.get("district")))))
      ||(criteria.containsKey("metroLine") && !options.metroLines().contains(criteria.get("metroLine")))
      ||(criteria.containsKey("nearestMetro") && !options.metroStations().contains(criteria.get("nearestMetro")));
   if(unknown) {limitations.add("所提位置不在模拟数据的可筛选范围内。"); return new Response("NEEDS_INPUT","请确认区域或地铁名称。",criteria,List.of(),0,limitations,"SIMULATED",datasets.info(),"SPRING_AI");}
   boolean commuteActive = root.hasNonNull("maxCommuteMinutes") || (!root.hasNonNull("commuteRequested") || root.path("commuteRequested").asBoolean()) && (root.hasNonNull("commuteMode") || root.path("commuteRequested").asBoolean() || (root.hasNonNull("workplace") && !(criteria.containsKey("district") || criteria.containsKey("town") || criteria.containsKey("metroLine") || criteria.containsKey("nearestMetro"))));
   if(commuteActive) return commute(root, request, values, criteria, limitations, previous);
   if((!criteria.containsKey("maxRent")&&!root.path("budgetUnrestricted").asBoolean(false))||!(criteria.containsKey("district")||criteria.containsKey("town")||criteria.containsKey("metroLine")||criteria.containsKey("nearestMetro"))) return new Response("NEEDS_INPUT","请补充预算上限和期望区域或地铁位置。",criteria,List.of(),0,limitations,"SIMULATED",datasets.info(),"SPRING_AI");
   var t=new Ticket();t.criteria=Map.copyOf(criteria);t.limitations=List.copyOf(limitations);t.values=new LinkedMultiValueMap<>(values);return filterResponse(t);
  } catch(ApiException e) {throw e;} catch(org.springframework.dao.DataAccessException e) {throw e;} catch(Exception e) {throw error(HttpStatus.BAD_GATEWAY,"AI_INVALID_RESPONSE","AI 返回的条件无法解析，请重试");}
 }
 private Response commute(JsonNode root, Request request, LinkedMultiValueMap<String,String> values, Map<String,Object> criteria, List<String> limitations, Workplace previous) {
  Integer max=root.hasNonNull("maxCommuteMinutes")?root.get("maxCommuteMinutes").asInt():null;
  TravelMode mode; try { mode=TravelMode.valueOf(root.path("commuteMode").asText()); } catch(Exception e) {return commuteResponse("NEEDS_INPUT",(root.hasNonNull("workplace")?"到"+root.path("workplace").asText()+"，":"")+(max!=null?max+"分钟以内，":"")+"请选择步行、骑行、公共交通或驾车通勤。",criteria,limitations,"NEEDS_MODE",null,max,null,List.of());}
  if(!criteria.containsKey("maxRent") && !root.path("budgetUnrestricted").asBoolean(false)) return new Response("NEEDS_INPUT","请补充预算上限。",criteria,List.of(),0,limitations,"SIMULATED",datasets.info(),"SPRING_AI");
  Workplace place=null; String name=root.path("workplace").asText("");
  try {
   if(request.workplaceId()!=null && !request.workplaceId().isBlank()) place=gateway.detail(request.workplaceId()).orElse(null);
   else if(previous!=null && !name.isBlank() && normalize(previous.name()).equals(normalize(name)) && districtMatches(previous.district(),root.path("workplaceDistrict").asText("")) && townMatches(previous.address(),root.path("workplaceTown").asText(""))) place=previous;
   else if(!name.isBlank()) {var town=root.path("workplaceTown").asText("").strip(); var found=gateway.search(town.isEmpty()?name:town+" "+name,root.path("workplaceDistrict").asText("")); var exact=found.stream().filter(w->normalize(w.name()).equals(normalize(name))).toList(); if(exact.size()==1) place=exact.getFirst(); else if(found.size()==1) place=found.getFirst(); else if(!found.isEmpty()) return commuteResponse("NEEDS_INPUT","请选择工作地点。",criteria,limitations,"NEEDS_WORKPLACE",mode,max,null,found);}
  } catch(CommuteException e){throw error(HttpStatus.SERVICE_UNAVAILABLE,e.code(),"地图服务暂不可用，请稍后重试");}
  if(place==null || place.adcode()==null || !place.adcode().startsWith("31")) return commuteResponse("NEEDS_INPUT","请提供上海的具体工作地点。",criteria,limitations,"NEEDS_WORKPLACE",mode,max,null,List.of());
  values.set("size","100"); var all=new ArrayList<Listing>(); int page=0; ListingRepository.ListingPage result; do {values.set("page",Integer.toString(page++)); result=listings.find(ListingQuery.parse(values)); all.addAll(result.items());} while(page<result.totalPages());
  final Workplace destination=place; var candidates=all.stream().filter(l->l.longitude()!=null && l.latitude()!=null && l.longitude().doubleValue()!=0 && l.latitude().doubleValue()!=0).sorted(Comparator.comparingDouble(l->distance(l,destination))).toList();
  var byOrigin=new LinkedHashMap<List<java.math.BigDecimal>,List<Listing>>();
  for(var home:candidates) byOrigin.computeIfAbsent(List.of(home.longitude().stripTrailingZeros(),home.latitude().stripTrailingZeros()), k->new ArrayList<>()).add(home);
  var t=new Ticket();t.groups=new ArrayList<>(byOrigin.values());t.all=all;t.place=place;t.mode=mode;t.max=max;t.criteria=Map.copyOf(criteria);t.limitations=List.copyOf(limitations);t.values=new LinkedMultiValueMap<>(values);return runBatch(t);
 }
 private Response runBatch(Ticket t) {
  var groups=t.groups.subList(t.offset,Math.min(t.offset+candidateCap,t.groups.size())); int failures=0; var failedGroups=new ArrayList<List<Listing>>(); int requested=groups.stream().mapToInt(List::size).sum();
  if(!groups.isEmpty() && !routePhase.tryAcquire()) throw error(HttpStatus.SERVICE_UNAVAILABLE,"AMAP_UNAVAILABLE","地图服务繁忙，请稍后重试");
  if(!groups.isEmpty()) try {
   var tasks=groups.stream().<Callable<Optional<RouteEstimate>>>map(group->()->{var home=group.getFirst();return gateway.route(home.longitude().doubleValue(),home.latitude().doubleValue(),t.place,t.mode);}).toList();
   var futures=routeExecutor.invokeAll(tasks,16,TimeUnit.SECONDS);
   for(int i=0;i<futures.size();i++) {var group=groups.get(i); try {var route=futures.get(i).get();t.checked+=group.size(); if(route.isPresent()) for(var home:group)t.observed.put(home.id(),route.get().durationSeconds()); if(route.isPresent() && (t.max==null || route.get().durationSeconds()<=t.max*60L)){var v=route.get(); var detail=Map.of("mode",t.mode,"durationSeconds",v.durationSeconds(),"minutes",(v.durationSeconds()+59)/60,"distanceMeters",v.distanceMeters(),"source","AMAP","estimated",true,"checkedAt",v.checkedAt()); for(var home:group) t.matches.add(new Recommendation(home,List.of(home.district()+" · 月租 "+home.rent()+" 元"),detail));}} catch(Exception e){failures+=group.size();failedGroups.add(group);}}
  } catch(InterruptedException e){Thread.currentThread().interrupt();failures=requested;} catch(RejectedExecutionException e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"AMAP_UNAVAILABLE","地图服务繁忙，请稍后重试");} finally {routeExecutor.purge();routePhase.release();}
  if(requested>0 && failures==requested) throw error(HttpStatus.SERVICE_UNAVAILABLE,"AMAP_UNAVAILABLE","地图服务暂不可用，请稍后重试");
  t.offset+=groups.size(); t.groups.addAll(failedGroups); t.matches.sort(Comparator.comparingLong((Recommendation r)->((Number)((Map<?,?>)r.commute()).get("durationSeconds")).longValue()).thenComparing(r->r.listing().rent()).thenComparing(r->r.listing().id()));
  int unverified=t.all.size()-t.checked; var info=new LinkedHashMap<String,Object>();info.put("state","CHECKED");info.put("mode",t.mode);if(t.max!=null)info.put("maxMinutes",t.max);info.put("workplace",t.place);info.put("checkedCount",t.checked);info.put("candidateCount",t.all.size());info.put("unverifiedCount",unverified);info.put("coverage",unverified>0?"PARTIAL":"COMPLETE");info.put("estimated",true);
  var limits=new ArrayList<>(t.limitations);limits.add("路线为社区坐标至工作地点的预计时间，不保证门到门或高峰耗时。");if(unverified>0)limits.add("仅核验了部分候选房源，其余尚未核验。");boolean more=t.offset<t.groups.size(); if(t.matches.isEmpty())t.adjustments=adjustments(t); t.presented=t.matches.stream().limit(6).map(r->enrich(r,t.criteria,t.max)).toList(); String token=save(t);
  return new Response(t.matches.isEmpty()?"NO_MATCH":"MATCHES",t.matches.isEmpty()?"已核验候选房源中没有匹配项。":"找到符合条件的已核验房源。",t.criteria,t.presented,t.matches.size(),limits,"SIMULATED",datasets.info(),"SPRING_AI",info,null,token,more,t.adjustments,new SortRule("COMMUTE_RENT_ID","预计通勤更短优先，同等通勤月租更低",unverified>0));
 }

 private Response filterResponse(Ticket t){var page=listings.find(ListingQuery.parse(t.values));var recs=page.items().stream().map(l->enrich(new Recommendation(l,List.of()),t.criteria,null)).toList();t.presented=recs;if(page.total()==0)t.adjustments=adjustments(t);String token=save(t);return new Response(page.total()==0?"NO_MATCH":"MATCHES",page.total()==0?"\u5f53\u524d\u6761\u4ef6\u4e0b\u6ca1\u6709\u5339\u914d\u623f\u6e90\u3002":"\u627e\u5230\u7b26\u5408\u7b5b\u9009\u6761\u4ef6\u7684\u6a21\u62df\u623f\u6e90\u3002",t.criteria,recs,page.total(),t.limitations,"SIMULATED",page.source(),"SPRING_AI",null,null,token,false,t.adjustments,new SortRule("RENT_ID","月租由低到高",false));}
 private Recommendation enrich(Recommendation r,Map<String,Object> criteria,Integer max){var insight=RecommendationFacts.build(r.listing(),criteria,r.commute(),max);var reasons=insight.evidence().stream().filter(e->e.status().equals("MATCHED")).sorted(Comparator.comparingInt(e->e.id().equals("commute")?0:e.id().equals("budget")?1:2)).map(RecommendationFacts.Evidence::text).limit(3).toList();return new Recommendation(r.listing(),reasons,r.commute(),insight);}
 private List<Listing> loadAll(LinkedMultiValueMap<String,String> v){var values=new LinkedMultiValueMap<String,String>(v);values.set("size","100");var all=new ArrayList<Listing>();int p=0;ListingRepository.ListingPage page;do {values.set("page",Integer.toString(p++));page=listings.find(ListingQuery.parse(values));all.addAll(page.items());}while(p<page.totalPages());return all;}
 private List<List<Listing>> group(List<Listing> all,Workplace place){var origins=new LinkedHashMap<List<java.math.BigDecimal>,List<Listing>>();all.stream().filter(l->l.longitude()!=null&&l.latitude()!=null&&l.longitude().doubleValue()!=0&&l.latitude().doubleValue()!=0).sorted(Comparator.comparingDouble(l->distance(l,place))).forEach(l->origins.computeIfAbsent(List.of(l.longitude().stripTrailingZeros(),l.latitude().stripTrailingZeros()),k->new ArrayList<>()).add(l));return new ArrayList<>(origins.values());}
 private List<Adjustment> adjustments(Ticket t){var result=new ArrayList<Adjustment>();if(t.max!=null){var next=t.observed.values().stream().filter(v->v>t.max*60L).mapToLong(v->(v+59)/60).min();if(next.isPresent()&&next.getAsLong()<=(long)t.max+30&&next.getAsLong()<=180){int minutes=(int)next.getAsLong();int count=(int)t.observed.values().stream().filter(v->v<=minutes*60L).count();result.add(new Adjustment("time","\u8c03\u6574\u901a\u52e4\u4e0a\u9650","\u901a\u52e4\u4e0a\u9650\u4ece "+t.max+" \u5206\u949f\u8c03\u6574\u81f3 "+minutes+" \u5206\u949f\uff0c\u5176\u4ed6\u6761\u4ef6\u4fdd\u6301\u4e0d\u53d8\u3002",List.of(new Change("maxCommuteMinutes",t.max,minutes)),count,"VERIFIED_ROUTE"));}}
 if(t.criteria.get("maxRent") instanceof Number old){var v=new LinkedMultiValueMap<String,String>(t.values);v.remove("maxRent");var all=loadAll(v);var next=all.stream().map(Listing::rent).filter(r->r.compareTo(new java.math.BigDecimal(old.toString()))>0).min(Comparator.naturalOrder());if(next.isPresent()&&next.get().compareTo(new java.math.BigDecimal(old.toString()).add(new java.math.BigDecimal("3000")))<=0){var rent=next.get();int count=(int)all.stream().filter(l->l.rent().compareTo(rent)<=0).count();result.add(new Adjustment("rent","\u8c03\u6574\u9884\u7b97\u4e0a\u9650","\u9884\u7b97\u4e0a\u9650\u4ece "+old+" \u5143\u8c03\u6574\u81f3 "+rent+" \u5143\uff1b\u5176\u4ed6\u6761\u4ef6\u4fdd\u6301\u4e0d\u53d8\uff0c\u4ec5\u7b5b\u9009\u5019\u9009\uff0c\u901a\u52e4\u5c1a\u672a\u6838\u9a8c\u3002",List.of(new Change("maxRent",old,rent)),count,"FILTER_ONLY"));}}return List.copyOf(result);}
 private Response commuteResponse(String status,String message,Map<String,Object> criteria,List<String> limitations,String state,TravelMode mode,Integer max,Workplace place,List<Workplace> choices){var info=new LinkedHashMap<String,Object>();info.put("state",state);if(mode!=null)info.put("mode",mode);if(max!=null)info.put("maxMinutes",max);if(place!=null)info.put("workplace",place);info.put("checkedCount",0);info.put("candidateCount",0);info.put("unverifiedCount",0);info.put("coverage","COMPLETE");info.put("estimated",true);return new Response(status,message,criteria,List.of(),0,limitations,"SIMULATED",datasets.info(),"SPRING_AI",info,choices.isEmpty()?null:choices);}
 private String normalize(String value){return value==null?"":value.replaceAll("[\\s\\p{Punct}，。·（）]","").toLowerCase(Locale.ROOT);}
 private boolean districtMatches(String district,String requested){var a=normalize(district);var b=normalize(requested);return b.isEmpty() || (!a.isEmpty() && (a.equals(b) || a.equals(b+"区")));}
 private boolean townMatches(String address,String requested){var town=normalize(requested);return town.isEmpty() || (!town.isEmpty() && normalize(address).contains(town));}
 private double distance(Listing l, Workplace w){double a=Math.toRadians(l.latitude().doubleValue()),b=Math.toRadians(w.latitude()),d=b-a,e=Math.toRadians(w.longitude()-l.longitude().doubleValue());double x=Math.sin(d/2)*Math.sin(d/2)+Math.cos(a)*Math.cos(b)*Math.sin(e/2)*Math.sin(e/2);return 2*Math.atan2(Math.sqrt(x),Math.sqrt(Math.max(0,1-x)));}
 private static ApiException error(HttpStatus status,String code,String message){return new ApiException(status,code,message,Map.of());}
}










