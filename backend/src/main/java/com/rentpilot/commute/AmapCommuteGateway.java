package com.rentpilot.commute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AmapCommuteGateway implements CommuteGateway {
 private final String key, base;
 private int readTimeoutMillis = 4000;

 private final ObjectMapper mapper = new ObjectMapper();
 private final Semaphore capacity = new Semaphore(8);
 private final Object routeRateLock=new Object();
 private long lastRouteStart;
 // Spread uncached route starts to stay within the provider's per-key rate limit.
 private void paceRoute(){synchronized(routeRateLock){try {if(Thread.currentThread().isInterrupted()) throw new InterruptedException();long wait=lastRouteStart+TimeUnit.MILLISECONDS.toNanos(500)-System.nanoTime();if(lastRouteStart!=0 && wait>0)TimeUnit.NANOSECONDS.sleep(wait);if(Thread.currentThread().isInterrupted()) throw new InterruptedException();lastRouteStart=System.nanoTime();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CommuteException("AMAP_UNAVAILABLE");}}}

 private record Entry<T>(T value, long expires) {}
 private final Map<String,Entry<List<Workplace>>> searches = new LinkedHashMap<>();
 private final Map<String,Entry<Optional<Workplace>>> details = new LinkedHashMap<>();
 private final Map<String,Entry<Optional<RouteEstimate>>> routes = new LinkedHashMap<>();
 public AmapCommuteGateway(@Value("${rentpilot.amap.web-service-key:}") String key, @Value("${rentpilot.amap.service-base-url:https://restapi.amap.com}") String base) { this.key=key; this.base=base.replaceAll("/+$", ""); }
 private synchronized <T> T get(Map<String,Entry<T>> cache,String k) { Entry<T> e=cache.get(k); if(e==null)return null; if(e.expires<System.currentTimeMillis()){cache.remove(k);return null;} return e.value; }
 private synchronized <T> void put(Map<String,Entry<T>> cache,String k,T value,long ttl,int cap) { cache.remove(k); while(cache.size()>=cap)cache.remove(cache.keySet().iterator().next()); cache.put(k,new Entry<>(value,System.currentTimeMillis()+ttl)); }
 private JsonNode request(String path, Map<String,String> params) {
  if(key==null || key.isBlank()) throw new CommuteException("AMAP_NOT_CONFIGURED");
  boolean acquired=false;
  try {
   acquired=capacity.tryAcquire(4,TimeUnit.SECONDS); if(!acquired) throw new CommuteException("AMAP_UNAVAILABLE");
   StringBuilder u=new StringBuilder(base).append(path).append("?key=").append(enc(key));
   params.forEach((k,v)->u.append('&').append(enc(k)).append('=').append(enc(v)));
   HttpURLConnection connection=(HttpURLConnection) URI.create(u.toString()).toURL().openConnection();
   connection.setConnectTimeout(3000); connection.setReadTimeout(readTimeoutMillis); connection.setInstanceFollowRedirects(false);
   if(connection.getResponseCode()!=200) { connection.disconnect(); throw new CommuteException("AMAP_UNAVAILABLE"); }
   try(var in=connection.getInputStream()) {
    long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis); var output=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n; while(true){ long remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()); if(remaining<=0) throw new java.net.SocketTimeoutException(); connection.setReadTimeout((int)Math.max(1,remaining)); n=in.read(buffer); if(n<0)break; output.write(buffer,0,n); if(output.size()>1048576)throw new CommuteException("AMAP_INVALID_RESPONSE"); } byte[] data=output.toByteArray();
    JsonNode root=mapper.readTree(data); if(root==null || !root.isObject()) throw new CommuteException("AMAP_INVALID_RESPONSE");
    if(root.has("status") && !"1".equals(root.path("status").asText())) throw new CommuteException("AMAP_UNAVAILABLE");
    if(root.has("errcode") && !"0".equals(root.path("errcode").asText())) throw new CommuteException("AMAP_UNAVAILABLE");
    if(!root.has("status") && !root.has("errcode")) throw new CommuteException("AMAP_INVALID_RESPONSE");
    return root;
   } finally { connection.disconnect(); }
  } catch(CommuteException e){throw e;} catch(InterruptedException e){Thread.currentThread().interrupt();throw new CommuteException("AMAP_UNAVAILABLE");} catch(Exception e){throw new CommuteException("AMAP_UNAVAILABLE");} finally {if(acquired)capacity.release();}
 }
 private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
 private static boolean coord(double x,double y){return Double.isFinite(x)&&Double.isFinite(y)&&x>0&&x<=180&&y>0&&y<=90;}
 private Workplace poi(JsonNode p){
  String id=p.path("id").asText(""), name=p.path("name").asText(""), ad=p.path("adcode").asText("");
  try {String[] xy=p.path("location").asText("").split(","); double x=Double.parseDouble(xy[0]),y=Double.parseDouble(xy[1]); if(id.isBlank()||name.isBlank()||!ad.matches("31\\d{4}")||!coord(x,y))return null; return new Workplace(id,name,text(p,"address"),text(p,"adname"),ad,x,y);}catch(Exception e){return null;}
 }
 private static String text(JsonNode n,String k){JsonNode v=n.path(k);return v.isTextual()?v.asText():"";}
 public List<Workplace> search(String query,String district){
  if(query==null||query.isBlank())return List.of(); String k=query.trim()+"\u0000"+(district==null?"":district.trim()); List<Workplace> c=get(searches,k);if(c!=null)return c;
  JsonNode a=request("/v3/place/text",Map.of("keywords",(district==null||district.isBlank()?query.trim():district.trim()+" "+query.trim()),"city","310000","citylimit","true","extensions","all","offset","20","page","1")).path("pois"); if(!a.isArray())throw new CommuteException("AMAP_INVALID_RESPONSE");
  List<Workplace> out=new ArrayList<>();for(JsonNode n:a){Workplace p=poi(n);if(p!=null&&(district==null||district.isBlank()||p.district().contains(district.trim()))){out.add(p);if(out.size()==5)break;}} List<Workplace> result=List.copyOf(out);put(searches,k,result,3600000,256);return result;
 }
 public Optional<Workplace> detail(String id){if(id==null||id.isBlank())return Optional.empty();Optional<Workplace> c=get(details,id);if(c!=null)return c;JsonNode a=request("/v3/place/detail",Map.of("id",id,"extensions","all")).path("pois");if(!a.isArray())throw new CommuteException("AMAP_INVALID_RESPONSE");Optional<Workplace> result=Optional.empty();for(JsonNode n:a){Workplace p=poi(n);if(p!=null&&p.id().equals(id)){result=Optional.of(p);break;}}put(details,id,result,3600000,256);return result;}
 private static long number(JsonNode n){try{String s=n.asText();if(!s.matches("\\d{1,10}"))return -1;long x=Long.parseLong(s);return x>0&&x<=100000000?x:-1;}catch(Exception e){return -1;}}
 public Optional<RouteEstimate> route(double x,double y,Workplace d,TravelMode mode){
  if(d==null||mode==null||!coord(x,y)||!coord(d.longitude(),d.latitude())||d.adcode()==null||!d.adcode().matches("31\\d{4}"))throw new CommuteException("AMAP_INVALID_RESPONSE");
  String origin=Double.toString(x)+","+Double.toString(y), dest=Double.toString(d.longitude())+","+Double.toString(d.latitude());String k=origin+"|"+dest+"|"+mode;Optional<RouteEstimate> cached=get(routes,k);if(cached!=null)return cached;
  String path=switch(mode){case WALKING->"/v3/direction/walking";case CYCLING->"/v4/direction/bicycling";case TRANSIT->"/v3/direction/transit/integrated";case DRIVING->"/v3/direction/driving";};
  Map<String,String> params=new HashMap<>();params.put("origin",origin);params.put("destination",dest);if(mode==TravelMode.TRANSIT){params.put("city","上海");params.put("cityd","上海");params.put("strategy","0");}
  paceRoute(); JsonNode root=request(path,params);JsonNode container=mode==TravelMode.CYCLING?root.path("data"):root.path("route");JsonNode paths=container.path(mode==TravelMode.TRANSIT?"transits":"paths");if(!paths.isArray())throw new CommuteException("AMAP_INVALID_RESPONSE");
  long best=Long.MAX_VALUE, distance=-1;for(JsonNode p:paths){long seconds=number(p.path("duration"));if(seconds>604800) seconds=-1;long meters=number(p.path("distance"));if(seconds>0&&meters>0&&seconds<best){best=seconds;distance=meters;}}
  if(!paths.isEmpty()&&best==Long.MAX_VALUE)throw new CommuteException("AMAP_INVALID_RESPONSE");Optional<RouteEstimate> result=best==Long.MAX_VALUE?Optional.empty():Optional.of(new RouteEstimate(best,distance,Instant.now()));put(routes,k,result,600000,2048);return result;
 }
}



