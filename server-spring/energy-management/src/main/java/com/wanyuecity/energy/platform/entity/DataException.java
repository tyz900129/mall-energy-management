package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异常中心主表（文档第 36 节）。
 * <p>
 * 字段对齐：
 * <ul>
 *   <li>{@code exceptionType}：DEVICE_MULTI_ACCOUNT / UNAUTHORIZED_ACCOUNT_DEVICE_CHANGE / SYNC_FAILED ...</li>
 *   <li>{@code severity}：INFO / WARN / ERROR / CRITICAL</li>
 *   <li>{@code status}：OPEN / ACK / HANDLING / RESOLVED / IGNORED</li>
 * </ul>
 */
@Data
@TableName("data_exception")
public class DataException {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("exception_type")
    private String exceptionType;

    @TableField("severity")
    private String severity;

    @TableField("status")
    private String status;

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("stall_id")
    private Long stallId;

    @TableField("merchant_code")
    private String merchantCode;

    @TableField("platform_device_id")
    private String platformDeviceId;

    @TableField("account_number")
    private String accountNumber;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("detected_at")
    private LocalDateTime detectedAt;

    @TableField("handler_id")
    private Long handlerId;

    @TableField("handled_at")
    private LocalDateTime handledAt;

    @TableField("handle_result")
    private String handleResult;

    @TableField("sync_batch_id")
    private Long syncBatchId;
}

