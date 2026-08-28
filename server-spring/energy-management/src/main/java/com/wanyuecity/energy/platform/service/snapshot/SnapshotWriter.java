package com.wanyuecity.energy.platform.service.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.platform.dto.PlatformAccount;
import com.wanyuecity.energy.platform.dto.PlatformAccountDeviceRelation;
import com.wanyuecity.energy.platform.dto.PlatformDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Writes daily {@code *_snapshot} rows. One row per data set per day;
 * overwrites the day's prior snapshot (UPSERT semantics via REPLACE INTO)
 * so a re-run for the same day never grows the table.
 */
@Slf4j(topic = "platform-sync")
@Service
@RequiredArgsConstructor
public class SnapshotWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public void writeDaily(List<PlatformAccount> accounts,
                           List<PlatformDevice> devices,
                           List<PlatformAccountDeviceRelation> relations,
                           Long batchId) {
        LocalDate today = LocalDate.now();
        try {
            writeOne(today, batchId, "platform_account_snapshot", accounts);
            writeOne(today, batchId, "platform_device_snapshot", devices);
            writeOne(today, batchId, "platform_account_device_snapshot", relations);
        } catch (Exception ex) {
            // Snapshot failure must not break the current mirror; the next daily run will overwrite.
            log.warn("snapshot write failed for batchId={}: {}", batchId, ex.getMessage());
        }
    }

    private void writeOne(LocalDate day, Long batchId, String table, List<?> rows) throws JsonProcessingException {
        if (rows == null) {
            return;
        }
        String json = objectMapper.writeValueAsString(rows);
        jdbc.update(
                "INSERT INTO " + table + " (snapshot_date, sync_batch_id, payload) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE sync_batch_id = VALUES(sync_batch_id), payload = VALUES(payload)",
                Date.valueOf(day), batchId, json);
        log.info("snapshot written: table={}, date={}, batchId={}, rows={}", table, day, batchId, rows.size());
    }
}

