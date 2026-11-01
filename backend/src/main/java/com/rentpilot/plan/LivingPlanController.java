package com.rentpilot.plan;

import com.rentpilot.api.ApiException;
import com.rentpilot.member.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.rentpilot.plan.PlanModels.*;

@RestController
public class LivingPlanController {
    private final AuthService auth;
    private final LivingPlanService plans;
    public LivingPlanController(AuthService auth,LivingPlanService plans){this.auth=auth;this.plans=plans;}

    @GetMapping("/api/me/plans")
    public List<PlanSummary> list(@RequestHeader(value="Authorization",required=false)String token){
        return plans.list(auth.requireMember(token));
    }

    @GetMapping("/api/me/plans/compare")
    public PlanComparison compare(@RequestHeader(value="Authorization",required=false)String token,
                                  @RequestParam String left,@RequestParam String right){
        requireId(left);requireId(right);
        return plans.compare(auth.requireMember(token),left,right);
    }

    @GetMapping("/api/me/plans/{id}")
    public PlanDetail detail(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id){
        requireId(id);return plans.detail(auth.requireMember(token),id);
    }

    @GetMapping("/api/me/plans/{id}/versions")
    public List<VersionSummary> versions(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id){
        requireId(id);return plans.versions(auth.requireMember(token),id);
    }

    @PostMapping("/api/me/plans") @ResponseStatus(HttpStatus.CREATED)
    public PlanDetail save(@RequestHeader(value="Authorization",required=false)String token,@Valid @RequestBody SaveInput input){
        return plans.save(auth.requireMember(token),input);
    }

    @PutMapping("/api/me/plans/{id}/active")
    public PlanDetail activate(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id,@Valid @RequestBody ActivateInput input){
        requireId(id);return plans.activate(auth.requireMember(token),id,input.versionId());
    }

    @DeleteMapping("/api/me/plans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id){
        requireId(id);plans.delete(auth.requireMember(token),id);
    }

    private void requireId(String id){
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,40}"))throw ApiException.invalid("id","方案编号无效");
    }
}