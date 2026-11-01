package com.rentpilot.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rentpilot.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import static com.rentpilot.plan.LifesimModels.*;
import static com.rentpilot.plan.PlanModels.PlanSummary;

@Service
public class LifesimService {
    private static final String DEFAULT_WORK_END="18:00";
    private static final String DEFAULT_OVERTIME_END="21:00";

    private final LivingPlanService plans;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public LifesimService(LivingPlanService plans,JdbcTemplate jdbc,ObjectMapper json){
        this.plans=plans;this.jdbc=jdbc;this.json=json;
    }

    public LifesimView get(String memberId,String planId){
        var detail=plans.detail(memberId,planId);
        var stored=readStored(detail.plan().versionId());
        return compute(detail.plan(),detail.snapshot(),stored.answers(),stored.updatedAt());
    }

    @Transactional
    public LifesimView save(String memberId,String planId,LifesimInput input){
        if(input==null)throw ApiException.invalid("lifesim","请先填写你的作息");
        var detail=plans.detail(memberId,planId);
        var answers=new LifesimInput(input.commuteMinutes(),trim(input.workStart()),trim(input.sleepTime()),input.overtimeDays(),
                trim(input.overtimeEnd()),trim(input.dailySpendBand()),input.fixedCost(),input.cookingDays());
        var root=json.createObjectNode();
        root.set("answers",json.valueToTree(answers));
        root.put("updatedAt",OffsetDateTime.now().toString());
        jdbc.update("UPDATE living_plan_version SET lifesim_json=? WHERE id=?",write(root),detail.plan().versionId());
        return compute(detail.plan(),detail.snapshot(),answers,root.path("updatedAt").asText());
    }

