package com.wanyuecity.energy.complaint.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.complaint.entity.CsStaff;
import com.wanyuecity.energy.complaint.mapper.CsStaffMapper;
import com.wanyuecity.energy.core.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 客诉人员名单：经办人（客服员）与责任商管的维护与选择。
 * 客诉参与人保存姓名快照，名单人员离开或账号注销不影响历史数据展示。
 */
@Service
@RequiredArgsConstructor
public class ComplaintStaffService {

    private final CsStaffMapper staffMapper;
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> list(String type, String keyword, String status) {
        LambdaQueryWrapper<CsStaff> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) w.eq(CsStaff::getStaffType, type);
        if (StringUtils.hasText(status)) w.eq(CsStaff::getStatus, status);
        if (StringUtils.hasText(keyword)) w.like(CsStaff::getStaffName, keyword.trim());
        w.orderByAsc(CsStaff::getStaffType).orderByAsc(CsStaff::getSortOrder).orderByAsc(CsStaff::getId);
        List<CsStaff> rows = staffMapper.selectList(w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CsStaff s : rows) result.add(view(s));
        return result;
    }

    /** 录入选择用：仅返回在岗名单人员。 */
    public List<Map<String, Object>> options(String type, String keyword) {
        return list(type, keyword, CsStaff.STATUS_ACTIVE);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        String type = str(body.get("staffType"));
        String name = str(body.get("staffName"));
        if (!StringUtils.hasText(type)) throw new BizException("请选择名单类型");
        if (!StringUtils.hasText(name)) throw new BizException("请填写姓名");
        CsStaff exists = findByName(name.trim(), type);
        if (exists != null) throw new BizException("该名单中已存在人员：" + name.trim());
        CsStaff staff = new CsStaff();
        staff.setStaffType(type);
        staff.setStaffName(name.trim());
        staff.setDepartment(str(body.get("department")));
        staff.setPhone(str(body.get("phone")));
        staff.setStatus(StringUtils.hasText(str(body.get("status"))) ? str(body.get("status")) : CsStaff.STATUS_ACTIVE);
        staff.setSortOrder(intValue(body.get("sortOrder"), 0));
        staff.setRemark(str(body.get("remark")));
        Object sourceUserId = body.get("sourceUserId");
        if (sourceUserId != null && StringUtils.hasText(sourceUserId.toString())) {
            staff.setSourceUserId(Long.valueOf(sourceUserId.toString()));
        }
        try {
            staffMapper.insert(staff);
        } catch (DuplicateKeyException e) {
            throw new BizException("该名单中已存在人员：" + name.trim());
        }
        return view(staff);
    }

    public Map<String, Object> update(Long id, Map<String, Object> body) {
        CsStaff staff = require(id);
        String name = str(body.get("staffName"));
        String type = str(body.get("staffType"));
        if (StringUtils.hasText(name)) staff.setStaffName(name.trim());
        if (StringUtils.hasText(type)) staff.setStaffType(type);
        if (body.containsKey("department")) staff.setDepartment(str(body.get("department")));
        if (body.containsKey("phone")) staff.setPhone(str(body.get("phone")));
        if (body.containsKey("status")) staff.setStatus(str(body.get("status")));
        if (body.containsKey("sortOrder")) staff.setSortOrder(intValue(body.get("sortOrder"), 0));
        if (body.containsKey("remark")) staff.setRemark(str(body.get("remark")));
        if (body.containsKey("sourceUserId")) {
            Object v = body.get("sourceUserId");
            staff.setSourceUserId(v == null || !StringUtils.hasText(v.toString()) ? null : Long.valueOf(v.toString()));
        }
        try {
            staffMapper.updateById(staff);
        } catch (DuplicateKeyException e) {
            throw new BizException("该名单中已存在人员：" + staff.getStaffName());
        }
        return view(staff);
    }

    public void delete(Long id) {
        require(id);
        Long used = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cs_complaint_participant WHERE staff_id=?", Long.class, id);
        if (used != null && used > 0) {
            throw new BizException("该人员已被 " + used + " 条客诉引用，请改为停用以保留历史记录");
        }
        staffMapper.deleteById(id);
    }

    @Transactional
    public CsStaff require(Long id) {
        CsStaff staff = staffMapper.selectById(id);
        if (staff == null) throw new BizException("名单人员不存在");
        return staff;
    }

    public CsStaff findByName(String name, String type) {
        if (!StringUtils.hasText(name)) return null;
        return staffMapper.selectOne(new LambdaQueryWrapper<CsStaff>()
                .eq(CsStaff::getStaffName, name.trim())
                .eq(CsStaff::getStaffType, type)
                .last("LIMIT 1"));
    }

    /** 导入/补录时按姓名取名单，不存在则自动新增，避免历史人员无法落地。 */
    @Transactional
    public Long ensureByName(String name, String type) {
        if (!StringUtils.hasText(name)) return null;
        CsStaff exists = findByName(name, type);
        if (exists != null) return exists.getId();
        CsStaff staff = new CsStaff();
        staff.setStaffType(type);
        staff.setStaffName(name.trim());
        staff.setStatus(CsStaff.STATUS_ACTIVE);
        staff.setSortOrder(0);
        staff.setRemark("历史数据导入自动创建");
        try {
            staffMapper.insert(staff);
        } catch (DuplicateKeyException e) {
            CsStaff again = findByName(name, type);
            return again == null ? null : again.getId();
        }
        return staff.getId();
    }

    /** 铺位责任商管（系统账号）映射为名单人员；名单缺失时自动补建。 */
    @Transactional
    public List<Long> staffIdsForUsers(List<Long> userIds, String type) {
        if (userIds == null || userIds.isEmpty()) return new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (userId == null) continue;
            CsStaff linked = staffMapper.selectOne(new LambdaQueryWrapper<CsStaff>()
                    .eq(CsStaff::getSourceUserId, userId)
                    .eq(CsStaff::getStaffType, type)
                    .last("LIMIT 1"));
            if (linked != null) {
                ids.add(linked.getId());
                continue;
            }
            Map<String, Object> user = userRow(userId);
            if (user == null) continue;
            String name = String.valueOf(user.getOrDefault("display_name", user.get("username")));
            Long staffId = ensureByName(name, type);
            if (staffId != null) {
                CsStaff staff = staffMapper.selectById(staffId);
                if (staff != null && staff.getSourceUserId() == null) {
                    staff.setSourceUserId(userId);
                    staffMapper.updateById(staff);
                }
                ids.add(staffId);
            }
        }
        return ids;
    }

    @Transactional
    public List<Map<String, Object>> byUsers(List<Long> userIds, String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : staffIdsForUsers(userIds, type)) {
            CsStaff staff = staffMapper.selectById(id);
            if (staff != null) result.add(view(staff));
        }
        return result;
    }

    private Map<String, Object> userRow(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, display_name FROM sys_user WHERE id=?", userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> view(CsStaff s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("staffType", s.getStaffType());
        m.put("staffName", s.getStaffName());
        m.put("department", s.getDepartment());
        m.put("phone", s.getPhone());
        m.put("sourceUserId", s.getSourceUserId());
        m.put("status", s.getStatus());
        m.put("sortOrder", s.getSortOrder());
        m.put("remark", s.getRemark());
        return m;
    }

    private String str(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private Integer intValue(Object value, int fallback) {
        if (value == null || !StringUtils.hasText(value.toString())) return fallback;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
