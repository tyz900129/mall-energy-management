package com.wanyuecity.energy.complaint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.complaint.entity.CsBrand;
import com.wanyuecity.energy.complaint.entity.CsComplaint;
import com.wanyuecity.energy.complaint.entity.CsComplaintLog;
import com.wanyuecity.energy.complaint.entity.CsComplaintParticipant;
import com.wanyuecity.energy.complaint.entity.CsStaff;
import com.wanyuecity.energy.complaint.mapper.CsBrandMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintDictMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintLogMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintMapper;
import com.wanyuecity.energy.complaint.mapper.CsComplaintParticipantMapper;
import com.wanyuecity.energy.complaint.service.dto.ComplaintQuery;
import com.wanyuecity.energy.complaint.service.dto.ComplaintRequest;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.merchant.entity.Merchant;
import com.wanyuecity.energy.merchant.mapper.MerchantMapper;
import com.wanyuecity.energy.patrol.PatrolAttachmentService;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.stall.service.StallNameResolver;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    public static final String ATTACHMENT_TYPE = "COMPLAINT";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "pending", List.of("processing"),
            "processing", List.of("resolved"),
            "resolved", List.of("closed", "processing"),
            "closed", List.of());

    private final CsComplaintMapper complaintMapper;
    private final CsComplaintParticipantMapper participantMapper;
    private final CsComplaintLogMapper logMapper;
    private final CsComplaintDictMapper dictMapper;
    private final CsBrandMapper brandMapper;
    private final StallMapper stallMapper;
    private final MerchantMapper merchantMapper;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final PatrolAttachmentService attachmentService;
    private final ComplaintStaffService staffService;

    // ================= 查询 =================

    public Map<String, Object> list(ComplaintQuery q, Long scopeUserId) {
        int pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1 : q.getPageNum();
        int pageSize = q.getPageSize() == null || q.getPageSize() < 1 ? 20 : Math.min(q.getPageSize(), 500);
        Filter filter = filter(q, scopeUserId);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM cs_complaint c " + filter.where(), Long.class, filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add(pageSize);
        args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_LIST + " " + filter.where()
                + " ORDER BY c.occur_date DESC, c.id DESC LIMIT ? OFFSET ?", args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", total == null ? 0 : total);
        result.put("current", pageNum);
        result.put("size", pageSize);
        return result;
    }

    public List<Map<String, Object>> listAll(ComplaintQuery q, Long scopeUserId) {
        Filter filter = filter(q, scopeUserId);
        return jdbc.queryForList(SELECT_LIST + " " + filter.where() + " ORDER BY c.occur_date DESC, c.id DESC", filter.args().toArray());
    }

    public Map<String, Object> detail(Long id) {
        Map<String, Object> data;
        try {
            data = jdbc.queryForMap(SELECT_LIST + " WHERE c.id=?", id);
        } catch (Exception ex) {
            throw BizException.notFound("客诉不存在: " + id);
        }
        data.put("managers", participants(id, CsComplaintParticipant.MANAGER));
        data.put("handlers", participants(id, CsComplaintParticipant.HANDLER));
        data.put("logs", jdbc.queryForList("SELECT id,action,from_status fromStatus,to_status toStatus,operator_id operatorId,"
                + "operator_name operatorName,note,created_at createdAt FROM cs_complaint_log WHERE complaint_id=? ORDER BY id", id));
        data.put("attachments", attachmentService.list(ATTACHMENT_TYPE, id));
        return data;
    }

    // ================= 写操作 =================

    @Transactional
    public Map<String, Object> create(ComplaintRequest req, SysUser operator) {
        validate(req);
        CsComplaint c = new CsComplaint();
        apply(c, req);
        c.setStatus("pending");
        c.setCreatedBy(operator == null ? null : operator.getId());
        if (c.getNoCost()) {
            c.setCompanyCost(BigDecimal.ZERO);
            c.setMerchantCost(BigDecimal.ZERO);
        }
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                c.setComplaintNo(nextComplaintNo(c.getOccurDate()));
                complaintMapper.insert(c);
                break;
            } catch (DuplicateKeyException ex) {
                if (attempt == 5) throw BizException.badRequest("客诉编号生成冲突，请重试");
                c.setId(null);
            }
        }
        Long complaintId = c.getId();
        List<Long> managers = req.getManagerStaffIds();
        if ((managers == null || managers.isEmpty()) && c.getStallId() != null) {
            managers = responsibleManagers(c.getStallId());
        }
        replaceParticipants(complaintId, CsComplaintParticipant.MANAGER, managers);
        replaceParticipants(complaintId, CsComplaintParticipant.HANDLER, req.getHandlerStaffIds());
        log(complaintId, "create", null, "pending", operator, "登记客诉");
        return detail(complaintId);
    }

    /** 历史数据导入：可指定编号、状态与满意度，跳过状态机校验。 */
    @Transactional
    public Long createImported(ComplaintRequest req, String complaintNo, String status, String satisfaction, SysUser operator) {
        validate(req);
        CsComplaint c = new CsComplaint();
        apply(c, req);
        String normalized = normalizeStatus(status);
        c.setStatus(normalized);
        if (satisfaction != null && !satisfaction.isBlank()) c.setSatisfaction(satisfaction);
        if ("closed".equals(normalized)) {
            c.setClosedAt(LocalDateTime.now());
            c.setHandleDays(handleDays(c));
        }
        c.setCreatedBy(operator == null ? null : operator.getId());
        if (c.getNoCost()) {
            c.setCompanyCost(BigDecimal.ZERO);
            c.setMerchantCost(BigDecimal.ZERO);
        }
        if (complaintNo != null && !complaintNo.isBlank()) {
            c.setComplaintNo(complaintNo.trim());
            try {
                complaintMapper.insert(c);
            } catch (DuplicateKeyException ex) {
                throw BizException.badRequest("客诉编号已存在: " + complaintNo);
            }
        } else {
            for (int attempt = 1; attempt <= 5; attempt++) {
                try {
                    c.setComplaintNo(nextComplaintNo(c.getOccurDate()));
                    complaintMapper.insert(c);
                    break;
                } catch (DuplicateKeyException ex) {
                    if (attempt == 5) throw BizException.badRequest("客诉编号生成冲突，请重试");
                    c.setId(null);
                }
            }
        }
        Long complaintId = c.getId();
        List<Long> managers = req.getManagerStaffIds();
        if ((managers == null || managers.isEmpty()) && c.getStallId() != null) managers = responsibleManagers(c.getStallId());
        replaceParticipants(complaintId, CsComplaintParticipant.MANAGER, managers);
        replaceParticipants(complaintId, CsComplaintParticipant.HANDLER, req.getHandlerStaffIds());
        log(complaintId, "import", null, normalized, operator, "历史数据导入");
        return complaintId;
    }

    /** 状态标签或编码统一转为编码。 */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "pending";
        List<String> values = jdbc.queryForList("SELECT item_value FROM cs_complaint_dict WHERE dict_type='STATUS' "
                + "AND (item_value=? OR item_label=?) LIMIT 1", String.class, status.trim(), status.trim());
        if (values.isEmpty()) throw BizException.badRequest("无效的客诉状态: " + status);
        return values.get(0);
    }

    @Transactional
    public Map<String, Object> update(Long id, ComplaintRequest req, SysUser operator) {
        CsComplaint c = requireComplaint(id);
        validate(req);
        apply(c, req);
        if (c.getNoCost()) {
            c.setCompanyCost(BigDecimal.ZERO);
            c.setMerchantCost(BigDecimal.ZERO);
        }
        complaintMapper.updateById(c);
        if (req.getManagerStaffIds() != null) replaceParticipants(id, CsComplaintParticipant.MANAGER, req.getManagerStaffIds());
        if (req.getHandlerStaffIds() != null) replaceParticipants(id, CsComplaintParticipant.HANDLER, req.getHandlerStaffIds());
        log(id, "update", c.getStatus(), c.getStatus(), operator, "编辑客诉");
        return detail(id);
    }

    @Transactional
    public Map<String, Object> changeStatus(Long id, String target, String note, SysUser operator) {
        if (target == null || target.isBlank()) throw BizException.badRequest("请选择目标状态");
        CsComplaint c = requireComplaint(id);
        String current = c.getStatus();
        if (!TRANSITIONS.getOrDefault(current, List.of()).contains(target)) {
            throw BizException.badRequest("不允许从「" + statusLabel(current) + "」流转到「" + statusLabel(target) + "」");
        }
        c.setStatus(target);
        if ("closed".equals(target)) {
            c.setClosedAt(LocalDateTime.now());
            c.setHandleDays(handleDays(c));
        }
        complaintMapper.updateById(c);
        log(id, "status", current, target, operator, note);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> close(Long id, String satisfaction, String note, SysUser operator) {
        CsComplaint c = requireComplaint(id);
        if (!"resolved".equals(c.getStatus())) {
            throw BizException.badRequest("只有「已解决」的客诉可以关闭");
        }
        if (satisfaction != null && !satisfaction.isBlank()) {
            requireDict("SATISFACTION", satisfaction);
            c.setSatisfaction(satisfaction);
        }
        c.setStatus("closed");
        c.setClosedAt(LocalDateTime.now());
        c.setHandleDays(handleDays(c));
        complaintMapper.updateById(c);
        log(id, "close", "resolved", "closed", operator, note == null ? "关闭客诉" : note);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        CsComplaint c = requireComplaint(id);
        participantMapper.delete(new LambdaQueryWrapper<CsComplaintParticipant>().eq(CsComplaintParticipant::getComplaintId, id));
        logMapper.delete(new LambdaQueryWrapper<CsComplaintLog>().eq(CsComplaintLog::getComplaintId, id));
        attachmentService.deleteByBusiness(ATTACHMENT_TYPE, id);
        complaintMapper.deleteById(c.getId());
    }

    // ================= 附件 =================

    public List<Map<String, Object>> addAttachments(Long id, org.springframework.web.multipart.MultipartFile[] files) {
        requireComplaint(id);
        int existing = attachmentService.list(ATTACHMENT_TYPE, id).size();
        int incoming = files == null ? 0 : files.length;
        if (existing + incoming > 6) throw BizException.badRequest("每条客诉最多上传 6 张照片");
        return attachmentService.saveAll(ATTACHMENT_TYPE, id, files);
    }

    public java.util.List<Map<String, Object>> attachments(Long id) {
        requireComplaint(id);
        return attachmentService.list(ATTACHMENT_TYPE, id);
    }

    public void deleteAttachment(Long id, Long attachmentId) {
        requireComplaint(id);
        assertAttachmentOwner(id, attachmentId);
        attachmentService.deleteById(attachmentId);
    }

    public org.springframework.core.io.Resource loadAttachment(Long id, Long attachmentId) {
        requireComplaint(id);
        assertAttachmentOwner(id, attachmentId);
        return attachmentService.load(attachmentId);
    }

    public String attachmentMimeType(Long attachmentId) {
        return attachmentService.mimeType(attachmentId);
    }

    private void assertAttachmentOwner(Long complaintId, Long attachmentId) {
        List<Long> owners = jdbc.queryForList("SELECT business_id FROM patrol_attachment WHERE id=? AND business_type=?",
                Long.class, attachmentId, ATTACHMENT_TYPE);
        if (owners.isEmpty() || !java.util.Objects.equals(owners.get(0), complaintId)) {
            throw BizException.notFound("照片不存在");
        }
    }

    // ================= 内部工具 =================

    private CsComplaint requireComplaint(Long id) {
        CsComplaint c = complaintMapper.selectById(id);
        if (c == null) throw BizException.notFound("客诉不存在: " + id);
        return c;
    }

    private void validate(ComplaintRequest req) {
        if (req.getOccurDate() == null) throw BizException.badRequest("请选择发生日期");
        if (req.getCategory() == null || req.getCategory().isBlank()) throw BizException.badRequest("请选择客诉类别");
        if (req.getContent() == null || req.getContent().isBlank()) throw BizException.badRequest("请填写案例内容");
        requireDict("CATEGORY", req.getCategory());
        if (req.getChannel() != null && !req.getChannel().isBlank()) requireDict("CHANNEL", req.getChannel());
        if (req.getSatisfaction() != null && !req.getSatisfaction().isBlank()) requireDict("SATISFACTION", req.getSatisfaction());
        if (req.getBusinessType() != null && !req.getBusinessType().isBlank()
                && jdbc.queryForObject("SELECT COUNT(*) FROM business_dictionary WHERE dict_type='BUSINESS_TYPE' AND item_value=? AND status=1",
                Integer.class, req.getBusinessType()) == 0) {
            throw BizException.badRequest("业态字典项无效: " + req.getBusinessType());
        }
    }

    private void requireDict(String type, String value) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cs_complaint_dict WHERE dict_type=? AND item_value=? AND enabled=1",
                Integer.class, type, value);
        if (count == null || count == 0) throw BizException.badRequest("无效的客诉字典项: " + value);
    }

    /** 品牌、业态、楼层、商户自动带出与快照写入。 */
    private void apply(CsComplaint c, ComplaintRequest req) {
        c.setOccurDate(req.getOccurDate());
        c.setStallId(req.getStallId());
        c.setBrandId(req.getBrandId());
        c.setCategory(req.getCategory());
        c.setChannel(blankToNull(req.getChannel()));
        c.setContent(req.getContent().trim());
        c.setSolution(req.getSolution());
        c.setCompanyCost(req.getCompanyCost() == null ? BigDecimal.ZERO : req.getCompanyCost());
        c.setMerchantCost(req.getMerchantCost() == null ? BigDecimal.ZERO : req.getMerchantCost());
        c.setNoCost(Boolean.TRUE.equals(req.getNoCost()));
        c.setOnSiteHandle(Boolean.TRUE.equals(req.getOnSiteHandle()));
        c.setSatisfaction(blankToNull(req.getSatisfaction()));

        String brandName = blankToNull(req.getBrandName());
        String businessType = blankToNull(req.getBusinessType());
        String floorCode = blankToNull(req.getFloorCode());
        String businessCategory = blankToNull(req.getBusinessCategory());
        Long merchantId = req.getMerchantId();
        String merchantName = blankToNull(req.getMerchantName());

        if (req.getStallId() != null) {
            Stall stall = stallMapper.selectById(req.getStallId());
            if (stall == null) throw BizException.badRequest("铺位不存在: " + req.getStallId());
            if (brandName == null) brandName = brandNameOf(stall, merchantId);
            if (businessType == null) businessType = stall.getBusinessType();
            if (floorCode == null) floorCode = stall.getFloorCode();
            if (businessCategory == null) businessCategory = stall.getBusinessCategory();
            if (merchantId == null) merchantId = stall.getCurrentMerchantId();
            if (merchantName == null && merchantId != null) {
                Merchant m = merchantMapper.selectById(merchantId);
                if (m != null) merchantName = m.getMerchantName();
            }
        } else if (req.getBrandId() != null) {
            CsBrand brand = brandMapper.selectById(req.getBrandId());
            if (brand == null) throw BizException.badRequest("品牌不存在: " + req.getBrandId());
            if (brandName == null) brandName = brand.getName();
            if (businessType == null) businessType = brand.getBusinessType();
            if (floorCode == null) floorCode = brand.getFloorCode();
        }
        if (brandName == null) throw BizException.badRequest("请选择或填写品牌");
        if (businessType == null) throw BizException.badRequest("请选择业态");
        c.setBrandName(brandName);
        c.setBusinessType(businessType);
        c.setFloorCode(floorCode);
        c.setBusinessCategory(businessCategory);
        c.setMerchantId(merchantId);
        c.setMerchantName(merchantName);
    }

    private String brandNameOf(Stall stall, Long merchantId) {
        if (merchantId == null) merchantId = stall.getCurrentMerchantId();
        if (merchantId != null) {
            Merchant m = merchantMapper.selectById(merchantId);
            if (m != null && m.getMerchantName() != null && !m.getMerchantName().isBlank()) return m.getMerchantName();
        }
        String name = stall.getStallName();
        if (name != null && !name.isBlank()
                && !StallNameResolver.PENDING_NAME.equals(name) && !StallNameResolver.AMBIGUOUS_NAME.equals(name)) {
            return name;
        }
        return stall.getStallCode();
    }

    /** 铺位在岗责任商管：系统账号自动映射为名单人员，名单缺失时补建。 */
    private List<Long> responsibleManagers(Long stallId) {
        List<Long> userIds = jdbc.queryForList(
                "SELECT responsible_user_id FROM operation_merchant_responsibility WHERE stall_id=? AND status='ACTIVE'",
                Long.class, stallId);
        return staffService.staffIdsForUsers(userIds, CsStaff.TYPE_MANAGER);
    }

    /** 参与人按名单写入姓名快照；名单人员离职或账号注销不影响历史数据。 */
    private void replaceParticipants(Long complaintId, String type, List<Long> staffIds) {
        participantMapper.delete(new LambdaQueryWrapper<CsComplaintParticipant>()
                .eq(CsComplaintParticipant::getComplaintId, complaintId)
                .eq(CsComplaintParticipant::getParticipantType, type));
        if (staffIds == null || staffIds.isEmpty()) return;
        LinkedHashSet<Long> unique = new LinkedHashSet<>(staffIds);
        int order = 0;
        for (Long staffId : unique) {
            if (staffId == null) continue;
            CsStaff staff = staffService.require(staffId);
            CsComplaintParticipant p = new CsComplaintParticipant();
            p.setComplaintId(complaintId);
            p.setStaffId(staffId);
            p.setUserId(staff.getSourceUserId());
            p.setUserName(staff.getStaffName());
            p.setParticipantType(type);
            p.setSortOrder(order++);
            participantMapper.insert(p);
        }
    }

    private void log(Long complaintId, String action, String from, String to, SysUser operator, String note) {
        CsComplaintLog entry = new CsComplaintLog();
        entry.setComplaintId(complaintId);
        entry.setAction(action);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setOperatorId(operator == null ? null : operator.getId());
        entry.setOperatorName(operator == null ? null : displayName(operator));
        entry.setNote(note);
        entry.setCreatedAt(LocalDateTime.now());
        logMapper.insert(entry);
    }

    private String nextComplaintNo(LocalDate occurDate) {
        String prefix = "CS-" + MONTH.format(occurDate) + "-";
        List<String> latest = jdbc.queryForList("SELECT complaint_no FROM cs_complaint WHERE complaint_no LIKE ? ORDER BY complaint_no DESC LIMIT 1 FOR UPDATE",
                String.class, prefix + "%");
        int seq = latest.isEmpty() ? 1 : Integer.parseInt(latest.get(0).substring(prefix.length())) + 1;
        return prefix + String.format("%03d", seq);
    }

    public SysUser currentUser(String username) {
        if (username == null || username.isBlank()) return null;
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
    }

    /** 商管角色（且不具备客服/管理员角色）只能查看自己作为责任商管的单据。 */
    public Long scopeUserId(java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
        if (authorities == null) return null;
        boolean manager = false;
        for (var authority : authorities) {
            String code = authority.getAuthority();
            if (List.of("ROLE_CS_AGENT", "ROLE_CS_MANAGER", "ROLE_SUPER_ADMIN", "ROLE_SYSTEM_ADMIN").contains(code)) return null;
            if ("ROLE_APPLICANT".equals(code)) manager = true;
        }
        if (!manager) return null;
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        SysUser user = currentUser(authentication.getName());
        return user == null ? null : user.getId();
    }

    private record Filter(String where, List<Object> args) { }

    private Filter filter(ComplaintQuery q, Long scopeUserId) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            String like = "%" + q.getKeyword().trim() + "%";
            conditions.add("(c.complaint_no LIKE ? OR c.brand_name LIKE ? OR c.merchant_name LIKE ? OR c.content LIKE ? OR c.solution LIKE ?)");
            for (int i = 0; i < 5; i++) args.add(like);
        }
        if (q.getBusinessType() != null && !q.getBusinessType().isBlank()) {
            conditions.add("c.business_type=?"); args.add(q.getBusinessType());
        }
        if (q.getFloor() != null && !q.getFloor().isBlank()) {
            conditions.add("c.floor_code=?"); args.add(q.getFloor());
        }
        if (q.getCategory() != null && !q.getCategory().isBlank()) {
            conditions.add("c.category=?"); args.add(q.getCategory());
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            conditions.add("c.status=?"); args.add(q.getStatus());
        }
        if (q.getSatisfaction() != null && !q.getSatisfaction().isBlank()) {
            conditions.add("c.satisfaction=?"); args.add(q.getSatisfaction());
        }
        if (q.getChannel() != null && !q.getChannel().isBlank()) {
            conditions.add("c.channel=?"); args.add(q.getChannel());
        }
        if (q.getStallId() != null) {
            conditions.add("c.stall_id=?"); args.add(q.getStallId());
        }
        if (q.getBrandId() != null) {
            conditions.add("c.brand_id=?"); args.add(q.getBrandId());
        }
        if (q.getManagerStaffId() != null) {
            conditions.add("EXISTS (SELECT 1 FROM cs_complaint_participant p WHERE p.complaint_id=c.id AND p.participant_type='MANAGER' AND p.staff_id=?)");
            args.add(q.getManagerStaffId());
        }
        if (q.getHandlerStaffId() != null) {
            conditions.add("EXISTS (SELECT 1 FROM cs_complaint_participant p WHERE p.complaint_id=c.id AND p.participant_type='HANDLER' AND p.staff_id=?)");
            args.add(q.getHandlerStaffId());
        }
        if (q.getOccurDateStart() != null) {
            conditions.add("c.occur_date>=?"); args.add(q.getOccurDateStart());
        }
        if (q.getOccurDateEnd() != null) {
            conditions.add("c.occur_date<=?"); args.add(q.getOccurDateEnd());
        }
        if (q.getCostMin() != null) {
            conditions.add("(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END)>=?");
            args.add(q.getCostMin());
        }
        if (q.getCostMax() != null) {
            conditions.add("(CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END)<=?");
            args.add(q.getCostMax());
        }
        if (scopeUserId != null) {
            conditions.add("EXISTS (SELECT 1 FROM cs_complaint_participant p WHERE p.complaint_id=c.id AND p.participant_type='MANAGER' "
                    + "AND (p.user_id=? OR p.staff_id IN (SELECT s.id FROM cs_staff s WHERE s.source_user_id=?)))");
            args.add(scopeUserId);
            args.add(scopeUserId);
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return new Filter(where, args);
    }

    private List<Map<String, Object>> participants(Long complaintId, String type) {
        return jdbc.queryForList("SELECT staff_id staffId,user_id userId,user_name userName,sort_order sortOrder FROM cs_complaint_participant "
                + "WHERE complaint_id=? AND participant_type=? ORDER BY sort_order,id", complaintId, type);
    }

    private int handleDays(CsComplaint c) {
        LocalDate end = c.getClosedAt() == null ? LocalDate.now() : c.getClosedAt().toLocalDate();
        long days = ChronoUnit.DAYS.between(c.getOccurDate(), end);
        return days < 0 ? 0 : (int) days;
    }

    private String statusLabel(String status) {
        String label = jdbc.queryForObject("SELECT item_label FROM cs_complaint_dict WHERE dict_type='STATUS' AND item_value=? LIMIT 1",
                String.class, status);
        return label == null ? status : label;
    }

    private String displayName(SysUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getUsername() : user.getDisplayName();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final String SELECT_LIST = """
            SELECT c.id, c.complaint_no complaintNo, c.occur_date occurDate, c.stall_id stallId, c.brand_id brandId,
                   c.brand_name brandName, c.merchant_id merchantId, c.merchant_name merchantName,
                   c.business_type businessType, COALESCE(d.item_label, c.business_type) businessTypeLabel,
                   c.business_category businessCategory, c.floor_code floorCode, COALESCE(f.item_label, c.floor_code) floorLabel,
                   c.category category, COALESCE(cd.item_label, c.category) categoryLabel,
                   c.channel channel, COALESCE(ch.item_label, c.channel) channelLabel,
                   c.status status, COALESCE(sd.item_label, c.status) statusLabel,
                   c.satisfaction satisfaction, COALESCE(sf.item_label, c.satisfaction) satisfactionLabel,
                   c.company_cost companyCost, c.merchant_cost merchantCost,
                   (CASE WHEN c.no_cost=1 THEN 0 ELSE c.company_cost+c.merchant_cost END) totalCost,
                   c.no_cost noCost, c.on_site_handle onSiteHandle, c.handle_days handleDays,
                   c.content, c.solution, c.closed_at closedAt,
                   c.created_by createdBy, c.created_at createdAt, c.updated_at updatedAt,
                   (SELECT GROUP_CONCAT(p.user_name ORDER BY p.sort_order SEPARATOR '、') FROM cs_complaint_participant p
                     WHERE p.complaint_id=c.id AND p.participant_type='MANAGER') managerNames,
                   (SELECT GROUP_CONCAT(p.user_name ORDER BY p.sort_order SEPARATOR '、') FROM cs_complaint_participant p
                     WHERE p.complaint_id=c.id AND p.participant_type='HANDLER') handlerNames
            FROM cs_complaint c
            LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=c.business_type
            LEFT JOIN business_dictionary f ON f.dict_type='FLOOR' AND f.item_value=c.floor_code
            LEFT JOIN cs_complaint_dict cd ON cd.dict_type='CATEGORY' AND cd.item_value=c.category
            LEFT JOIN cs_complaint_dict ch ON ch.dict_type='CHANNEL' AND ch.item_value=c.channel
            LEFT JOIN cs_complaint_dict sd ON sd.dict_type='STATUS' AND sd.item_value=c.status
            LEFT JOIN cs_complaint_dict sf ON sf.dict_type='SATISFACTION' AND sf.item_value=c.satisfaction
            """;
}
