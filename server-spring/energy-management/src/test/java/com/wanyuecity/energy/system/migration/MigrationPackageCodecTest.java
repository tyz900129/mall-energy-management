package com.wanyuecity.energy.system.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MigrationPackageCodecTest {
    @TempDir Path temp;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MigrationPackageCodec codec = new MigrationPackageCodec(objectMapper);

    @Test
    void encryptedPackageCanBeDecryptedAndValidated() throws Exception {
        Path zip = validZip();
        Path encrypted = temp.resolve("backup.wymbackup");
        Path decrypted = temp.resolve("decrypted.zip");

        codec.encrypt(zip, encrypted, "StrongPassword123");
        assertFalse(new String(Files.readAllBytes(encrypted), StandardCharsets.UTF_8).contains("万悦城"));
        codec.decrypt(encrypted, decrypted, "StrongPassword123");

        MigrationPackageCodec.PackageManifest manifest = codec.validate(decrypted, 10_000_000);
        assertEquals(1, manifest.formatVersion());
        assertEquals("41", manifest.schemaVersion());
        assertEquals(1, manifest.uploadFileCount());
    }

    @Test
    void wrongPasswordNeverProducesAUsablePackage() throws Exception {
        Path encrypted = temp.resolve("backup.wymbackup");
        codec.encrypt(validZip(), encrypted, "StrongPassword123");
        BizException error = assertThrows(BizException.class,
                () -> codec.decrypt(encrypted, temp.resolve("wrong.zip"), "WrongPassword123"));
        assertTrue(error.getMessage().contains("密码错误") || error.getMessage().contains("损坏"));
        assertFalse(Files.exists(temp.resolve("wrong.zip")));
    }

    @Test
    void checksumMismatchIsRejected() throws Exception {
        Path zip = temp.resolve("bad.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "database.sql.gz", "changed".getBytes(StandardCharsets.UTF_8));
            entry(out, "portable-secrets.json", "{}".getBytes(StandardCharsets.UTF_8));
            entry(out, "manifest.json", objectMapper.writeValueAsBytes(manifest()));
            entry(out, "checksums.sha256", ("0".repeat(64) + "  database.sql.gz\n").getBytes(StandardCharsets.UTF_8));
        }
        assertThrows(BizException.class, () -> codec.validate(zip, 10_000_000));
    }

    private Path validZip() throws Exception {
        Path zip = temp.resolve("plain.zip");
        Map<String, String> checksums = new LinkedHashMap<>();
        byte[] database = "compressed database placeholder".getBytes(StandardCharsets.UTF_8);
        byte[] upload = "photo".getBytes(StandardCharsets.UTF_8);
        byte[] secrets = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] manifest = objectMapper.writeValueAsBytes(manifest());
        checksums.put("database.sql.gz", sha(database));
        checksums.put("portable-secrets.json", sha(secrets));
        checksums.put("uploads/2026/photo.jpg", sha(upload));
        checksums.put("manifest.json", sha(manifest));
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "database.sql.gz", database);
            entry(out, "portable-secrets.json", secrets);
            entry(out, "uploads/2026/photo.jpg", upload);
            entry(out, "manifest.json", manifest);
            out.putNextEntry(new ZipEntry("checksums.sha256"));
            codec.writeChecksums(out, checksums);
            out.closeEntry();
        }
        return zip;
    }

    private MigrationPackageCodec.PackageManifest manifest() {
        return new MigrationPackageCodec.PackageManifest(1, "万悦城后勤管理系统", "0.1.0", "41",
                "2026-09-15T11:00:00", "admin", 31, 1, 5, Map.of("stall", 100L));
    }

    private void entry(ZipOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    private String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
