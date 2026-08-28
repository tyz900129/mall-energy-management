package com.wanyuecity.energy.platform.service.detector;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.platform.entity.DataException;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.mapper.DataExceptionMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档第 34 节：一设备多账户检测。
 * <p>
 * 任何 {@code platform_account_device_current} 中同一 {@code platformDeviceId}
 * 多账户写 INFO；一个以上启用账户另写 DEVICE_MULTI_ENABLED_ACCOUNT / WARN。
 * <p>
 * 在每次同步成功提交后调用。
 */
@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class MultiAccountDetector {

    private final PlatformAccountDeviceCurrentMapper relationMapper;
    private final PlatformAccountCurrentMapper accountMapper;
    private final DataExceptionMapper exceptionMapper;

    /** 返回写入的异常条数。 */
    @Transactional
    public int detect(Long syncBatchId) {
        List<PlatformAccountDeviceCurrent> all = relationMapper.selectList(null);
        if (all.isEmpty()) {
            return 0;
        }

        // 按 deviceId 分组
        Map<String, Set<String>> deviceToAccounts = all.stream().collect(
                Collectors.groupingBy(
                        PlatformAccountDeviceCurrent::getPlatformDeviceId,
                        Collectors.mapping(
                                PlatformAccountDeviceCurrent::getAccountNumber,
                                Collectors.toCollection(HashSet::new))));
        Map<String,Boolean> enabledAccounts=accountMapper.selectList(null).stream().collect(Collectors.toMap(
                PlatformAccountCurrent::getAccountNumber,a->isEnabled(a.getPlatformStatus()),(a,b)->a));

        List<DataException> toInsert = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : deviceToAccounts.entrySet()) {
            if (e.getValue().size() > 1) {
                DataException ex = new DataException();
                ex.setExceptionType("DEVICE_MULTI_ACCOUNT");
                ex.setSeverity("INFO");
                ex.setStatus("OPEN");
                ex.setPlatformDeviceId(e.getKey());
                // account_number is a single-value VARCHAR(64). The complete
                // account set belongs in payload_json/description.
                ex.setAccountNumber(null);
                ex.setTitle("设备绑定到 " + e.getValue().size() + " 个计费账户");
                ex.setDescription("设备 " + e.getKey() + " 在当前同步结果中关联到以下账户: " + e.getValue());
                ex.setPayloadJson("{\"deviceId\":\"" + e.getKey() + "\",\"accounts\":["
                        + e.getValue().stream().map(a -> "\"" + a + "\"").collect(Collectors.joining(","))
                        + "]}");
                ex.setDetectedAt(LocalDateTime.now());
                ex.setSyncBatchId(syncBatchId);
                toInsert.add(ex);
                Set<String> enabled=e.getValue().stream().filter(a->Boolean.TRUE.equals(enabledAccounts.get(a))).collect(Collectors.toSet());
                if(enabled.size()>1){
                    DataException warn=new DataException();warn.setExceptionType("DEVICE_MULTI_ENABLED_ACCOUNT");warn.setSeverity("WARN");warn.setStatus("OPEN");warn.setPlatformDeviceId(e.getKey());warn.setTitle("设备绑定到 "+enabled.size()+" 个启用计费账户");warn.setDescription("同一设备只能绑定一个启用计费账户；当前启用账户: "+enabled);warn.setPayloadJson("{\"deviceId\":\""+e.getKey()+"\"}");warn.setDetectedAt(LocalDateTime.now());warn.setSyncBatchId(syncBatchId);toInsert.add(warn);
                }
            }
        }
        if (toInsert.isEmpty()) {
            return 0;
        }

        // 用唯一键 (exception_type, platform_device_id, sync_batch_id) 防止同批次重复
        int inserted = 0;
        for (DataException ex : toInsert) {
            Long existed = exceptionMapper.selectCount(new QueryWrapper<DataException>()
                    .eq("exception_type", ex.getExceptionType())
                    .eq("platform_device_id", ex.getPlatformDeviceId())
                    .eq("sync_batch_id", syncBatchId));
            if (existed == null || existed == 0) {
                exceptionMapper.insert(ex);
                inserted++;
            }
        }
        if (inserted > 0) {
            log.warn("Multi-Account detector: {} new exception(s) for batchId={}", inserted, syncBatchId);
        }
        return inserted;
    }
    private boolean isEnabled(String status){return status==null||(!"0".equals(status)&&!"DISABLED".equalsIgnoreCase(status)&&!"INACTIVE".equalsIgnoreCase(status));}
}

