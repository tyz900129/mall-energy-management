package com.wanyuecity.energy.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import com.wanyuecity.energy.workflow.entity.ConstructionAcceptance;
import com.wanyuecity.energy.workflow.entity.ConstructionInspection;
import com.wanyuecity.energy.workflow.entity.ConstructionWorkflowDetail;
import com.wanyuecity.energy.workflow.entity.Workflow;
import com.wanyuecity.energy.workflow.mapper.ConstructionAcceptanceMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionInspectionMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionWorkflowDetailMapper;
import com.wanyuecity.energy.workflow.mapper.WorkflowMapper;
import com.wanyuecity.energy.workflow.service.dto.ConstructionAcceptanceRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionAcceptanceResultRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionInspectionRequest;
import com.wanyuecity.energy.workflow.service.dto.ConstructionWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConstructionLifecycleService {
    private static final Set<String> INSPECTION_TYPES = Set.of("SECURITY", "ENGINEERING", "CLEANING");
    private final WorkflowMapper workflowMapper;
    private final ConstructionWorkflowDetailMapper detailMapper;
    private final ConstructionInspectionMapper inspectionMapper;
    private final ConstructionAcceptanceMapper acceptanceMapper;
    private final StallMapper stallMapper;
    private final SysUserMapper userMapper;
    private final ConstructionWorkflowService workflowService;
    private final ConstructionInspectionTemplateService inspectionTemplateService;
    private final ObjectMapper objectMapper;
    @Value("${energy.public-base-url:https://auto.apperload.com}") private String publicBaseUrl;

    public ConstructionWorkflowView publicView(String token) {
        ConstructionWorkflowDetail detail = requireDetailByToken(token);
        return workflowService.get(detail.getWorkflowId(), null, false);
    }

    public byte[] qr(String token) {
        requireDetailByToken(token);
        try {
            String url = publicBaseUrl.replaceAll("/$", "") + "/construction-public/" + token;
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 420, 420);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw BizException.badRequest("施工许可证二维码生成失败");
        }
    }

    @Transactional
    public ConstructionInspection inspect(Long workflowId, ConstructionInspectionRequest request, String username) {
        Workflow workflow = requireActive(workflowId);
        String type = request.getInspectionType().trim().toUpperCase();
        if (!INSPECTION_TYPES.contains(type)) throw BizException.badRequest("不支持的巡检类型");
        Map<String, Object> template = inspectionTemplateService.require(type);
        Object enabledValue = template.get("enabled");
        boolean templateEnabled = Boolean.TRUE.equals(enabledValue)
                || (enabledValue instanceof Number number && number.intValue() == 1);
        if (!templateEnabled) throw BizException.badRequest("该巡检类型已停用");
        String templateJson = String.valueOf(template.get("fieldsJson"));
        JsonNode fields = inspectionTemplateService.parseFields(templateJson);
        JsonNode answers = parseAnswers(request.getAnswersJson());
        List<String> abnormalLabels = new ArrayList<>();
        for (JsonNode field : fields) {
            if (!field.path("enabled").asBoolean(true)) continue;
            String key = field.path("key").asText();
            JsonNode answer = answers.get(key);
            if (field.path("required").asBoolean(false) && isEmptyAnswer(answer))
                throw BizException.badRequest("请完成必填项：" + field.path("label").asText());
            if (!isEmptyAnswer(answer) && isAbnormal(field, answer)) abnormalLabels.add(field.path("label").asText());
        }
        String result = abnormalLabels.isEmpty() ? "NORMAL" : "ISSUE";
        String problemDescription = trim(request.getProblemDescription());
        if ("ISSUE".equals(result) && problemDescription == null)
            problemDescription = "异常项目：" + String.join("、", abnormalLabels);
        SysUser user = requireUser(username);
        ConstructionInspection row = new ConstructionInspection();
        row.setWorkflowId(workflow.getId()); row.setInspectionType(type); row.setResult(result);
        row.setTemplateSnapshotJson(templateJson);
        row.setAnswersJson(writeJson(answers));
        row.setProblemDescription(problemDescription);
        row.setRectificationRequirement(trim(request.getRectificationRequirement()));
        row.setRectificationDeadline(request.getRectificationDeadline());
        row.setRectificationStatus("ISSUE".equals(result) ? "PENDING" : "NONE");
        row.setInspectorUserId(user.getId()); row.setInspectorName(displayName(user)); row.setInspectorPhone(user.getPhone());
        row.setInspectedAt(LocalDateTime.now());
        inspectionMapper.insert(row);
        return row;
    }

    private JsonNode parseAnswers(String json) {
        if (json == null || json.isBlank()) return objectMapper.createObjectNode();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) throw BizException.badRequest("巡检答案格式无效");
            return node;
        } catch (BizException ex) { throw ex; }
        catch (Exception ex) { throw BizException.badRequest("巡检答案格式无效"); }
    }

    private String writeJson(JsonNode node) {
        try { return objectMapper.writeValueAsString(node); }
        catch (Exception ex) { throw BizException.badRequest("巡检答案无法保存"); }
    }

    private boolean isEmptyAnswer(JsonNode answer) {
        return answer == null || answer.isNull() || (answer.isTextual() && answer.asText().isBlank())
                || (answer.isArray() && answer.isEmpty());
    }

    private boolean isAbnormal(JsonNode field, JsonNode answer) {
        String control = field.path("controlType").asText();
        JsonNode normal = field.get("normalValue");
        if ("NORMAL_ABNORMAL".equals(control)) return !"NORMAL".equalsIgnoreCase(answer.asText());
        if (normal == null || normal.isNull() || normal.asText().isBlank()) return false;
        if (answer.isArray()) {
            for (JsonNode item : answer) if (normal.asText().equals(item.asText())) return false;
            return true;
        }
        return !normal.asText().equals(answer.asText());
    }

    @Transactional
    public ConstructionInspection closeInspection(Long workflowId, Long inspectionId) {
        requireActive(workflowId);
        ConstructionInspection row = inspectionMapper.selectById(inspectionId);
        if (row == null || !Objects.equals(row.getWorkflowId(), workflowId)) throw BizException.notFound("巡检记录不存在");
        if (!"PENDING".equals(row.getRectificationStatus())) throw BizException.badRequest("该巡检记录无需整改确认");
        row.setRectificationStatus("COMPLETED");
        inspectionMapper.updateById(row);
        return row;
    }

    @Transactional
    public ConstructionAcceptance requestAcceptance(Long workflowId, ConstructionAcceptanceRequest request,
                                                     String username, boolean canManage) {
        Workflow workflow = require(workflowId);
        if (!canManage && !Objects.equals(workflow.getApplicantName(), username)) throw BizException.forbidden("只能为本人提交的施工流程申请验收");
        if (!Set.of("IN_PROGRESS", "REJECTED").contains(workflow.getStatus())) throw BizException.badRequest("当前流程状态不能申请验收");
        long openIssues = inspectionMapper.selectCount(new QueryWrapper<ConstructionInspection>()
                .eq("workflow_id", workflowId).eq("rectification_status", "PENDING"));
        if (openIssues > 0) throw BizException.badRequest("仍有未完成整改的巡检问题，不能申请验收");
        Integer maxRound = acceptanceMapper.selectList(new QueryWrapper<ConstructionAcceptance>().eq("workflow_id", workflowId))
                .stream().map(ConstructionAcceptance::getRoundNo).max(Integer::compareTo).orElse(0);
        ConstructionAcceptance row = new ConstructionAcceptance();
        row.setWorkflowId(workflowId); row.setRoundNo(maxRound + 1); row.setActualCompletionTime(request.getActualCompletionTime());
        row.setApplicationNote(trim(request.getApplicationNote())); row.setStatus("PENDING");
        row.setRequestedBy(username); row.setRequestedAt(LocalDateTime.now());
        acceptanceMapper.insert(row);
        workflowMapper.update(null, new UpdateWrapper<Workflow>().eq("id", workflowId).set("status", "PENDING_ACCEPTANCE"));
        detailMapper.update(null, new UpdateWrapper<ConstructionWorkflowDetail>().eq("workflow_id", workflowId)
                .set("acceptance_requested_at", row.getRequestedAt()));
        return row;
    }

    @Transactional
    public ConstructionAcceptance decideAcceptance(Long workflowId, ConstructionAcceptanceResultRequest request, String username) {
        Workflow workflow = require(workflowId);
        if (!"PENDING_ACCEPTANCE".equals(workflow.getStatus())) throw BizException.badRequest("当前流程不在待验收状态");
        String result = request.getResult().trim().toUpperCase();
        if (!Set.of("QUALIFIED", "REJECTED").contains(result)) throw BizException.badRequest("验收结果必须为合格或不合格");
        ConstructionAcceptance row = acceptanceMapper.selectOne(new QueryWrapper<ConstructionAcceptance>()
                .eq("workflow_id", workflowId).eq("status", "PENDING").orderByDesc("round_no").last("LIMIT 1"));
        if (row == null) throw BizException.notFound("待处理的验收申请不存在");
        row.setResult(result); row.setResultNote(trim(request.getResultNote())); row.setInspectedBy(username);
        row.setInspectedAt(LocalDateTime.now()); row.setStatus("QUALIFIED".equals(result) ? "COMPLETED" : "REJECTED");
        acceptanceMapper.updateById(row);
        if ("REJECTED".equals(result)) {
            workflowMapper.update(null, new UpdateWrapper<Workflow>().eq("id", workflowId).set("status", "REJECTED"));
            return row;
        }
        ConstructionWorkflowDetail detail = requireDetail(workflowId);
        String target = ConstructionWorkflowService.ENTRY.equals(workflow.getWorkflowType()) ? "OCCUPIED" : "AVAILABLE";
        stallMapper.update(null, new UpdateWrapper<com.wanyuecity.energy.stall.entity.Stall>().eq("id", workflow.getStallId()).set("status", target)
                .set(ConstructionWorkflowService.EXIT.equals(workflow.getWorkflowType()), "current_merchant_id", null));
        LocalDateTime now = LocalDateTime.now();
        detailMapper.update(null, new UpdateWrapper<ConstructionWorkflowDetail>().eq("workflow_id", workflowId)
                .set("stall_status_after", target).set("completed_by", username).set("completed_at", now).set("archived_at", now));
        workflowMapper.update(null, new UpdateWrapper<Workflow>().eq("id", workflowId).set("status", "ARCHIVED").set("completed_at", now));
        return row;
    }

    private Workflow requireActive(Long id) {
        Workflow workflow = require(id);
        if (!Set.of("IN_PROGRESS", "PENDING_ACCEPTANCE", "REJECTED").contains(workflow.getStatus()))
            throw BizException.badRequest("当前施工流程不能继续巡检");
        return workflow;
    }

    private Workflow require(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null || !ConstructionWorkflowService.TYPES.contains(workflow.getWorkflowType())) throw BizException.notFound("施工流程不存在");
        return workflow;
    }

    private ConstructionWorkflowDetail requireDetail(Long workflowId) {
        ConstructionWorkflowDetail detail = detailMapper.selectOne(new QueryWrapper<ConstructionWorkflowDetail>().eq("workflow_id", workflowId).last("LIMIT 1"));
        if (detail == null) throw BizException.notFound("施工流程明细不存在");
        return detail;
    }

    private ConstructionWorkflowDetail requireDetailByToken(String token) {
        ConstructionWorkflowDetail detail = detailMapper.selectOne(new QueryWrapper<ConstructionWorkflowDetail>().eq("public_token", token).last("LIMIT 1"));
        if (detail == null) throw BizException.notFound("施工许可证不存在或已失效");
        return detail;
    }

    private SysUser requireUser(String username) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (user == null) throw BizException.unauthorized("登录用户不存在");
        return user;
    }

    private String displayName(SysUser user) { return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getUsername() : user.getDisplayName(); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
