package com.wanyuecity.energy.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.merchant.entity.Merchant;
import com.wanyuecity.energy.merchant.mapper.MerchantMapper;
import com.wanyuecity.energy.merchant.service.dto.MerchantUpsertRequest;
import com.wanyuecity.energy.merchant.service.dto.MerchantView;
import com.wanyuecity.energy.platform.service.MerchantCodeExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");
    private static final List<String> ALLOWED_STATUS = List.of("ACTIVE", "INACTIVE", "SUSPENDED");

    private final MerchantMapper mapper;

    public Page<MerchantView> list(String keyword, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Merchant> q = new LambdaQueryWrapper<Merchant>()
                .orderByDesc(Merchant::getId);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(Merchant::getMerchantCode, k)
                    .or().like(Merchant::getMerchantName, k)
                    .or().like(Merchant::getContactName, k)
                    .or().like(Merchant::getContactPhone, k));
        }
        if (status != null && !status.isBlank()) {
            q.eq(Merchant::getStatus, status);
        }
        Page<Merchant> p = mapper.selectPage(Page.of(pageNum, pageSize), q);
        Page<MerchantView> out = Page.of(pageNum, pageSize, p.getTotal());
        out.setRecords(p.getRecords().stream().map(this::toView).toList());
        return out;
    }

    public MerchantView get(Long id) {
        Merchant m = mapper.selectById(id);
        if (m == null) throw BizException.notFound("Merchant not found");
        return toView(m);
    }

    public MerchantView getByCode(String code) {
        if (code == null || code.isBlank()) throw BizException.badRequest("code is required");
        Merchant m = mapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantCode, code.trim())
                .last("LIMIT 1"));
        if (m == null) throw BizException.notFound("Merchant not found: " + code);
        return toView(m);
    }

    @Transactional
    public MerchantView create(MerchantUpsertRequest req) {
        validateCode(req.getMerchantCode());
        validateStatus(req.getStatus());

        Long dup = mapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantCode, req.getMerchantCode()));
        if (dup > 0) {
            throw BizException.badRequest("merchant_code already exists");
        }

        Merchant m = new Merchant();
        applyRequest(m, req);
        if (m.getStatus() == null) m.setStatus("ACTIVE");
        if (m.getMerchantCodeSource() == null) m.setMerchantCodeSource("MANUAL");
        if (m.getMerchantCodeMatchStatus() == null) m.setMerchantCodeMatchStatus("MANUAL_CONFIRMED");
        mapper.insert(m);
        return toView(m);
    }

    @Transactional
    public MerchantView update(Long id, MerchantUpsertRequest req) {
        Merchant m = mapper.selectById(id);
        if (m == null) throw BizException.notFound("Merchant not found");
        validateCode(req.getMerchantCode());
        validateStatus(req.getStatus());

        if (!m.getMerchantCode().equals(req.getMerchantCode())) {
            Long dup = mapper.selectCount(new LambdaQueryWrapper<Merchant>()
                    .eq(Merchant::getMerchantCode, req.getMerchantCode())
                    .ne(Merchant::getId, id));
            if (dup > 0) {
                throw BizException.badRequest("merchant_code already exists");
            }
        }
        applyRequest(m, req);
        mapper.updateById(m);
        return toView(m);
    }

    /**
     * Try to auto-match a 6-digit code from the supplied free-text name.
     * The result records the match status: AUTO_MATCHED / AMBIGUOUS / NOT_FOUND.
     * The caller decides whether to persist it (typically: AUTO_MATCHED → save;
     * AMBIGUOUS / NOT_FOUND → operator must confirm manually).
     */
    public MerchantCodeExtractor.Match previewCode(String name) {
        return MerchantCodeExtractor.match(name);
    }

    private void applyRequest(Merchant m, MerchantUpsertRequest req) {
        m.setMerchantCode(req.getMerchantCode());
        m.setMerchantName(req.getMerchantName());
        m.setContactName(req.getContactName());
        m.setContactPhone(req.getContactPhone());
        m.setSourcePlatformAccountId(req.getSourcePlatformAccountId());
        m.setRemark(req.getRemark());
        m.setStatus(req.getStatus());
        if (req.getMerchantCodeSource() != null) {
            m.setMerchantCodeSource(req.getMerchantCodeSource());
        }
        if (req.getMerchantCodeMatchStatus() != null) {
            m.setMerchantCodeMatchStatus(req.getMerchantCodeMatchStatus());
        }
    }

    private void validateCode(String code) {
        if (code == null || !SIX_DIGITS.matcher(code).matches()) {
            throw BizException.badRequest("merchant_code must be exactly 6 digits");
        }
    }

    private void validateStatus(String s) {
        if (s == null) return;
        if (!ALLOWED_STATUS.contains(s)) {
            throw BizException.badRequest("status must be one of " + ALLOWED_STATUS);
        }
    }

    private MerchantView toView(Merchant m) {
        MerchantView v = new MerchantView();
        v.setId(m.getId());
        v.setMerchantCode(m.getMerchantCode());
        v.setMerchantCodeSource(m.getMerchantCodeSource());
        v.setMerchantCodeMatchStatus(m.getMerchantCodeMatchStatus());
        v.setMerchantName(m.getMerchantName());
        v.setContactName(m.getContactName());
        v.setContactPhone(m.getContactPhone());
        v.setSourcePlatformAccountId(m.getSourcePlatformAccountId());
        v.setStatus(m.getStatus());
        v.setRemark(m.getRemark());
        v.setCreatedAt(m.getCreatedAt());
        v.setUpdatedAt(m.getUpdatedAt());
        return v;
    }
}

