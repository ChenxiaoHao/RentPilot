package com.rentpilot.api;

import com.rentpilot.dataset.DatasetInfo;
import com.rentpilot.dataset.DatasetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DatasetService datasets;

    public HealthController(DatasetService datasets) { this.datasets = datasets; }

    @GetMapping("/api/health")
    public Health health() { return new Health("UP", datasets.info()); }

    public record Health(String status, DatasetInfo dataset) {}
}
