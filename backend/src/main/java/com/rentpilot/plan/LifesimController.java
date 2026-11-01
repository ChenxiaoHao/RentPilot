package com.rentpilot.plan;

import com.rentpilot.api.ApiException;
import com.rentpilot.member.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.rentpilot.plan.LifesimModels.*;

@RestController
public class LifesimController {
    private final AuthService auth;
    private final LifesimService lifesim;
    public LifesimController(AuthService auth,LifesimService lifesim){this.auth=auth;this.lifesim=lifesim;}

    @GetMapping("/api/me/plans/{id}/lifesim")
    public LifesimView get(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id){
        requireId(id);
        return lifesim.get(auth.requireMember(token),id);
    }

    @PutMapping("/api/me/plans/{id}/lifesim")
    public LifesimView save(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id,
                            @Valid @RequestBody LifesimInput input){
        requireId(id);
        return lifesim.save(auth.requireMember(token),id,input);
    }

    private void requireId(String id){
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,40}"))throw ApiException.invalid("id","方案编号无效");
    }
}