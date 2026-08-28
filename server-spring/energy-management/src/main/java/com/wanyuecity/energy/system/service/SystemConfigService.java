package com.wanyuecity.energy.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SystemConfig;
import com.wanyuecity.energy.system.mapper.SystemConfigMapper;
import com.wanyuecity.energy.system.service.dto.SystemConfigUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper mapper;

    public List<SystemConfig> list(String keyword) {
        QueryWrapper<SystemConfig> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like("config_key", k).or().like("description", k));
        }
        q.orderByAsc("config_key");
        return mapper.selectList(q);
    }

    @Transactional
    public SystemConfig upsert(SystemConfigUpsertRequest req) {
        SystemConfig existing = mapper.findByKey(req.getConfigKey());
        if (existing == null) {
            SystemConfig c = new SystemConfig();
            c.setConfigKey(req.getConfigKey());
            c.setConfigValue(req.getConfigValue());
            c.setDescription(req.getDescription());
            mapper.insert(c);
            return c;
        }
        existing.setConfigValue(req.getConfigValue());
        existing.setDescription(req.getDescription());
        mapper.updateById(existing);
        return existing;
    }

    public void delete(Long id) {
        SystemConfig c = mapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("Config not found: " + id);
        }
        mapper.deleteById(id);
    }
}

