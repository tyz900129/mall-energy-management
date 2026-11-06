package com.wanyuecity.energy.complaint.service;

import com.wanyuecity.energy.complaint.entity.CsBrand;
import com.wanyuecity.energy.complaint.mapper.CsBrandMapper;
import com.wanyuecity.energy.core.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客诉登记页所需的品牌、人员、字典选项。 */
@Service
@RequiredArgsConstructor
public class ComplaintOptionService {

    private static final String DEFAULT_USER_ROLES = "CS_AGENT,CS_MANAGER,APPLICANT,PROPERTY_MANAGER,OPS_PATROL_MANAGER";

    private final JdbcTemplate jdbc;
    private final CsBrandMapper brandMapper;

    /** 品牌候选：铺位/商户主数据优先，非铺位补充品牌在后。 */
    public List<Map<String, Object>> brands(String keyword, String businessType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(STALL_BRAND_SELECT + " WHERE s.status<>'DECOMMISSIONED'");
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (s.stall_name LIKE ? OR s.stall_code LIKE ? OR m.merchant_name LIKE ? OR s.business_category LIKE ?)");
            for (int i = 0; i < 4; i++) args.add(like);
        }
        if (businessType != null && !businessType.isBlank()) {
            sql.append(" AND s.business_type=?"); args.add(businessType);
        }
        sql.append(" ORDER BY brandName LIMIT 20");
        List<Map<String, Object>> stalls = new ArrayList<>(jdbc.queryForList(sql.toString(), args.toArray()));

        List<Object> brandArgs = new ArrayList<>();
        StringBuilder brandSql = new StringBuilder(BRAND_SELECT + " WHERE b.active=1");
        if (keyword != null && !keyword.isBlank()) {
            brandSql.append(" AND b.name LIKE ?");
            brandArgs.add("%" + keyword.trim() + "%");
        }
        if (businessType != null && !businessType.isBlank()) {
            brandSql.append(" AND b.business_type=?");
            brandArgs.add(businessType);
        }
        brandSql.append(" ORDER BY b.name LIMIT 20");
        List<Map<String, Object>> manual = new ArrayList<>(jdbc.queryForList(brandSql.toString(), brandArgs.toArray()));

