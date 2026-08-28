package com.wanyuecity.energy.stall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.merchant.entity.Merchant;
import com.wanyuecity.energy.merchant.mapper.MerchantMapper;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stall.service.dto.StallUpsertRequest;
import com.wanyuecity.energy.stall.service.dto.StallView;
import com.wanyuecity.energy.system.service.BusinessDictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StallService {

    private static final List<String> ALLOWED_STATUS = List.of(
            "AVAILABLE", "OCCUPIED", "MAINTENANCE", "DECOMMISSIONED");

    private final StallMapper stallMapper;
    private final MerchantMapper merchantMapper;
    private final BusinessDictionaryService dictionaryService;
    private final StallNameResolver stallNameResolver;
    private final JdbcTemplate jdbc;

    public Page<StallView> list(String keyword, String status,
                                 String floorCode, String businessType,
                                 int pageNum, int pageSize) {
        LambdaQueryWrapper<Stall> q = new LambdaQueryWrapper<Stall>().orderByDesc(Stall::getId);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(Stall::getStallCode, k)
                    .or().like(Stall::getStallName, k)
                    .or().like(Stall::getFloorCode, k)
                    .or().like(Stall::getAreaName, k));
        }
        if (status != null && !status.isBlank()) {
            q.eq(Stall::getStatus, status);
        }
        if (floorCode != null && !floorCode.isBlank()) {
            q.eq(Stall::getFloorCode, floorCode.trim());
        }
        if (businessType != null && !businessType.isBlank()) {
            q.eq(Stall::getBusinessType, businessType.trim());
        }
        Page<Stall> p = stallMapper.selectPage(Page.of(pageNum, pageSize), q);
        Page<StallView> out = Page.of(pageNum, pageSize, p.getTotal());
        out.setRecords(p.getRecords().stream().map(this::toView).toList());
        return out;
    }

    public StallView get(Long id) {
        Stall s = stallMapper.selectById(id);
        if (s == null) throw BizException.notFound("Stall not found");
        return toView(s);
    }

    @Transactional
    public StallView create(StallUpsertRequest req) {
        validateStatus(req.getStatus());
        Long dup = stallMapper.selectCount(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getStallCode, req.getStallCode()));
        if (dup > 0) {
            throw BizException.badRequest("stall_code already exists");
        }
        if (req.getCurrentMerchantId() != null) {
            requireActiveMerchant(req.getCurrentMerchantId());
        }
        Stall s = new Stall();
        applyRequest(s, req);
        if (s.getStatus() == null) s.setStatus("AVAILABLE");
        stallMapper.insert(s);
        return toView(s);
    }

    @Transactional
    public StallView update(Long id, StallUpsertRequest req) {
        Stall s = stallMapper.selectById(id);
        if (s == null) throw BizException.notFound("Stall not found");
        validateStatus(req.getStatus());

        if (!s.getStallCode().equals(req.getStallCode())) {
            Long dup = stallMapper.selectCount(new LambdaQueryWrapper<Stall>()
                    .eq(Stall::getStallCode, req.getStallCode())
                    .ne(Stall::getId, id));
            if (dup > 0) {
                throw BizException.badRequest("stall_code already exists");
            }
        }
        if (req.getCurrentMerchantId() != null) {
            requireActiveMerchant(req.getCurrentMerchantId());
        }
        applyRequest(s, req);
        stallMapper.updateById(s);
        return toView(s);
    }

    /**
     * 软删铺位：把状态置为 DECOMMISSIONED（spec §3 DELETE /stalls/{id}）。
     * 若仍绑定有效设备或有效商户，先解绑再软删。
     */
    @Transactional
    public void softDelete(Long id) {
        Stall s = stallMapper.selectById(id);
        if (s == null) throw BizException.notFound("Stall not found");
        s.setStatus("DECOMMISSIONED");
        s.setCurrentMerchantId(null);
        stallMapper.updateById(s);
    }

    /** 超级管理员永久删除铺位；历史流程必须先由流程中心永久删除，防止产生孤立数据。 */
    @Transactional
    public void permanentDelete(Long id) {
        Stall s = stallMapper.selectById(id);
        if (s == null) throw BizException.notFound("铺位不存在");
        Integer workflowCount = jdbc.queryForObject("SELECT COUNT(*) FROM workflow WHERE stall_id=?", Integer.class, id);
        if (workflowCount != null && workflowCount > 0)
            throw BizException.badRequest("该铺位仍关联 " + workflowCount + " 条流程，请先在流程中心永久删除关联流程");
        jdbc.update("DELETE FROM stall_device WHERE stall_id=?", id);
        jdbc.update("DELETE FROM data_exception WHERE stall_id=?", id);
        stallMapper.deleteById(id);
    }

    /** Convenience for the workflow layer: bind / unbind a merchant on a stall. */
    @Transactional
    public void setCurrentMerchant(Long stallId, Long merchantId) {
        Stall s = stallMapper.selectById(stallId);
        if (s == null) throw BizException.notFound("Stall not found");
        if ("DECOMMISSIONED".equals(s.getStatus())) {
            throw BizException.badRequest("stall is decommissioned");
        }
        s.setCurrentMerchantId(merchantId);
        // Auto-derive status: if a merchant is set, mark OCCUPIED; if null, AVAILABLE.
        s.setStatus(merchantId == null ? "AVAILABLE" : "OCCUPIED");
        stallMapper.updateById(s);
    }

    private void requireActiveMerchant(Long merchantId) {
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) {
            throw BizException.badRequest("merchant not found: id=" + merchantId);
        }
        if (!"ACTIVE".equals(m.getStatus())) {
            throw BizException.badRequest("merchant is not ACTIVE: id=" + merchantId);
        }
    }

    private void applyRequest(Stall s, StallUpsertRequest req) {
        requireDictionary("FLOOR", req.getFloorCode());
        requireDictionaryIfPresent("AREA", req.getAreaName());
        requireDictionary("BUSINESS_TYPE", req.getBusinessType());
        requireDictionaryIfPresent("BUSINESS_CATEGORY", req.getBusinessCategory());
        s.setStallCode(req.getStallCode());
        // 名称由设备关联的源平台账户派生，禁止通过管理接口写入。
        if (s.getId() == null) s.setStallName(StallNameResolver.PENDING_NAME);
        s.setFloorCode(req.getFloorCode());
        // 系统只使用一个楼层字段；保留旧列仅为兼容历史接口。
        s.setFloorName(dictionaryService.label("FLOOR", req.getFloorCode()));
        s.setAreaName(req.getAreaName());
        s.setArea(req.getArea());
        s.setBusinessType(req.getBusinessType());
        s.setBusinessCategory(req.getBusinessCategory());
        s.setStatus(req.getStatus());
        s.setCurrentMerchantId(req.getCurrentMerchantId());
        s.setRemark(req.getRemark());
    }
    private void requireDictionary(String type,String value){if(!dictionaryService.isEnabled(type,value))throw BizException.badRequest("请选择有效的基础字典项: "+type);}
    private void requireDictionaryIfPresent(String type,String value){if(value!=null&&!value.isBlank())requireDictionary(type,value);}

    private void validateStatus(String s) {
        if (s == null) return;
        if (!ALLOWED_STATUS.contains(s)) {
            throw BizException.badRequest("status must be one of " + ALLOWED_STATUS);
        }
    }

    private StallView toView(Stall s) {
        StallView v = new StallView();
        v.setId(s.getId());
        v.setStallCode(s.getStallCode());
        v.setStallName(s.getStallName());
        v.setFloorCode(s.getFloorCode());
        v.setFloorName(s.getFloorName());
        v.setAreaName(s.getAreaName());
        v.setArea(s.getArea());
        v.setBusinessType(s.getBusinessType());
        v.setBusinessCategory(s.getBusinessCategory());
        v.setStatus(s.getStatus());
        v.setCurrentMerchantId(s.getCurrentMerchantId());
        v.setRemark(s.getRemark());
        v.setCreatedAt(s.getCreatedAt());
        v.setUpdatedAt(s.getUpdatedAt());
        var account = stallNameResolver.findByStallCode(s.getStallCode());
        if (account != null) {
            v.setAccountNumber(account.getAccountNumber());
            v.setAccountMobilePhone(account.getMobilePhone());
            v.setAccountStatus(account.getPlatformStatus());
            v.setCurrentMerchantName(account.getAccountName());
        }
        if (s.getCurrentMerchantId() != null) {
            Merchant m = merchantMapper.selectById(s.getCurrentMerchantId());
            if (m != null) {
                v.setCurrentMerchantCode(m.getMerchantCode());
                v.setCurrentMerchantName(m.getMerchantName());
            }
        }
        return v;
    }
}
