package com.rentpilot.listing;

import java.math.BigDecimal;
import java.util.List;

public record Listing(String id, String name, String district, String town, String sector, String community,
                      String address, BigDecimal longitude, BigDecimal latitude, String rentalType, String layout,
                      int bedrooms, BigDecimal rent, BigDecimal area, String floor, String orientation,
                      String decoration, String nearestMetro, String metroLinesRaw, List<String> metroLines,
                      Integer metroDistanceMeters, Integer metroWalkMinutes, Amenities amenities,
                      BigDecimal deposit, String paymentMethod, BigDecimal propertyFee, String propertyFeePeriod,
                      String availability, String description, String dataNature, String locationStatus,
                      String dataKind, String coordinateSystem, String coordinateScope, String datasetId,
                      String sourceSha256, String notice) {
    public record Amenities(TriState privateBathroom, TriState privateKitchen, TriState gas, TriState balcony,
                            TriState airConditioning, TriState refrigerator, TriState washingMachine,
                            TriState waterHeater, TriState elevator, TriState furnished, TriState petsAllowed,
                            TriState cookingAllowed) {}
}