    private LifesimView compute(PlanSummary plan,JsonNode snapshot,LifesimInput answers,String updatedAt){
        var primary=snapshot.path("recommendations").path(0);
        var listing=primary.path("listing");
        var costsNode=primary.path("insight").path("costs");
        Integer factCommute=number(primary.path("commute").path("minutes"));Integer commute=answers.commuteMinutes()!=null?answers.commuteMinutes():factCommute;
        String mode=snapshot.path("commute").path("mode").asText(null);
        if(mode==null)mode=primary.path("commute").path("mode").asText(null);

        BigDecimal rent=firstDecimal(costsNode,"monthlyRent",decimal(listing,"rent"));
        BigDecimal deposit=firstDecimal(costsNode,"deposit",decimal(listing,"deposit"));
        BigDecimal initial=decimal(costsNode,"knownInitialPayment");
        BigDecimal propertyFee=firstDecimal(costsNode,"propertyFee",decimal(listing,"propertyFee"));
        String propertyPeriod=costsNode.path("propertyFeePeriod").asText(listing.path("propertyFeePeriod").asText("UNSPECIFIED"));
        BigDecimal monthlyProperty=propertyFee;
        if(propertyFee!=null&&"YEARLY".equals(propertyPeriod))monthlyProperty=propertyFee.divide(BigDecimal.valueOf(12),0,RoundingMode.HALF_UP);
        boolean propertyKnown=monthlyProperty!=null&&!("UNSPECIFIED".equals(propertyPeriod)||"NONE".equals(propertyPeriod));
        if(!propertyKnown)monthlyProperty=null;

        boolean workKnown=answers.workStart()!=null;
        boolean sleepKnown=answers.sleepTime()!=null;
        int overtimeDays=answers.overtimeDays()==null?0:answers.overtimeDays();
        boolean overtimeKnown=answers.overtimeDays()!=null;
        String overtimeEnd=answers.overtimeEnd()==null?DEFAULT_OVERTIME_END:answers.overtimeEnd();
        String workEnd=overtimeDays>0?overtimeEnd:DEFAULT_WORK_END;

        var timeline=new ArrayList<LifesimMoment>();
        var sources=new LinkedHashMap<String,String>();
        sources.put("commute",answers.commuteMinutes()!=null?"USER":factCommute==null?"UNKNOWN":"FACT");
        sources.put("monthlyRent",rent==null?"UNKNOWN":"FACT");
        sources.put("deposit",deposit==null?"UNKNOWN":"FACT");
        sources.put("knownInitialPayment",initial==null?"UNKNOWN":"FACT");
        sources.put("propertyFee",propertyKnown?"FACT":"UNKNOWN");
        sources.put("workStart",workKnown?"USER":"ASSUMED");
        sources.put("sleepTime",sleepKnown?"USER":"ASSUMED");
        sources.put("overtime",overtimeKnown?"USER":"ASSUMED");
        sources.put("dailySpend",answers.dailySpendBand()==null?"UNKNOWN":"USER");
        sources.put("fixedCost",answers.fixedCost()==null?"UNKNOWN":"USER");
        sources.put("cookingDays",answers.cookingDays()==null?"UNKNOWN":"USER");

        String status;
        if(commute==null){
            status="NEEDS_COMMUTE";
        } else {
            status=workKnown&&sleepKnown?"READY":"INCOMPLETE";
            String workStart=workKnown?answers.workStart():"09:00";
            String sleepTime=sleepKnown?answers.sleepTime():"23:30";
            String routineSource=workKnown?"DERIVED":"ASSUMED";
            int start=minutes(workStart);
            int sleep=minutes(sleepTime);
            int end=minutes(workEnd);
            int leave=start-commute;
            int arrive=end+commute;
            timeline.add(new LifesimMoment("leave","出门",clock(leave),routineSource,"按 "+workStart+" 上班与 "+commute+" 分钟"+modeLabel(mode)+"倒推，含 0 分钟准备时间"));
            timeline.add(new LifesimMoment("office","到公司",workStart,routineSource,"通勤 "+commute+" 分钟"));
            timeline.add(new LifesimMoment("leaveOffice","下班",workEnd,overtimeDays>0?"USER":"ASSUMED",overtimeDays>0?"按每周 "+overtimeDays+" 天加班，假设 "+workEnd+" 下班":"按 18:00 下班假设"));
            timeline.add(new LifesimMoment("home","到家",clock(arrive),routineSource,"下班后再通勤 "+commute+" 分钟"));
            int free=sleep-arrive;
            if(free>=0){
                timeline.add(new LifesimMoment("free","可自由安排",clock(arrive)+"–"+sleepTime,routineSource,duration(free)+"，不含晚饭与家务时间"));
            } else {
                timeline.add(new LifesimMoment("free","可自由安排","未知","UNKNOWN","到家时间已晚于睡觉时间，无法推算"));
                sources.put("freeTime","UNKNOWN");
            }
            timeline.add(new LifesimMoment("sleep","睡觉",sleepTime,sleepKnown?"USER":"ASSUMED",sleepKnown?"按你填写的时间":"默认 23:30 睡觉假设"));
        }
        if(!sources.containsKey("freeTime"))sources.put("freeTime",commute==null?"UNKNOWN":workKnown&&sleepKnown?"DERIVED":"ASSUMED");

        var band=band(answers.dailySpendBand());
        BigDecimal fixed=answers.fixedCost();
        BigDecimal monthlyKnown=add(add(rent,monthlyProperty),fixed);
        BigDecimal low=null,high=null;
        if(band!=null&&monthlyKnown!=null){
            low=monthlyKnown.add(BigDecimal.valueOf(band.low()));
            high=band.high()==null?null:monthlyKnown.add(BigDecimal.valueOf(band.high()));
        } else if(band==null&&monthlyKnown!=null){
            low=monthlyKnown;high=monthlyKnown;
        }
        var costs=new LifesimCosts(rent,deposit,propertyFee,propertyPeriod,initial,fixed,
                answers.dailySpendBand(),band==null?"未填写":band.label(),monthlyKnown,low,high);

        var assumptions=new LinkedHashMap<String,Object>();
        assumptions.put("workEnd",DEFAULT_WORK_END);assumptions.put("commuteSource",answers.commuteMinutes()!=null?"用户填写的估计值":"方案已核验的通勤时间");
        assumptions.put("overtimeEnd",overtimeEnd);
        assumptions.put("commuteScope","小区参考坐标到工作地点，不含门到门与高峰等待");
        assumptions.put("preparationMinutes",0);
        assumptions.put("freeTimeExcludes","晚饭、家务、购物等必要时间");
        assumptions.put("dailySpendMode","你选择的区间，不是系统估算");

        var limits=new ArrayList<String>();
        limits.add("时间账本按你填写的时间和系统固定假设推算，不代表实际生活。");if(!workKnown||!sleepKnown)limits.add("未填写的上班/睡觉时间使用 09:00 与 23:30 默认假设，标为假设。");
        limits.add("通勤为小区参考坐标到工作地点的预计值，不保证门到门或高峰耗时。");
        if("PARTIAL".equals(plan.coverage())||"PARTIAL".equals(snapshot.path("commute").path("coverage").asText()))limits.add("通勤只核验了部分候选房源，未核验不代表不符合。");
        if(answers.commuteMinutes()!=null)limits.add("通勤时间使用你填写的估计值，不是高德路线核验结果。");if(!propertyKnown)limits.add("物业费周期未注明，未计入月度合计。");
        if(band==null)limits.add("日常开销未填写，月度合计只包含已知固定支出。");
        limits.add("本页不生成消费、幸福感、天气、社区评分等没有数据的内容。");

        return new LifesimView(plan.id(),plan.versionId(),plan.versionNo(),plan.title(),plan.capturedAt(),
                status,mode,commute,answers,assumptions,timeline,costs,sources,limits,updatedAt);
    }

