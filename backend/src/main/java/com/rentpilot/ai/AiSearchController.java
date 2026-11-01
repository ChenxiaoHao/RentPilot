package com.rentpilot.ai;
import org.springframework.web.bind.annotation.*;
@RestController
class AiSearchController {
 private final AiSearchService service;
 AiSearchController(AiSearchService service){this.service=service;}
 @PostMapping("/api/ai/search")
 AiSearchService.Response search(@RequestBody AiSearchService.Request request){return service.search(request);}
 @PostMapping("/api/ai/explain")
 AiSearchService.ExplainResponse explain(@RequestBody com.fasterxml.jackson.databind.JsonNode body){if(!body.isObject()||body.size()!=2||!body.path("searchToken").isTextual()||!body.path("listingId").isTextual())throw com.rentpilot.api.ApiException.invalid("request","解读请求无效");return service.explain(body.get("searchToken").asText(),body.get("listingId").asText());}
}
