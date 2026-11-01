package com.rentpilot.listing;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class ListingController {
    private final ListingRepository listings;
    public ListingController(ListingRepository listings) { this.listings = listings; }

    @GetMapping("/api/listings")
    public ListingRepository.ListingPage list(@RequestParam MultiValueMap<String, String> parameters) {
        return listings.find(ListingQuery.parse(parameters));
    }

    @GetMapping("/api/listings/map")
    public ListingRepository.MapPage map(@RequestParam MultiValueMap<String, String> parameters) {
        return listings.mapFind(MapListingQuery.parse(parameters));
    }

    @GetMapping("/api/listings/{id}")
    public Listing detail(@PathVariable String id) { return listings.detail(id); }

    @GetMapping("/api/listing-filters")
    public ListingRepository.FilterOptions filters() { return listings.filters(); }
}
