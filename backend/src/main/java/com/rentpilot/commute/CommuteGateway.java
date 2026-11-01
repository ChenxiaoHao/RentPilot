package com.rentpilot.commute;
public interface CommuteGateway {
 java.util.List<Workplace> search(String query, String district);
 java.util.Optional<Workplace> detail(String id);
 java.util.Optional<RouteEstimate> route(double originLongitude, double originLatitude, Workplace destination, TravelMode mode);
}
