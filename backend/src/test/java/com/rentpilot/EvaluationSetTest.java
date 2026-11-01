package com.rentpilot;

import com.rentpilot.listing.ListingQuery;
import com.rentpilot.listing.ListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:evaluation-set;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
class EvaluationSetTest {
    @Autowired ListingRepository repository;

    private record Case(String id,String description,ListingQuery query,BigDecimal maxRent,Integer bedrooms,String district,String rentalType) {}

    private ListingQuery query(String district,String rentalType,Integer bedrooms,BigDecimal maxRent,String maxArea,String minArea,String elevator,Integer maxWalk){
        return new ListingQuery(0,100,null,district,null,rentalType,bedrooms,null,maxRent,
                minArea==null?null:new BigDecimal(minArea),maxArea==null?null:new BigDecimal(maxArea),maxWalk,null,null,
                elevator,null,null,"id",null);
    }

    @Test void runsTenFixedCasesAndReportsViolations() {
        var cases=new ArrayList<Case>();
        cases.add(new Case("C01","预算 3000、1 居、全城",query(null,null,1,new BigDecimal("3000"),null,null,null,null),new BigDecimal("3000"),1,null,null));
        cases.add(new Case("C02","预算 2000、1 居、全城（预期无匹配）",query(null,null,1,new BigDecimal("2000"),null,null,null,null),new BigDecimal("2000"),1,null,null));
        cases.add(new Case("C03","闵行区、预算 4000、1 居",query("闵行区",null,1,new BigDecimal("4000"),null,null,null,null),new BigDecimal("4000"),1,"闵行区",null));
        cases.add(new Case("C04","浦东新区、预算 5000、2 居",query("浦东新区",null,2,new BigDecimal("5000"),null,null,null,null),new BigDecimal("5000"),2,"浦东新区",null));
        cases.add(new Case("C05","整租、预算 3500、1 居",query(null,"ENTIRE",1,new BigDecimal("3500"),null,null,null,null),new BigDecimal("3500"),1,null,"ENTIRE"));
        cases.add(new Case("C06","合租、预算 3000",query(null,"SHARED",null,new BigDecimal("3000"),null,null,null,null),new BigDecimal("3000"),null,null,"SHARED"));
        cases.add(new Case("C07","面积不少于 30㎡、预算 4500",query(null,null,null,new BigDecimal("4500"),null,"30",null,null),new BigDecimal("4500"),null,null,null));
        cases.add(new Case("C08","电梯、预算 5000",query(null,null,null,new BigDecimal("5000"),null,null,"YES",null),new BigDecimal("5000"),null,null,null));
        cases.add(new Case("C09","地铁步行不超过 10 分钟、预算 4500",query(null,null,null,new BigDecimal("4500"),null,null,null,10),new BigDecimal("4500"),null,null,null));
        cases.add(new Case("C10","预算 100000、全量返回",query(null,null,null,new BigDecimal("100000"),null,null,null,null),new BigDecimal("100000"),null,null,null));

        int totalViolations=0;
        var times=new ArrayList<Long>();
        System.out.println("EVAL|id|description|total|violations|ms");
        for(var c:cases){
            long start=System.nanoTime();
            var page=repository.find(c.query());
            long ms=(System.nanoTime()-start)/1_000_000;
            int violations=0;
            for(var home:page.items()){
                if(c.maxRent()!=null&&home.rent()!=null&&home.rent().compareTo(c.maxRent())>0)violations++;
                if(c.bedrooms()!=null&&home.bedrooms()!=c.bedrooms())violations++;
                if(c.district()!=null&&!c.district().equals(home.district()))violations++;
                if(c.rentalType()!=null&&!c.rentalType().equals(home.rentalType()))violations++;
            }
            totalViolations+=violations;
            times.add(ms);
            System.out.println("EVAL|"+c.id()+"|"+c.description()+"|"+page.total()+"|"+violations+"|"+ms);
        }
        times.sort(Long::compareTo);
        long p95=times.get((int)Math.ceil(times.size()*0.95)-1);
        System.out.println("EVAL|SUMMARY|total_violations="+totalViolations+"|p95_ms="+p95);
        assertThat(totalViolations).isZero();
    }
}