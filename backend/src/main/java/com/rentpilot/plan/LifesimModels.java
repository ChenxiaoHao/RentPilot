package com.rentpilot.plan;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class LifesimModels {
    private LifesimModels() {}

    public record LifesimInput(
            @Min(0) @Max(300) Integer commuteMinutes,
            @Pattern(regexp="([01]\\d|2[0-3]):[0-5]\\d") String workStart,
            @Pattern(regexp="([01]\\d|2[0-3]):[0-5]\\d") String sleepTime,
            @Min(0) @Max(7) Integer overtimeDays,
            @Pattern(regexp="([01]\\d|2[0-3]):[0-5]\\d") String overtimeEnd,
            @Pattern(regexp="UNDER_1000|1000_2000|2000_3000|OVER_3000") String dailySpendBand,
            @DecimalMin("0") @DecimalMax("100000") BigDecimal fixedCost,
            @Min(0) @Max(7) Integer cookingDays) {}

    public record LifesimMoment(String key,String label,String time,String source,String detail) {}

    public record LifesimCosts(BigDecimal monthlyRent,BigDecimal deposit,BigDecimal propertyFee,String propertyFeePeriod,
                               BigDecimal knownInitialPayment,BigDecimal fixedCost,String dailySpendBand,String dailySpendLabel,
                               BigDecimal monthlyKnown,BigDecimal monthlyLow,BigDecimal monthlyHigh) {}

    public record LifesimView(String planId,String versionId,int versionNo,String title,String capturedAt,
                              String status,String mode,Integer commuteMinutes,
                              LifesimInput answers,Map<String,Object> assumptions,
                              List<LifesimMoment> timeline,LifesimCosts costs,
                              Map<String,String> sources,List<String> limitations,String updatedAt) {}
}