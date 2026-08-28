package com.wanyuecity.energy.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wanyuecity.energy.system.entity.AuditLog;
import com.wanyuecity.energy.system.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper mapper;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(String operator,Long operatorId,String operation,String businessType,String businessId,String beforeData,String afterData,String ip,String userAgent){AuditLog row=new AuditLog();row.setOperator(operator);row.setOperatorId(operatorId);row.setOperation(operation);row.setBusinessType(businessType);row.setBusinessId(businessId);row.setBeforeData(beforeData);row.setAfterData(afterData);row.setIp(ip);row.setUserAgent(userAgent==null?null:userAgent.substring(0,Math.min(512,userAgent.length())));mapper.insert(row);}

    public Page<AuditLog> page(String operator, String businessType, LocalDateTime from, LocalDateTime to,
                               int pageNum, int pageSize) {
        QueryWrapper<AuditLog> q = new QueryWrapper<>();
        if (operator != null && !operator.isBlank()) {
            q.like("operator", operator.trim());
        }
        if (businessType != null && !businessType.isBlank()) {
            q.eq("business_type", businessType.trim());
        }
        if (from != null) {
            q.ge("created_at", from);
        }
        if (to != null) {
            q.le("created_at", to);
        }
        q.orderByDesc("created_at");
        return mapper.selectPage(Page.of(pageNum, pageSize), q);
    }
}
