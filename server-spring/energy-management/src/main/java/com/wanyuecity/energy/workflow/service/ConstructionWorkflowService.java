package com.wanyuecity.energy.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import com.wanyuecity.energy.system.service.SysUserService;
import com.wanyuecity.energy.workflow.entity.ConstructionAcceptance;
import com.wanyuecity.energy.workflow.entity.ConstructionInspection;
import com.wanyuecity.energy.workflow.entity.ConstructionWorkflowDetail;
import com.wanyuecity.energy.workflow.entity.Workflow;
import com.wanyuecity.energy.workflow.mapper.ConstructionWorkflowDetailMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionAcceptanceMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionInspectionMapper;
import com.wanyuecity.energy.workflow.mapper.WorkflowMapper;
import com.wanyuecity.energy.workflow.service.dto.ConstructionWorkflowRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConstructionWorkflowService {
    public static final String ENTRY = "MERCHANT_CONSTRUCTION_ENTRY";
    public static final String ACCEPTANCE = "MERCHANT_CONSTRUCTION_ACCEPTANCE";
    public static final String EXIT = "MERCHANT_CONSTRUCTION_EXIT";
    public static final Set<String> NEW_TYPES = Set.of(ENTRY, EXIT);
    public static final Set<String> TYPES = Set.of(ENTRY, ACCEPTANCE, EXIT);

    private final WorkflowMapper workflowMapper;
    private final ConstructionWorkflowDetailMapper detailMapper;
    private final StallMapper stallMapper;
    private final SysUserMapper userMapper;
    private final SysUserService userService;
    private final ConstructionInspectionMapper inspectionMapper;
    private final ConstructionAcceptanceMapper acceptanceMapper;

    @Transactional
    public ConstructionWorkflowView create(ConstructionWorkflowRequest req, String username) {
        String type = normalizeType(req.getWorkflowType());
        validatePeriod(req.getConstructionStartTime(), req.getConstructionEndTime());
        Stall stall = requireStallForUpdate(req.getStallId());
        if ("DECOMMISSIONED".equals(stall.getStatus())) throw BizException.badRequest("停用铺位不能发起施工流程");
        long active = workflowMapper.selectCount(new QueryWrapper<Workflow>()
                .eq("stall_id", stall.getId()).in("workflow_type", NEW_TYPES)
                .in("status", List.of("IN_PROGRESS", "PENDING_ACCEPTANCE", "ACCEPTING", "REJECTED")));
        if (active > 0) throw BizException.badRequest("该铺位已有进行中的施工流程，请先处理完成");

        String requestKey = text(req.getRequestKey()) ? req.getRequestKey().trim() : UUID.randomUUID().toString();
        Workflow repeated = workflowMapper.selectOne(new QueryWrapper<Workflow>().eq("request_key", requestKey).last("LIMIT 1"));
        if (repeated != null) {
            if (!TYPES.contains(repeated.getWorkflowType()) || !Objects.equals(repeated.getApplicantName(), username))
                throw BizException.badRequest("流程请求编号已被使用");
            return get(repeated.getId(), username, false);
        }

        SysUser user = text(username) ? userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1")) : null;
        Workflow workflow = new Workflow();
        workflow.setWorkflowNo(nextWorkflowNo());
        workflow.setRequestKey(requestKey);
        workflow.setWorkflowType(type);
        workflow.setMerchantName(req.getMerchantName().trim());
        workflow.setContactName(req.getMerchantPrincipalName().trim());
        workflow.setContactPhone(req.getMerchantPrincipalPhone().trim());
        workflow.setStallId(stall.getId());
        workflow.setRequestedBillingStartTime(req.getConstructionStartTime());
        workflow.setApplicantId(user == null ? null : user.getId());
        workflow.setApplicantName(username);
        workflow.setStatus("IN_PROGRESS");
        workflow.setSubmittedAt(LocalDateTime.now());
        workflow.setRemark(trim(req.getRemark()));
        try {
            workflowMapper.insert(workflow);
        } catch (DuplicateKeyException ex) {
            Workflow existing = workflowMapper.selectOne(new QueryWrapper<Workflow>().eq("request_key", requestKey).last("LIMIT 1"));
            if (existing != null && TYPES.contains(existing.getWorkflowType()) && Objects.equals(existing.getApplicantName(), username))
                return toView(existing);
            throw ex;
        }

        ConstructionWorkflowDetail detail = new ConstructionWorkflowDetail();
        detail.setWorkflowId(workflow.getId());
        detail.setPermitNo("SG-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%04d", workflow.getId()));
        detail.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
        detail.setConstructionStartTime(req.getConstructionStartTime());
        detail.setConstructionEndTime(req.getConstructionEndTime());
        detail.setMerchantPrincipalName(req.getMerchantPrincipalName().trim());
        detail.setMerchantPrincipalPhone(req.getMerchantPrincipalPhone().trim());
        detail.setConstructionCompanyName(req.getConstructionCompanyName().trim());
        detail.setConstructionPrincipalName(req.getConstructionPrincipalName().trim());
        detail.setConstructionPrincipalPhone(req.getConstructionPrincipalPhone().trim());
        fillInternalContacts(detail, req, user);
        detail.setRelatedWorkflowId(findRelatedWorkflowId(stall.getId(), type));
        detail.setStallStatusBefore(stall.getStatus());
        detail.setPreviousCurrentMerchantId(stall.getCurrentMerchantId());
        detail.setStallStatusAfter(ENTRY.equals(type) ? "RENOVATING" : "EXIT_CONSTRUCTION");
        detailMapper.insert(detail);
        stallMapper.update(null, new UpdateWrapper<Stall>().eq("id", stall.getId()).set("status", detail.getStallStatusAfter()));
        return toView(workflow);
    }

    public Page<ConstructionWorkflowView> list(String status, String workflowType, String keyword,
                                                String applicantScope, int pageNum, int pageSize) {
        QueryWrapper<Workflow> query = new QueryWrapper<Workflow>().in("workflow_type", TYPES);
        if (text(status)) query.eq("status", status.trim().toUpperCase());
        if (text(workflowType)) query.eq("workflow_type", normalizeType(workflowType));
        if (text(applicantScope)) query.eq("applicant_name", applicantScope);
        if (text(keyword)) {
            String value = keyword.trim();
            List<Long> stallIds = stallMapper.selectList(new QueryWrapper<Stall>().like("stall_code", value))
                    .stream().map(Stall::getId).toList();
            query.and(q -> {
                q.like("workflow_no", value).or().like("merchant_name", value).or().like("applicant_name", value);
                if (!stallIds.isEmpty()) q.or().in("stall_id", stallIds);
            });
        }
        query.orderByDesc("created_at");
        Page<Workflow> page = workflowMapper.selectPage(Page.of(Math.max(1, pageNum), Math.min(Math.max(1, pageSize), 100)), query);
        Page<ConstructionWorkflowView> out = Page.of(page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(page.getRecords().stream().map(this::toView).toList());
        return out;
    }

    public ConstructionWorkflowView get(Long id, String username, boolean ownOnly) {
        Workflow workflow = require(id);
        requireOwner(workflow, username, ownOnly);
        return toView(workflow);
    }

    @Transactional
    public ConstructionWorkflowView submit(Long id, String username, boolean ownOnly) {
        Workflow workflow = require(id);
        requireOwner(workflow, username, ownOnly);
        if (!"DRAFT".equals(workflow.getStatus())) throw BizException.badRequest("该施工流程已提交");
        requireStallForUpdate(workflow.getStallId());
        long active = workflowMapper.selectCount(new QueryWrapper<Workflow>()
                .eq("stall_id", workflow.getStallId()).in("workflow_type", TYPES)
                .in("status", List.of("IN_PROGRESS", "PENDING_ACCEPTANCE", "ACCEPTING", "REJECTED")).ne("id", workflow.getId()));
        if (active > 0) throw BizException.badRequest("该铺位已有待处理的施工流程，请先处理完成");
        workflow.setStatus("IN_PROGRESS");
        workflow.setSubmittedAt(LocalDateTime.now());
        workflowMapper.updateById(workflow);
        return toView(workflow);
    }

    @Transactional
    public ConstructionWorkflowView cancel(Long id, String username, boolean ownOnly) {
        Workflow workflow = require(id);
        requireOwner(workflow, username, ownOnly);
        if (!Set.of("DRAFT", "IN_PROGRESS", "REJECTED").contains(workflow.getStatus())) throw BizException.badRequest("当前状态不能取消");
        ConstructionWorkflowDetail detail = requireDetail(id);
        stallMapper.update(null, new UpdateWrapper<Stall>().eq("id", workflow.getStallId())
                .set("status", detail.getStallStatusBefore()).set("current_merchant_id", detail.getPreviousCurrentMerchantId()));
        workflow.setStatus("CANCELLED");
        workflowMapper.updateById(workflow);
        return toView(workflow);
    }

    @Transactional
    public ConstructionWorkflowView complete(Long id, String operator) {
        Workflow workflow = require(id);
        if (!ACCEPTANCE.equals(workflow.getWorkflowType()) || !"SUBMITTED".equals(workflow.getStatus()))
            throw BizException.badRequest("新施工流程必须通过验收结果完成并归档");
        Stall stall = requireStall(workflow.getStallId());
        if ("DECOMMISSIONED".equals(stall.getStatus())) throw BizException.badRequest("停用铺位不能完成施工流程");
        int claimed = workflowMapper.update(null, new UpdateWrapper<Workflow>().eq("id", workflow.getId())
                .eq("status", "SUBMITTED").set("status", "PROCESSING"));
        if (claimed != 1) throw BizException.badRequest("流程状态已发生变化，请刷新后重试");

        ConstructionWorkflowDetail detail = requireDetail(workflow.getId());
        String targetStatus = targetStallStatus(workflow.getWorkflowType());
        detail.setStallStatusBefore(stall.getStatus());
        detail.setStallStatusAfter(targetStatus);
        detail.setPreviousCurrentMerchantId(stall.getCurrentMerchantId());
        detail.setCompletedBy(operator);
        detail.setCompletedAt(LocalDateTime.now());
        detailMapper.updateById(detail);

        UpdateWrapper<Stall> stallUpdate = new UpdateWrapper<Stall>().eq("id", stall.getId()).set("status", targetStatus);
        if (EXIT.equals(workflow.getWorkflowType())) stallUpdate.set("current_merchant_id", null);
        stallMapper.update(null, stallUpdate);

        workflow.setStatus("COMPLETED");
        workflow.setCompletedAt(detail.getCompletedAt());
        workflowMapper.updateById(workflow);
        return toView(workflow);
    }

    @Transactional
    public ConstructionWorkflowView reset(Long id) {
        Workflow workflow = require(id);
        ConstructionWorkflowDetail detail = requireDetail(id);
        if (Set.of("COMPLETED", "ARCHIVED").contains(workflow.getStatus())) {
            long later = detailMapper.selectCount(new QueryWrapper<ConstructionWorkflowDetail>()
                    .gt("completed_at", detail.getCompletedAt()).isNotNull("completed_at")
                    .inSql("workflow_id", "SELECT id FROM workflow WHERE stall_id=" + workflow.getStallId()));
            if (later > 0) throw BizException.badRequest("该铺位存在后续已完成施工流程，不能直接复位本流程");
            Stall stall = requireStall(workflow.getStallId());
            stallMapper.update(null, new UpdateWrapper<Stall>().eq("id", stall.getId())
                    .set("status", detail.getStallStatusBefore())
                    .set("current_merchant_id", detail.getPreviousCurrentMerchantId()));
        }
        workflowMapper.update(null, new UpdateWrapper<Workflow>().eq("id", id)
                .set("status", "IN_PROGRESS").set("completed_at", null));
        detailMapper.update(null, new UpdateWrapper<ConstructionWorkflowDetail>().eq("id", detail.getId())
                .set("completed_by", null).set("completed_at", null)
                .set("acceptance_requested_at", null).set("archived_at", null));
        inspectionMapper.delete(new QueryWrapper<ConstructionInspection>().eq("workflow_id", id));
        acceptanceMapper.delete(new QueryWrapper<ConstructionAcceptance>().eq("workflow_id", id));
        stallMapper.update(null, new UpdateWrapper<Stall>().eq("id", workflow.getStallId())
                .set("status", ENTRY.equals(workflow.getWorkflowType()) ? "RENOVATING" : "EXIT_CONSTRUCTION"));
        return toView(require(id));
    }

    @Transactional
    public void permanentDelete(Long id) {
        Workflow workflow = require(id);
        if (Set.of("COMPLETED", "ARCHIVED").contains(workflow.getStatus())) throw BizException.badRequest("已归档流程请先复位，再执行删除");
        workflowMapper.deleteById(id);
    }

    private Workflow require(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null || !TYPES.contains(workflow.getWorkflowType())) throw BizException.notFound("施工流程不存在");
        return workflow;
    }

    private ConstructionWorkflowDetail requireDetail(Long workflowId) {
        ConstructionWorkflowDetail detail = detailMapper.selectOne(new QueryWrapper<ConstructionWorkflowDetail>()
                .eq("workflow_id", workflowId).last("LIMIT 1"));
        if (detail == null) throw BizException.notFound("施工流程明细不存在");
        return detail;
    }

    private Stall requireStall(Long stallId) {
        Stall stall = stallMapper.selectById(stallId);
        if (stall == null) throw BizException.notFound("铺位不存在");
        return stall;
    }

    private Stall requireStallForUpdate(Long stallId) {
        Stall stall = stallMapper.selectOne(new QueryWrapper<Stall>().eq("id", stallId).last("FOR UPDATE"));
        if (stall == null) throw BizException.notFound("铺位不存在");
        return stall;
    }

    private void requireOwner(Workflow workflow, String username, boolean ownOnly) {
        if (ownOnly && !Objects.equals(workflow.getApplicantName(), username)) throw BizException.forbidden("申请人只能查看和操作本人流程");
    }

    private ConstructionWorkflowView toView(Workflow workflow) {
        ConstructionWorkflowDetail detail = requireDetail(workflow.getId());
        Stall stall = stallMapper.selectById(workflow.getStallId());
        ConstructionWorkflowView view = new ConstructionWorkflowView();
        view.setId(workflow.getId()); view.setWorkflowNo(workflow.getWorkflowNo()); view.setWorkflowType(workflow.getWorkflowType());
        view.setPermitNo(detail.getPermitNo()); view.setPublicToken(detail.getPublicToken());
        view.setMerchantName(workflow.getMerchantName()); view.setStallId(workflow.getStallId());
        if (stall != null) { view.setStallCode(stall.getStallCode()); view.setStallName(stall.getStallName()); view.setFloorCode(stall.getFloorCode()); view.setFloorName(stall.getFloorName()); view.setCurrentStallStatus(stall.getStatus()); }
        view.setConstructionStartTime(detail.getConstructionStartTime()); view.setConstructionEndTime(detail.getConstructionEndTime());
        view.setMerchantPrincipalName(detail.getMerchantPrincipalName()); view.setMerchantPrincipalPhone(detail.getMerchantPrincipalPhone());
        view.setConstructionCompanyName(detail.getConstructionCompanyName()); view.setConstructionPrincipalName(detail.getConstructionPrincipalName()); view.setConstructionPrincipalPhone(detail.getConstructionPrincipalPhone());
        view.setPropertyEngineerName(detail.getPropertyEngineerName()); view.setPropertyEngineerPhone(detail.getPropertyEngineerPhone());
        view.setCommercialManagerName(detail.getCommercialManagerName()); view.setCommercialManagerPhone(detail.getCommercialManagerPhone());
        view.setRelatedWorkflowId(detail.getRelatedWorkflowId());
        view.setAcceptanceRequestedAt(detail.getAcceptanceRequestedAt());
        view.setInspections(inspectionMapper.selectList(new QueryWrapper<ConstructionInspection>().eq("workflow_id", workflow.getId()).orderByDesc("inspected_at")));
        view.setAcceptances(acceptanceMapper.selectList(new QueryWrapper<ConstructionAcceptance>().eq("workflow_id", workflow.getId()).orderByDesc("round_no")));
        if (detail.getRelatedWorkflowId() != null) { Workflow related = workflowMapper.selectById(detail.getRelatedWorkflowId()); if (related != null) view.setRelatedWorkflowNo(related.getWorkflowNo()); }
        view.setStatus(workflow.getStatus()); view.setApplicantName(workflow.getApplicantName()); view.setRemark(workflow.getRemark()); view.setSubmittedAt(workflow.getSubmittedAt());
        view.setStallStatusBefore(detail.getStallStatusBefore()); view.setStallStatusAfter(detail.getStallStatusAfter()); view.setCompletedBy(detail.getCompletedBy()); view.setCompletedAt(detail.getCompletedAt());
        view.setArchivedAt(detail.getArchivedAt());
        view.setCreatedAt(workflow.getCreatedAt()); view.setUpdatedAt(workflow.getUpdatedAt());
        return view;
    }

    private Long findRelatedWorkflowId(Long stallId, String type) {
        if (ENTRY.equals(type)) return null;
        List<String> sourceTypes = ACCEPTANCE.equals(type) ? List.of(ENTRY) : List.of(ACCEPTANCE, ENTRY);
        Workflow related = workflowMapper.selectOne(new QueryWrapper<Workflow>()
                .eq("stall_id", stallId).in("workflow_type", sourceTypes).eq("status", "COMPLETED")
                .orderByDesc("completed_at").last("LIMIT 1"));
        return related == null ? null : related.getId();
    }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        if (!NEW_TYPES.contains(type)) throw BizException.badRequest("施工申请只支持入场装修和撤场施工");
        return type;
    }

    private void validatePeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) throw BizException.badRequest("请选择施工开始和结束时间");
        if (!end.isAfter(start)) throw BizException.badRequest("施工结束时间必须晚于开始时间");
    }

    private String targetStallStatus(String type) {
        return switch (type) { case ENTRY, ACCEPTANCE -> "OCCUPIED"; case EXIT -> "AVAILABLE"; default -> throw BizException.badRequest("不支持的施工流程类型"); };
    }

    private void fillInternalContacts(ConstructionWorkflowDetail detail, ConstructionWorkflowRequest req, SysUser user) {
        if (user == null) throw BizException.unauthorized("登录用户不存在");
        String name = text(user.getDisplayName()) ? user.getDisplayName().trim() : user.getUsername();
        String phone = trim(user.getPhone());
        if (!text(phone)) throw BizException.badRequest("当前用户未维护联系电话，请先联系管理员完善用户资料");
        Set<String> roles = Set.copyOf(userService.findRoleCodes(user.getId()));
        if (roles.contains("DEVICE_VERIFIER")) {
            detail.setPropertyEngineerName(name); detail.setPropertyEngineerPhone(phone);
            detail.setCommercialManagerName(required(req.getCommercialManagerName(), "请选择或填写商管负责人"));
            detail.setCommercialManagerPhone(required(req.getCommercialManagerPhone(), "请填写商管负责人电话"));
        } else {
            detail.setCommercialManagerName(name); detail.setCommercialManagerPhone(phone);
            detail.setPropertyEngineerName(required(req.getPropertyEngineerName(), "请选择或填写物业部工程专员"));
            detail.setPropertyEngineerPhone(required(req.getPropertyEngineerPhone(), "请填写工程专员电话"));
        }
    }

    private String required(String value, String message) { if (!text(value)) throw BizException.badRequest(message); return value.trim(); }

    private String nextWorkflowNo() {
        return "SG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String trim(String value) { return text(value) ? value.trim() : null; }
    private boolean text(String value) { return value != null && !value.isBlank(); }
}
