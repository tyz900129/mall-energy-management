package com.wanyuecity.energy.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.ApiResponse;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.service.dto.PlatformAccountView;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import com.wanyuecity.energy.stalldevice.mapper.StallDeviceMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 源平台商户账户视图；仅商户代码为本地维护字段。 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final PlatformAccountCurrentMapper accountMapper;
    private final PlatformAccountDeviceCurrentMapper relationMapper;
    private final StallDeviceMapper stallDeviceMapper;
    private final StallMapper stallMapper;
    private final JdbcTemplate jdbc;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','APPLICANT','DEVICE_VERIFIER','PLATFORM_OPERATOR','AUDITOR','VIEWER')")
    public ApiResponse<Page<PlatformAccountView>> list(@RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="false") boolean showDisabled,
            @RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) {
        QueryWrapper<PlatformAccountCurrent> q=new QueryWrapper<>();
        if(keyword!=null&&!keyword.isBlank()){String k=keyword.trim();q.and(w->w.like("account_number",k).or().like("account_name",k).or().like("user_name",k).or().like("mobile_phone",k).or().like("merchant_code",k).or().apply("EXISTS (SELECT 1 FROM platform_account_local_profile p WHERE p.account_number=platform_account_current.account_number AND p.merchant_code LIKE CONCAT('%',{0},'%'))",k));}
        if(!showDisabled)q.and(w->w.isNull("platform_status").or().notIn("platform_status","0","DISABLED","INACTIVE"));
        q.orderByAsc("account_name");
        Page<PlatformAccountCurrent> source=accountMapper.selectPage(Page.of(pageNum,pageSize),q);
        Page<PlatformAccountView> out=Page.of(pageNum,pageSize,source.getTotal());
        out.setRecords(source.getRecords().stream().map(this::view).toList());
        return ApiResponse.ok(out);
    }

    @PutMapping("/{accountNumber}/merchant-code")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<Void> saveMerchantCode(@PathVariable String accountNumber,@RequestBody MerchantCodeRequest req,Authentication auth){
        String code=req.getMerchantCode()==null?null:req.getMerchantCode().trim();
        if(code!=null&&code.isEmpty())code=null;
        if(code!=null&&!code.matches("\\d{6}"))throw BizException.badRequest("商户代码必须是连续六位数字");
        if(accountMapper.selectCount(new QueryWrapper<PlatformAccountCurrent>().eq("account_number",accountNumber))==0)throw BizException.notFound("商户账户不存在");
        jdbc.update("INSERT INTO platform_account_local_profile(account_number,merchant_code,updated_by) VALUES(?,?,?) ON DUPLICATE KEY UPDATE merchant_code=VALUES(merchant_code),updated_by=VALUES(updated_by)",accountNumber,code,auth==null?null:auth.getName());
        return ApiResponse.ok(null);
    }

    private PlatformAccountView view(PlatformAccountCurrent a){PlatformAccountView v=new PlatformAccountView();v.setPlatformAccountId(a.getPlatformAccountId());v.setAccountNumber(a.getAccountNumber());v.setAccountName(a.getAccountName());v.setUserName(a.getUserName());v.setMobilePhone(a.getMobilePhone());v.setBalance(a.getBalance());v.setArrears(a.getArrears());v.setPlatformStatus(a.getPlatformStatus());v.setLastSyncedAt(a.getLastSyncedAt());String local=jdbc.query("SELECT merchant_code FROM platform_account_local_profile WHERE account_number=?",rs->rs.next()?rs.getString(1):null,a.getAccountNumber());v.setMerchantCode(local!=null?local:a.getMerchantCode());v.setMerchantCodeSource(local!=null?"LOCAL":"AUTO");v.setStallCodes(stallsFor(a.getAccountNumber()));return v;}

    private List<String> stallsFor(String accountNumber){Set<String> codes=new LinkedHashSet<>();for(PlatformAccountDeviceCurrent relation:relationMapper.selectList(new QueryWrapper<PlatformAccountDeviceCurrent>().eq("account_number",accountNumber))){StallDevice binding=stallDeviceMapper.findActiveByDeviceId(relation.getPlatformDeviceId());if(binding!=null){Stall stall=stallMapper.selectById(binding.getStallId());if(stall!=null&&stall.getStallCode()!=null)codes.add(stall.getStallCode());}}return List.copyOf(codes);}

    @Data public static class MerchantCodeRequest { private String merchantCode; }
}
