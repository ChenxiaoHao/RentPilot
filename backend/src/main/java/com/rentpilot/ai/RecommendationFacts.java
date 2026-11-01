package com.rentpilot.ai;
import com.rentpilot.listing.Listing;
import com.rentpilot.listing.TriState;
import java.math.BigDecimal;
import java.util.*;
final class RecommendationFacts {
 record Evidence(String id,String label,String text,String status,String source,List<String> fields) {}
 record Costs(BigDecimal monthlyRent,BigDecimal deposit,String paymentMethod,BigDecimal propertyFee,String propertyFeePeriod,Integer prepaidMonths,BigDecimal firstRentPayment,BigDecimal knownInitialPayment,List<String> excludedItems) {}
 record Insight(List<Evidence> evidence,Costs costs) {}
 static Insight build(Listing l,Map<String,Object> criteria,Object commute) { return build(l,criteria,commute,null); }
 static Insight build(Listing l,Map<String,Object> criteria,Object commute,Integer maxMinutes) {
  Integer months=switch(l.paymentMethod()==null?"":l.paymentMethod()){case "押一付一"->1;case "押一付三","押二付三"->3;default->null;};
  BigDecimal first=months==null||l.rent()==null?null:l.rent().multiply(BigDecimal.valueOf(months));
  var costs=new Costs(l.rent(),l.deposit(),l.paymentMethod(),l.propertyFee(),"UNSPECIFIED",months,first,first==null||l.deposit()==null?null:first.add(l.deposit()),List.of("物业费周期未知","水电费","网络费"));
  var e=new ArrayList<Evidence>();
  if(criteria.get("maxRent") instanceof Number n && l.rent()!=null) {var d=new BigDecimal(n.toString()).subtract(l.rent());e.add(new Evidence("budget","预算",d.signum()>0?"月租比预算上限低 "+d.stripTrailingZeros().toPlainString()+" 元":d.signum()==0?"月租符合预算上限":"月租超过预算上限 "+d.abs().stripTrailingZeros().toPlainString()+" 元",d.signum()>=0?"MATCHED":"UNVERIFIED","LISTING",List.of("rent")));}
  bound(e,criteria,"minRent","租金下限",l.rent(),"rent",true); bound(e,criteria,"minArea","面积下限",l.area(),"area",true); bound(e,criteria,"maxArea","面积上限",l.area(),"area",false); bound(e,criteria,"maxMetroWalkMinutes","地铁步行上限",l.metroWalkMinutes(),"metroWalkMinutes",false);
  simple(e,criteria,"district","区域",l.district(),"district"); simple(e,criteria,"town","板块",l.town(),"town");
  simple(e,criteria,"rentalType","租赁方式",l.rentalType(),"rentalType"); simple(e,criteria,"bedrooms","居室",l.bedrooms(),"bedrooms");
  simple(e,criteria,"nearestMetro","地铁站",l.nearestMetro(),"nearestMetro");
  if(criteria.containsKey("metroLine")) {boolean match=l.metroLines()!=null&&l.metroLines().contains(criteria.get("metroLine").toString()); e.add(new Evidence("metroLine","地铁线路",match?"地铁线路："+criteria.get("metroLine"):"地铁线路信息待核验",match?"MATCHED":"UNVERIFIED","LISTING",List.of("metroLines")));}
  amenity(e,criteria,"elevator","电梯",l.amenities()==null?null:l.amenities().elevator());
  amenity(e,criteria,"petsAllowed","养宠",l.amenities()==null?null:l.amenities().petsAllowed());
  amenity(e,criteria,"privateBathroom","独立卫生间",l.amenities()==null?null:l.amenities().privateBathroom());
  if(commute instanceof Map<?,?> route && route.get("durationSeconds") instanceof Number seconds){long s=seconds.longValue();boolean match=maxMinutes==null||s<=maxMinutes*60L;e.add(new Evidence("commute","预计通勤","高德预计"+switch(String.valueOf(route.get("mode"))){case "WALKING"->"步行";case "CYCLING"->"骑行";case "TRANSIT"->"公共交通";case "DRIVING"->"驾车";default->"通勤";}+((s+59)/60)+" 分钟",match?"MATCHED":"UNVERIFIED","AMAP",List.of("commute.mode","commute.durationSeconds")));}
  e.add(new Evidence("propertyFee","物业费","物业费周期未知","UNVERIFIED","LISTING",List.of("propertyFee","propertyFeePeriod")));
  e.add(new Evidence("rent","月租","月租 "+l.rent()+" 元","MATCHED","LISTING",List.of("rent")));
  return new Insight(List.copyOf(e),costs);
 }
 private static void bound(List<Evidence> e,Map<String,Object> c,String key,String label,Number value,String field,boolean min){if(!(c.get(key) instanceof Number limit))return;boolean match=value!=null&&(min?new BigDecimal(value.toString()).compareTo(new BigDecimal(limit.toString()))>=0:new BigDecimal(value.toString()).compareTo(new BigDecimal(limit.toString()))<=0);e.add(new Evidence(key,label,value==null?label+"信息未知":(field.equals("area")?"面积 "+value+" 平方米":field.equals("rent")?"月租 "+value+" 元":"地铁步行 "+value+" 分钟")+(match?"，符合":"，不符合")+(min?"不少于 ":"不超过 ")+limit+(field.equals("area")?" 平方米":field.equals("rent")?" 元":" 分钟"),match?"MATCHED":"UNVERIFIED","LISTING",List.of(field)));}
 private static void simple(List<Evidence> e,Map<String,Object> c,String key,String label,Object actual,String field) {if(!c.containsKey(key))return; boolean match=actual!=null&&actual.toString().equals(c.get(key).toString());String display=actual==null?"未知":actual.toString();if(key.equals("bedrooms")&&actual!=null)display+=" 居室";if(key.equals("rentalType"))display=display.equals("ENTIRE")?"整租":display.equals("SHARED")?"合租":display;e.add(new Evidence(key,label,label+"："+display,match?"MATCHED":"UNVERIFIED","LISTING",List.of(field)));}
 private static void amenity(List<Evidence> e,Map<String,Object> c,String key,String label,TriState v){if(!c.containsKey(key))return; boolean known=v!=null&&v!=TriState.UNKNOWN;boolean match=known&&v.toString().equals(c.get(key).toString());e.add(new Evidence(key,label,label+"："+(!known?"未知":v==TriState.YES?"有":"无"),match?"MATCHED":"UNVERIFIED","LISTING",List.of("amenities."+key)));}
}