        List<Map<String, Object>> merged = new ArrayList<>(stalls);
        merged.addAll(manual);
        merged.sort(Comparator.comparing(m -> String.valueOf(m.get("brandName") == null ? "" : m.get("brandName"))));
        return merged.size() > 20 ? merged.subList(0, 20) : merged;
    }

    /** 移动端扫码：按铺位二维码 token 直接定位品牌。 */
    public Map<String, Object> brandByToken(String token) {
        if (token == null || token.isBlank()) throw BizException.badRequest("二维码无效");
        List<Map<String, Object>> rows = jdbc.queryForList(
                STALL_BRAND_SELECT + " WHERE s.operation_qr_token=? AND s.status<>'DECOMMISSIONED' LIMIT 1", token.trim());
        if (rows.isEmpty()) throw BizException.notFound("未找到该二维码对应的铺位");
        return rows.get(0);
    }

    /** 非铺位品牌（超市品类/物业/自营主力）手工新增。 */
    public Map<String, Object> createBrand(Map<String, Object> body) {
        String name = text(body.get("name"));
        if (name == null) throw BizException.badRequest("请填写品牌/品类名称");
        String businessType = text(body.get("businessType"));
        String floorCode = text(body.get("floorCode"));
        String remark = text(body.get("remark"));
        CsBrand existing = brandMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CsBrand>()
                .eq(CsBrand::getName, name).eq(businessType != null, CsBrand::getBusinessType, businessType).last("LIMIT 1"));
        if (existing != null) return brandView(existing);
        CsBrand brand = new CsBrand();
        brand.setName(name);
        brand.setBusinessType(businessType);
        brand.setFloorCode(floorCode);
        brand.setSource("MANUAL");
        brand.setActive(1);
        brand.setRemark(remark);
        try {
            brandMapper.insert(brand);
        } catch (DuplicateKeyException ex) {
            CsBrand again = brandMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CsBrand>()
                    .eq(CsBrand::getName, name).last("LIMIT 1"));
            if (again != null) return brandView(again);
            throw BizException.badRequest("品牌已存在");
        }
        return brandView(brand);
    }

    /** 经办人/责任商管候选：按角色筛选，支持姓名/账号/手机号搜索。 */
    public List<Map<String, Object>> users(String roleCodes, String keyword) {
        String codes = roleCodes == null || roleCodes.isBlank() ? DEFAULT_USER_ROLES : roleCodes;
        List<String> roleList = List.of(codes.split(","));
        List<Object> args = new ArrayList<>(roleList);
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT u.id userId, u.username, COALESCE(NULLIF(u.display_name,''), u.username) displayName,
                       u.phone, d.name departmentName
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id=u.id
                JOIN sys_role r ON r.id=ur.role_id
                LEFT JOIN sys_department d ON d.id=u.department_id
                WHERE u.status=1 AND r.code IN (""" + placeholders(roleList.size()) + ")");
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (u.display_name LIKE ? OR u.username LIKE ? OR u.phone LIKE ?)");
            args.add(like); args.add(like); args.add(like);
        }
        sql.append(" ORDER BY displayName LIMIT 50");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 客诉自有字典，可按类型过滤。 */
    public List<Map<String, Object>> dicts(String type) {
        if (type == null || type.isBlank()) {
            return jdbc.queryForList("SELECT dict_type dictType,item_value itemValue,item_label itemLabel,color,sort_order sortOrder "
                    + "FROM cs_complaint_dict WHERE enabled=1 ORDER BY dict_type,sort_order");
        }
        return jdbc.queryForList("SELECT dict_type dictType,item_value itemValue,item_label itemLabel,color,sort_order sortOrder "
                + "FROM cs_complaint_dict WHERE enabled=1 AND dict_type=? ORDER BY sort_order", type);
    }

    /** 基础字典：业态与楼层。 */
    public Map<String, Object> dictionaryOptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessTypes", dictionary("BUSINESS_TYPE"));
        result.put("floors", dictionary("FLOOR"));
        return result;
    }

    private List<Map<String, Object>> dictionary(String type) {
        return jdbc.queryForList("SELECT item_value value,item_label label,sort_order sortOrder FROM business_dictionary "
                + "WHERE dict_type=? AND status=1 ORDER BY sort_order,item_value", type);
    }

    private Map<String, Object> brandView(CsBrand brand) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("brandId", brand.getId());
        row.put("brandName", brand.getName());
        row.put("stallId", null);
        row.put("stallCode", null);
        row.put("merchantId", null);
        row.put("merchantName", null);
        row.put("businessType", brand.getBusinessType());
        row.put("businessTypeLabel", dictionaryLabel("BUSINESS_TYPE", brand.getBusinessType()));
        row.put("businessCategory", null);
        row.put("floorCode", brand.getFloorCode());
        row.put("floorLabel", dictionaryLabel("FLOOR", brand.getFloorCode()));
        row.put("responsibleUserId", null);
        row.put("responsibleUserName", null);
        return row;
    }

    private String dictionaryLabel(String type, String value) {
        if (value == null || value.isBlank()) return null;
        List<String> labels = jdbc.queryForList("SELECT item_label FROM business_dictionary WHERE dict_type=? AND item_value=? LIMIT 1",
                String.class, type, value);
        return labels.isEmpty() ? value : labels.get(0);
    }

    private String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static final String STALL_BRAND_SELECT = """
            SELECT s.id stallId, NULL brandId,
                   COALESCE(NULLIF(m.merchant_name,''), NULLIF(s.stall_name,''), s.stall_code) brandName,
                   s.stall_code stallCode, s.current_merchant_id merchantId, m.merchant_name merchantName,
                   s.business_type businessType, COALESCE(d.item_label, s.business_type) businessTypeLabel,
                   s.business_category businessCategory, s.floor_code floorCode,
                   COALESCE(f.item_label, s.floor_code) floorLabel,
                   (SELECT r.responsible_user_id FROM operation_merchant_responsibility r
                     WHERE r.stall_id=s.id AND r.status='ACTIVE' ORDER BY r.id LIMIT 1) responsibleUserId,
                   (SELECT COALESCE(NULLIF(u.display_name,''), u.username) FROM operation_merchant_responsibility r
                     JOIN sys_user u ON u.id=r.responsible_user_id
                     WHERE r.stall_id=s.id AND r.status='ACTIVE' ORDER BY r.id LIMIT 1) responsibleUserName
            FROM stall s
            LEFT JOIN merchant m ON m.id=s.current_merchant_id
            LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=s.business_type
            LEFT JOIN business_dictionary f ON f.dict_type='FLOOR' AND f.item_value=s.floor_code
            """;

    private static final String BRAND_SELECT = """
            SELECT NULL stallId, b.id brandId, b.name brandName, NULL stallCode, NULL merchantId, NULL merchantName,
                   b.business_type businessType, COALESCE(d.item_label, b.business_type) businessTypeLabel,
                   NULL businessCategory, b.floor_code floorCode, COALESCE(f.item_label, b.floor_code) floorLabel,
                   NULL responsibleUserId, NULL responsibleUserName
            FROM cs_brand b
            LEFT JOIN business_dictionary d ON d.dict_type='BUSINESS_TYPE' AND d.item_value=b.business_type
            LEFT JOIN business_dictionary f ON f.dict_type='FLOOR' AND f.item_value=b.floor_code
            """;
}
