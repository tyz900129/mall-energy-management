package com.wanyuecity.energy.platform.service.detector;

import com.wanyuecity.energy.platform.entity.DataException;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.DataExceptionMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class OrphanRelationDetector {

    private final PlatformAccountCurrentMapper accountMapper;
    private final PlatformDeviceCurrentMapper deviceMapper;
    private final PlatformAccountDeviceCurrentMapper relationMapper;
    private final DataExceptionMapper exceptionMapper;

    @Transactional
    public int detect(Long syncBatchId) {
        Set<String> accounts = new HashSet<>();
        for (PlatformAccountCurrent row : accountMapper.selectList(null)) {
            accounts.add(row.getAccountNumber());
        }
        Set<String> devices = new HashSet<>();
        for (PlatformDeviceCurrent row : deviceMapper.selectList(null)) {
            devices.add(row.getPlatformDeviceId());
        }

        int inserted = 0;
        List<PlatformAccountDeviceCurrent> relations = relationMapper.selectList(null);
        for (PlatformAccountDeviceCurrent relation : relations) {
            if (!accounts.contains(relation.getAccountNumber())) {
                exceptionMapper.insert(orphan(
                        "RELATION_ACCOUNT_NOT_FOUND", relation, syncBatchId,
                        "关系引用了不存在的账户"));
                inserted++;
            }
            if (!devices.contains(relation.getPlatformDeviceId())) {
                exceptionMapper.insert(orphan(
                        "RELATION_DEVICE_NOT_FOUND", relation, syncBatchId,
                        "关系引用了不存在的设备"));
                inserted++;
            }
        }
        if (inserted > 0) {
            log.warn("Orphan relation detector: {} exception(s) for batchId={}", inserted, syncBatchId);
        }
        return inserted;
    }

    private DataException orphan(String type, PlatformAccountDeviceCurrent relation,
                                 Long syncBatchId, String title) {
        DataException ex = new DataException();
        ex.setExceptionType(type);
        ex.setSeverity("CRITICAL");
        ex.setStatus("OPEN");
        ex.setPlatformDeviceId(relation.getPlatformDeviceId());
        ex.setAccountNumber(relation.getAccountNumber());
        ex.setTitle(title);
        ex.setDescription("account=" + relation.getAccountNumber()
                + ", device=" + relation.getPlatformDeviceId());
        ex.setPayloadJson("{\"accountNumber\":\"" + relation.getAccountNumber()
                + "\",\"deviceId\":\"" + relation.getPlatformDeviceId() + "\"}");
        ex.setDetectedAt(LocalDateTime.now());
        ex.setSyncBatchId(syncBatchId);
        return ex;
    }
}

