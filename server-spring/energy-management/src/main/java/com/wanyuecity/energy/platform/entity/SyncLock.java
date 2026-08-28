package com.wanyuecity.energy.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_lock")
public class SyncLock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String holder;
    private LocalDateTime acquiredAt;
    private String note;
}

