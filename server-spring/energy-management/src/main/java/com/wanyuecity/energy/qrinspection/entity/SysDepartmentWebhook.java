package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 部门-群机器人绑定（PRIMARY 主群 / CC 抄送群）。
 * 表使用复合主键 (department_id, webhook_config_id, bind_type)，无自增 id，
 * 因此不要用 deleteById/updateById 等按 id 的 BaseMapper 方法。
 */
@Data
@TableName("sys_department_webhook")
public class SysDepartmentWebhook {
    private Long departmentId;
    private Long webhookConfigId;
    /** PRIMARY / CC */
    private String bindType;
    private Integer enabled;
    private LocalDateTime createdAt;
}
