package com.wanyuecity.energy.qrinspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.qrinspection.entity.SysDepartmentWebhook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;
@Mapper public interface SysDepartmentWebhookMapper extends BaseMapper<SysDepartmentWebhook> {
    /** 查询某部门的主 webhook 配置 id（enabled=1 + 绑定的 config 启用） */
    @Select("SELECT wc.id FROM sys_department_webhook dw " +
            "JOIN webhook_config wc ON wc.id=dw.webhook_config_id " +
            "WHERE dw.department_id=#{deptId} AND dw.enabled=1 AND dw.bind_type='PRIMARY' AND wc.enabled=1")
    List<Long> primaryConfigIdsOfDept(@Param("deptId") Long deptId);

    @Select("SELECT dw.webhook_config_id configId, wc.provider, d.code dept_code FROM sys_department_webhook dw " +
            "JOIN webhook_config wc ON wc.id=dw.webhook_config_id " +
            "JOIN sys_department d ON d.id=dw.department_id " +
            "WHERE dw.enabled=1 AND wc.enabled=1 AND dw.bind_type='PRIMARY'")
    List<Map<String,Object>> allActivePrimaryBindings();
}