    private Stored readStored(String versionId){
        String raw=jdbc.queryForObject("SELECT lifesim_json FROM living_plan_version WHERE id=?",String.class,versionId);
        if(raw==null||raw.isBlank())return new Stored(new LifesimInput(null,null,null,null,null,null,null,null),null);
        try{
            var root=json.readTree(raw);
            var a=root.path("answers");
            return new Stored(new LifesimInput(integer(a,"commuteMinutes"),text(a,"workStart"),text(a,"sleepTime"),integer(a,"overtimeDays"),
                    text(a,"overtimeEnd"),text(a,"dailySpendBand"),decimal(a,"fixedCost"),integer(a,"cookingDays")),
                    root.path("updatedAt").asText(null));
        }catch(Exception e){
            throw new IllegalStateException("Stored lifesim is invalid",e);
        }
    }

    private String write(Object node){
        try{return json.writeValueAsString(node);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Unable to store lifesim",e);}
    }

    private record Stored(LifesimInput answers,String updatedAt){}
    private record Band(String label,int low,Integer high){}

    private Band band(String value){
        if(value==null)return null;
        return switch(value){
            case "UNDER_1000" -> new Band("1000 元以内",0,1000);
            case "1000_2000" -> new Band("1000–2000 元",1000,2000);
            case "2000_3000" -> new Band("2000–3000 元",2000,3000);
            case "OVER_3000" -> new Band("3000 元以上",3000,null);
            default -> null;
        };
    }

    private static int minutes(String hhmm){var parts=hhmm.split(":");return Integer.parseInt(parts[0])*60+Integer.parseInt(parts[1]);}
    private static String clock(int value){int m=((value%1440)+1440)%1440;return String.format("%02d:%02d",m/60,m%60);}
    private static String duration(int minutes){if(minutes<=0)return "不足 1 分钟";int h=minutes/60,m=minutes%60;return (h>0?h+" 小时 ":"")+(m>0?m+" 分钟":"");}
    private static String modeLabel(String mode){if(mode==null)return "通勤";return switch(mode){case "WALKING"->"步行";case "CYCLING"->"骑行";case "TRANSIT"->"公共交通";case "DRIVING"->"驾车";default->"通勤";};}
    private static String trim(String value){return value==null||value.isBlank()?null:value.strip();}
    private static Integer number(JsonNode node){return node.isNumber()?node.asInt():null;}
    private static BigDecimal decimal(JsonNode node,String field){var value=node.path(field);return value.isNumber()?value.decimalValue():null;}
    private static BigDecimal firstDecimal(JsonNode node,String field,BigDecimal fallback){var value=decimal(node,field);return value==null?fallback:value;}
    private static BigDecimal add(BigDecimal left,BigDecimal right){if(left==null)return right;if(right==null)return left;return left.add(right);}
    private static String text(JsonNode node,String field){var value=node.path(field);return value.isTextual()&&!value.asText().isBlank()?value.asText():null;}
    private static Integer integer(JsonNode node,String field){var value=node.path(field);return value.isNumber()?value.asInt():null;}
}