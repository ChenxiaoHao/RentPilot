package com.rentpilot.plan;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class PlanModels {
    private PlanModels() {}

    public record SaveInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String searchToken,
                            @Size(max=60) String title,
                            @Size(max=6) List<String> listingIds,
                            @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String planId) {}

    public record ActivateInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String versionId) {}

    public record PlanSummary(String id,String versionId,int versionNo,String title,
                              String primaryListingId,String primaryHomeName,String primaryDistrict,String primaryTown,
                              BigDecimal primaryRent,Integer primaryCommuteMinutes,int homeCount,String coverage,
                              String capturedAt,String updatedAt) {}

    public record VersionSummary(String id,int versionNo,int homeCount,String coverage,String capturedAt) {}

    public record PlanDetail(PlanSummary plan,List<VersionSummary> versions,JsonNode snapshot) {}

    public record CompareSide(String planId,String versionId,int versionNo,String title,String capturedAt,
                              String primaryListingId,String primaryHomeName,String primaryDistrict,String primaryTown,
                              BigDecimal rent,BigDecimal deposit,String paymentMethod,
                              BigDecimal propertyFee,String propertyFeePeriod,BigDecimal knownInitialPayment,
                              String commuteMode,Integer commuteMinutes,String commuteCheckedAt,
                              String coverage,int homeCount,boolean partialCoverage) {}

    public record PlanComparison(CompareSide left,CompareSide right,
                                 BigDecimal rentDifference,Integer commuteMinutesDifference,
                                 BigDecimal knownInitialPaymentDifference,List<String> notes) {}
}