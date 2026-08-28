package com.wanyuecity.energy.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.platform.dto.PlatformAccount;
import com.wanyuecity.energy.platform.dto.PlatformAccountDeviceRelation;
import com.wanyuecity.energy.platform.dto.PlatformDevice;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import com.wanyuecity.energy.platform.entity.PlatformDeviceCurrent;
import com.wanyuecity.energy.platform.mapper.PlatformAccountCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformAccountDeviceCurrentMapper;
import com.wanyuecity.energy.platform.mapper.PlatformDeviceCurrentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Transactional persistence of the source-platform mirror tables. The whole
 * truncate-then-insert sequence for one data set lives in a single
 * transaction so an interrupted sync never leaves a half-populated current.
 */
@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class CurrentPersistence {

    private final PlatformAccountCurrentMapper accountMapper;
    private final PlatformDeviceCurrentMapper deviceMapper;
    private final PlatformAccountDeviceCurrentMapper relationMapper;
    private final ObjectMapper objectMapper;

    /**
     * 一次事务：清 + 写 accounts / devices / relations。
     * <p>spec 文档第 16 节：clear current → write current → write snapshot 必须在同一 COMMIT。
     * 失败时整批回滚，上一批 current 保持不变。
     */
    @Transactional
    public int[] replaceAll(List<PlatformAccount> accounts,
                            List<PlatformDevice> devices,
                            List<PlatformAccountDeviceRelation> relations,
                            Long batchId, LocalDateTime now) {
        int aCount = replaceAccounts(accounts, batchId, now);
        int dCount = replaceDevices(devices, batchId, now);
        int rCount = replaceRelations(relations, batchId, now);
        return new int[]{aCount, dCount, rCount};
    }

    @Transactional
    public int replaceAccounts(List<PlatformAccount> src, Long batchId, LocalDateTime now) {
        accountMapper.deleteAll();
        if (src.isEmpty()) return 0;
        List<PlatformAccountCurrent> rows = new ArrayList<>(src.size());
        for (PlatformAccount a : src) {
            PlatformAccountCurrent r = new PlatformAccountCurrent();
            r.setPlatformAccountId(a.getPlatformAccountId());
            r.setAccountNumber(a.getAccountNumber());
            r.setAccountName(a.getAccountName());
            r.setUserName(a.getUserName());
            r.setContactName(null);
            r.setMobilePhone(a.getMobilePhone());

            MerchantCodeExtractor.Match m = MerchantCodeExtractor.match(a.getUserName());
            if (!m.found()) m = MerchantCodeExtractor.match(a.getAccountName());
            r.setMerchantCode(m.code());
            r.setMerchantCodeSource("AUTO_REGEX");
            r.setMerchantCodeMatchStatus(m.status());

            r.setBalance(a.getBalance());
            r.setArrears(a.getArrears());
            r.setPlatformStatus(a.getPlatformStatus());
            r.setSourceSpaceId(a.getSourceSpaceId());
            r.setSyncBatchId(batchId);
            r.setLastSyncedAt(now);
            r.setRawJson(safeJson(a));
            rows.add(r);
        }
        for (PlatformAccountCurrent r : rows) {
            accountMapper.insert(r);
        }
        return rows.size();
    }

    @Transactional
    public int replaceDevices(List<PlatformDevice> src, Long batchId, LocalDateTime now) {
        deviceMapper.deleteAll();
        if (src.isEmpty()) return 0;
        List<PlatformDeviceCurrent> rows = new ArrayList<>(src.size());
        for (PlatformDevice d : src) {
            PlatformDeviceCurrent r = new PlatformDeviceCurrent();
            r.setPlatformDeviceId(d.getPlatformDeviceId());
            r.setDeviceName(d.getDeviceName());
            r.setCommAddress(d.getCommAddress());
            r.setSerialNumber(d.getSerialNumber());
            r.setGatewayId(d.getGatewayId());
            r.setGatewayName(d.getGatewayName());
            r.setChannelId(d.getChannelId());
            r.setChannelName(d.getChannelName());
            r.setProductId(d.getProductId());
            r.setProductName(d.getProductName());
            r.setEnergyType(d.getEnergyType());
            r.setEnergyTypeName(d.getEnergyTypeName());
            r.setDeviceSystem(d.getDeviceSystem());
            r.setDeviceSystemName(d.getDeviceSystemName());
            r.setCommStatus(d.getCommStatus());
            r.setLastAcqTime(parseDateTime(d.getLastAcqTime()));
            r.setSyncBatchId(batchId);
            r.setLastSyncedAt(now);
            r.setRawJson(safeJson(d));
            rows.add(r);
        }
        for (PlatformDeviceCurrent r : rows) {
            deviceMapper.insert(r);
        }
        return rows.size();
    }

    @Transactional
    public int replaceRelations(List<PlatformAccountDeviceRelation> src, Long batchId, LocalDateTime now) {
        relationMapper.deleteAll();
        if (src.isEmpty()) return 0;
        for (PlatformAccountDeviceRelation rel : src) {
            PlatformAccountDeviceCurrent r = new PlatformAccountDeviceCurrent();
            r.setAccountNumber(rel.getAccountNumber());
            r.setPlatformDeviceId(rel.getPlatformDeviceId());
            r.setSyncBatchId(batchId);
            r.setLastSyncedAt(now);
            relationMapper.insert(r);
        }
        return src.size();
    }

    private String safeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.replace(" ", "T"));
        } catch (Exception ignore) {
            return null;
        }
    }
}
