package com.rentpilot.member;

import com.rentpilot.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static com.rentpilot.member.MemberModels.*;

@RestController
public class MemberController {
    private final AuthService auth;private final MemberService members;
    public MemberController(AuthService auth,MemberService members){this.auth=auth;this.members=members;}
    @PostMapping("/api/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public Session register(@Valid @RequestBody Credentials input){return auth.register(input);}
    @PostMapping("/api/auth/login") public Session login(@Valid @RequestBody Credentials input){return auth.login(input);}
    @PostMapping("/api/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value="Authorization",required=false)String token){auth.logout(token);}
    @GetMapping("/api/me") public Profile me(@RequestHeader(value="Authorization",required=false)String token){return auth.profile(auth.requireMember(token));}
    @PatchMapping("/api/me") public Profile rename(@RequestHeader(value="Authorization",required=false)String token,@Valid @RequestBody Nickname input){return auth.rename(auth.requireMember(token),input.nickname());}
    @GetMapping({"/api/me/favorites","/api/me/history"})
    public Page<SavedListing> saved(@RequestHeader(value="Authorization",required=false)String token,@RequestParam Map<String,String> params,jakarta.servlet.http.HttpServletRequest request){
        int[] paging=paging(params);return members.saved(auth.requireMember(token),request.getRequestURI().endsWith("favorites")?"favorites":"history",paging[0],paging[1]);
    }
    @PutMapping({"/api/me/favorites/{id}","/api/me/history/{id}"}) @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id,jakarta.servlet.http.HttpServletRequest request){members.save(auth.requireMember(token),request.getRequestURI().contains("/favorites/")?"favorites":"history",id);}
    @DeleteMapping("/api/me/favorites/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id){members.unsave(auth.requireMember(token),id);}
    @DeleteMapping("/api/me/history") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(@RequestHeader(value="Authorization",required=false)String token){members.clearHistory(auth.requireMember(token));}
    @GetMapping("/api/me/favorite-ids") public java.util.List<String> favoriteIds(@RequestHeader(value="Authorization",required=false)String token){return members.favoriteIds(auth.requireMember(token));}
    @GetMapping("/api/me/viewings") public Page<Viewing> plans(@RequestHeader(value="Authorization",required=false)String token,@RequestParam Map<String,String> params){
        String status=params.get("status");if(status!=null&&!java.util.Set.of("PLANNED","CANCELLED","COMPLETED").contains(status))throw ApiException.invalid("status","不支持的计划状态");
        var rest=new java.util.HashMap<>(params);rest.remove("status");int[] p=paging(rest);return members.viewings(auth.requireMember(token),p[0],p[1],status);
    }
    @PostMapping("/api/me/viewings") public Viewing plan(@RequestHeader(value="Authorization",required=false)String token,@Valid @RequestBody ViewingInput input){return members.plan(auth.requireMember(token),input);}
    @PutMapping("/api/me/viewings/{id}") public Viewing edit(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id,@Valid @RequestBody ViewingInput input){return members.edit(auth.requireMember(token),id,input);}
    @PatchMapping("/api/me/viewings/{id}/status") public Viewing status(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String id,@Valid @RequestBody StatusInput input){return members.status(auth.requireMember(token),id,input.status());}
    private int[] paging(Map<String,String> params){
        for(String key:params.keySet())if(!key.equals("page")&&!key.equals("size"))throw ApiException.invalid(key,"不支持此条件");
        try{int page=Integer.parseInt(params.getOrDefault("page","0")),size=Integer.parseInt(params.getOrDefault("size","20"));if(page<0||size<1||size>50)throw new NumberFormatException();return new int[]{page,size};}
        catch(NumberFormatException e){throw ApiException.invalid("page/size","页码从0起，每页1至50条");}
    }
}
