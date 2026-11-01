package com.rentpilot.commute;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class AmapCommuteGatewayTest {
 @Test void routesAndCache() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); AtomicInteger calls=new AtomicInteger();
  server.createContext("/",exchange->{calls.incrementAndGet();String path=exchange.getRequestURI().getPath();String body=path.contains("bicycling")?"{\"errcode\":0,\"data\":{\"paths\":[{\"duration\":\"30\",\"distance\":\"50\"}]}}":path.contains("integrated")?"{\"status\":\"1\",\"route\":{\"distance\":\"50\",\"transits\":[{\"duration\":\"30\",\"distance\":\"50\"}]}}":"{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"30\",\"distance\":\"50\"}]}}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
  try {var gateway=new AmapCommuteGateway("secret","http://127.0.0.1:"+server.getAddress().getPort());var workplace=new Workplace("id","name","","","310112",121.4,31.0);for(var mode:TravelMode.values()){assertEquals(30,gateway.route(121.5,31.1,workplace,mode).orElseThrow().durationSeconds());gateway.route(121.5,31.1,workplace,mode);}assertEquals(4,calls.get());}finally{server.stop(0);}
 }
 @Test void concurrentRouteMissesArePacedAndCacheHitsSkipTheGate() throws Exception {
  var arrivals=new java.util.concurrent.CopyOnWriteArrayList<Long>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{arrivals.add(System.nanoTime());byte[] b="{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"30\",\"distance\":\"50\"}]}}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();var pool=java.util.concurrent.Executors.newFixedThreadPool(3);
  try {var g=new AmapCommuteGateway("fixture","http://127.0.0.1:"+server.getAddress().getPort());var d=new Workplace("id","n","","","310112",121.4,31);var tasks=new java.util.ArrayList<java.util.concurrent.Future<?>>();for(int i=0;i<3;i++){final double lon=121.5+i*0.01;tasks.add(pool.submit(()->g.route(lon,31,d,TravelMode.WALKING)));}for(var f:tasks)f.get();assertEquals(3,arrivals.size());for(int i=1;i<3;i++)assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(arrivals.get(i)-arrivals.get(i-1))>=400,"provider requests must be spaced");long start=System.nanoTime();g.route(121.5,31,d,TravelMode.WALKING);assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<300,"cached route must not wait");assertEquals(3,arrivals.size());}finally{pool.shutdownNow();server.stop(0);}
 }
 @Test void interruptedWaitingRouteDoesNotReachProvider() throws Exception {
  var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{calls.incrementAndGet();byte[] b="{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"30\",\"distance\":\"50\"}]}}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{var g=new AmapCommuteGateway("fixture","http://127.0.0.1:"+server.getAddress().getPort());var d=new Workplace("id","n","","","310112",121.4,31);g.route(121.5,31,d,TravelMode.WALKING);var started=new java.util.concurrent.CountDownLatch(1);var f=pool.submit(()->{started.countDown();return g.route(121.6,31,d,TravelMode.WALKING);});assertTrue(started.await(1,java.util.concurrent.TimeUnit.SECONDS));f.cancel(true);pool.shutdown();assertTrue(pool.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,calls.get());}finally{pool.shutdownNow();server.stop(0);}
 }
 @Test void invalidInputAndMissingKey() {
  var g=new AmapCommuteGateway("", "http://127.0.0.1:1");
  var d=new Workplace("id","n","","","310112",121.4,31.0);
  assertEquals("AMAP_INVALID_RESPONSE",assertThrows(CommuteException.class,()->g.route(Double.NaN,31,d,TravelMode.WALKING)).code());
  assertEquals("AMAP_NOT_CONFIGURED",assertThrows(CommuteException.class,()->g.route(121.5,31,d,TravelMode.WALKING)).code());
 }
 @Test void emptyMalformedQuotaAndCity() throws Exception {
  var body=new java.util.concurrent.atomic.AtomicReference<String>("{\"status\":\"1\",\"route\":{\"paths\":[]}}");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{byte[] b=body.get().getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();
  try {var g=new AmapCommuteGateway("secret","http://127.0.0.1:"+server.getAddress().getPort());var d=new Workplace("id","n","","","310112",121.4,31);
   assertTrue(g.route(121.5,31,d,TravelMode.WALKING).isEmpty());
   body.set("{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"-1\",\"distance\":\"50\"}]}}");
   assertEquals("AMAP_INVALID_RESPONSE",assertThrows(CommuteException.class,()->g.route(121.6,31,d,TravelMode.WALKING)).code());
   body.set("{\"status\":\"0\",\"info\":\"secret quota\"}");var ex=assertThrows(CommuteException.class,()->g.route(121.7,31,d,TravelMode.WALKING));assertEquals("AMAP_UNAVAILABLE",ex.code());assertFalse(ex.toString().contains("secret"));
   body.set("{\"status\":\"1\",\"pois\":[{\"id\":\"x\",\"name\":\"n\",\"adcode\":\"330100\",\"location\":\"120,30\"}]}");assertTrue(g.search("x",null).isEmpty());
  } finally {server.stop(0);}
 }
 @Test void httpOversizeAndStalledBody() throws Exception {
  var kind=new java.util.concurrent.atomic.AtomicInteger();
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{try {if(kind.get()==0){e.sendResponseHeaders(500,-1);}else if(kind.get()==1){byte[] b=new byte[1048577];e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);}else{e.sendResponseHeaders(200,10);e.getResponseBody().write(123);e.getResponseBody().flush();Thread.sleep(700);} }catch(Exception ignored){}finally{e.close();}});server.start();
  try{var g=new AmapCommuteGateway("secret","http://127.0.0.1:"+server.getAddress().getPort());var f=AmapCommuteGateway.class.getDeclaredField("readTimeoutMillis");f.setAccessible(true);f.setInt(g,100);var d=new Workplace("id","n","","","310112",121.4,31);
   assertEquals("AMAP_UNAVAILABLE",assertThrows(CommuteException.class,()->g.route(121.5,31,d,TravelMode.WALKING)).code());kind.set(1);assertEquals("AMAP_INVALID_RESPONSE",assertThrows(CommuteException.class,()->g.route(121.6,31,d,TravelMode.WALKING)).code());kind.set(2);assertEquals("AMAP_UNAVAILABLE",assertThrows(CommuteException.class,()->g.route(121.7,31,d,TravelMode.WALKING)).code());
  }finally{server.stop(0);}
 }
}
