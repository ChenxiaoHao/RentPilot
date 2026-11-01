package com.rentpilot.dataset;

import java.util.List;
import java.util.Map;

public record ParsedDataset(String sha256, List<Map<String, Object>> rows) {}
