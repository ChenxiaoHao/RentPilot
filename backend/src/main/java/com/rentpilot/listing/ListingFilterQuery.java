package com.rentpilot.listing;

import java.math.BigDecimal;

interface ListingFilterQuery {
    String q();

    String district();

    String town();

    String rentalType();

    Integer bedrooms();

    BigDecimal minRent();

    BigDecimal maxRent();

    BigDecimal minArea();

    BigDecimal maxArea();

    Integer maxMetroWalkMinutes();

    String metroLine();
    String nearestMetro();

    String elevator();

    String petsAllowed();

    String privateBathroom();
}