package com.rentpilot.dataset;

import java.time.OffsetDateTime;

public record DatasetInfo(String id, String sourceName, String sha256, int rowCount,
                          OffsetDateTime importedAt, int missingCoordinates, int missingMetroWalk,
                          String dataKind, String coordinateSystem, String coordinateScope, String notice) {}
