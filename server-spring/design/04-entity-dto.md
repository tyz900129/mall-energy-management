# 后端实体 / DTO / Enum 设计

> 分层：`Controller → ApplicationService → DomainService → Repository/Mapper`（文档第 52 节）。  
> 命名规范：Entity = 表名驼峰；DTO = `XxxDTO`；Command = `XxxCmd`；Query = `XxxQry`；VO = `XxxVO`。  
> 严禁：Controller 直写 SQL；Request/Response/Entity 混用；AccountNumber 用 JS number / Java long（文档第 52 节）。

## 1. 平台包 `platform`

### Adapter 层（与源平台解耦）
```java
public interface PlatformAdapter {
    LoginResult login();
    List<PlatformAccountDTO> fetchAccounts();
    List<PlatformDeviceDTO> fetchDevices();
    List<PlatformAccountDeviceDTO> fetchAccountDeviceRelations();
    List<EnergyDailyDTO> fetchDailyEnergy(LocalDate from, LocalDate to);
    List<EnergyMonthlyDTO> fetchMonthlyEnergy(int year, int month);
}
```

### DTO（来自源平台原始 JSON，不做业务转换）
| DTO | 关键字段 |
|---|---|
| `LoginResult` | sessionId, cookieHeader, token, expiresAt |
| `PlatformAccountDTO` | id, accountNumber, accountName, userName, contactName, mobilePhone, balance, arrears, status, spaceId, rawJson |
| `PlatformDeviceDTO` | deviceId, deviceName, commAddress, serialNumber, gatewayId, gatewayName, channelId, channelName, productId, productName, energyType, energyTypeName, deviceSystem, deviceSystemName, commStatus, lastAcqTime, rawJson |
| `PlatformAccountDeviceDTO` | accountNumber, deviceId |
| `EnergyDailyDTO` | deviceId, identifier, statDate, usageValue, unit, dataStatus |
| `EnergyMonthlyDTO` | deviceId, identifier, year, month, usageValue, unit |

### Entity（Current 镜像 = 平台数据的本地视图）
- `PlatformConfig`：单行记录（id=1）
- `PlatformAccountCurrent` ↔ `platform_account_current`
- `PlatformDeviceCurrent` ↔ `platform_device_current`
- `PlatformAccountDeviceCurrent` ↔ `platform_account_device_current`
- `PlatformSyncBatch` ↔ `platform_sync_batch`

### Service
- `CredentialCryptoService`：AES-256-GCM encrypt/decrypt，从 `PLATFORM_CREDENTIAL_SECRET` 派生 32 字节 key
- `PlatformConfigService`：CRUD + `testConnection()`（文档第 10.2）
- `PlatformSyncService`：
  - `runSync(triggerType, userId)` → 单一入口
  - 内部：分布式锁 → 创建 Batch → 拉三类数据 → 写暂存 → 事务切换 Current → 生成 Snapshot → 治理 → 验证待完成流程 → Batch SUCCESS
  - 失败时 Batch FAILED，上一批 Current 保持不变

### Controller
- `PlatformConfigController`：`GET/PUT /api/v1/platform/config`
- `PlatformSyncController`：`POST /api/v1/platform/test-connection`、`POST /api/v1/platform/sync`、`GET /api/v1/platform/sync-logs`
- `PlatformDataController`（只读）：`GET /api/v1/platform/accounts`、`/devices`、`/accounts/{n}/devices`、`/merchant/{code}`

## 2. 系统 / 安全包

### Entity
- `SysUser`（含 passwordHash, status, lastLoginAt）
- `SysRole`（code 唯一：SUPER_ADMIN / SYSTEM_ADMIN / APPLICANT / DEVICE_VERIFIER / PLATFORM_OPERATOR / AUDITOR / VIEWER）
- `SysUserRole`、`SysMenu`、`SysRoleMenu`
- `AuditLog`

### DTO
- `LoginRequest { username, password }`、`LoginResponse { token, user, roles, menus }`
- `CurrentUserVO { id, username, displayName, roles, menus }`

### Service / Controller
- `AuthController`：`/api/v1/auth/login`、`/logout`、`/me`
- `SecurityConfig`：JWT + RBAC 注解 `@PreAuthorize("hasRole('PLATFORM_OPERATOR')")`

## 3. 商户 / 铺位 / 设备包

### Entity
- `Merchant`（merchantCode VARCHAR(6)）
- `Stall`（stallCode 唯一）
- `StallDevice`

### DTO / VO
- `MerchantVO { merchantCode, merchantName, contactName, contactPhone, sourcePlatformAccountId, codeMatchStatus }`
- `StallVO`、`StallDeviceVO`
- `DeviceSearchQry { commAddress?, serialNumber?, name? }`

### Service
- `MerchantCodeParser`（放在 common）：`parse(String accountName) → ParseResult { code, source, status }`
  - 正则：`(?<!\d)\d{6}(?!\d)`
  - 状态：`AUTO_MATCHED`（一个匹配）/ `AMBIGUOUS`（多个匹配）/ `NOT_FOUND`（无匹配）
  - 同步时写入 `merchant_code_source` / `merchant_code_match_status`
- `StallService`、`DeviceService`

### Controller
- `StallController`：文档第 44 节全套
- `MerchantController`（轻）：`GET /api/v1/merchants/{code}`

## 4. 流程 / 核验 / 通知 / 对账（Phase 3+）

### Workflow 状态枚举
```java
public enum WorkflowStatus {
  DRAFT, SUBMITTED, FIELD_CHECK_PENDING, FIELD_CHECKING,
  FIELD_VERIFIED, FIELD_EXCEPTION, WAITING_PLATFORM_CHANGE,
  PLATFORM_VERIFYING, VERIFIED, PARTIAL_MATCH, EXCEPTION,
  REJECTED, CANCELLED, COMPLETED
}
```

### WorkflowType 枚举
```java
public enum WorkflowType { NEW_MERCHANT, EXISTING_MERCHANT_ADD_STALL, MERCHANT_OFFBOARDING }
```

### OperationType（workflow_device）
```java
public enum OperationType { BIND, UNBIND, KEEP }
```

### ChangeType（workflow_expected_change）
```java
public enum ChangeType { DEVICE_BIND, DEVICE_UNBIND, ACCOUNT_DISABLE }
```

### VerifyStatus
```java
public enum VerifyStatus { PENDING, VERIFIED, PARTIAL_MATCH, EXCEPTION, CONFLICT, WRONG_TARGET_ACCOUNT }
```

### Entity / DTO 关键字段
- `Workflow` 字段同 `workflow` 表
- `WorkflowDevice`：含 6 个 snapshot 字段（device_name/energy_type/comm_address/serial_number/gateway_name/channel_name）
- `ExpectedChange`：含 expected_effective_time / verify_status
- `FieldVerificationTask`：`token VARCHAR(64) UNIQUE, tokenExpireAt`
- `FieldVerificationDevice`：含 onsite_meter_number / onsite_serial_number / onsite_reading / reading_unit / device_matched / serial_matched / address_matched
- `Attachment`：`photoType` 枚举（LOCATION / METER_FACE / SERIAL_NUMBER / OTHER）
- `DataException`：`exceptionType` 至少包含文档第 36 节列出的 11 种
- `WebhookConfig`：`provider` WECOM/DINGTALK/FEISHU/EMAIL/SMS；`url_ciphertext`, `secret_ciphertext` 加密
- `WebhookLog`：`requestPayload`, `responsePayload` TEXT

### 关键 Service
- `WorkflowService`：状态机推进
- `WorkflowPrecheckService`：三类型 precheck 业务（文档第 21-23 节）
- `FieldVerificationService`：现场核验 + 状态机推进
- `NotificationService` + `NotificationProvider` 接口 + `WeComWebhookProvider` 第一期实现
- `ReconciliationService`：同步成功后比对 ExpectedChange vs Current（文档第 33 节）
- `ExceptionDetectorService`：Multi-Account / Unauthorized Change / Mismatch 检测
- `EnergyAnomalyDetectorService`（后期）

## 5. 通用 / 异常 / 工具

```java
// 统一响应
public record ApiResponse<T>(int code, String message, T data) {
  public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, "success", data); }
}

// 业务异常
public class BizException extends RuntimeException {
  private final int code; // 与 ApiResponse.code 对齐
  public BizException(int code, String message) { super(message); this.code = code; }
}

// 全局异常处理：把 BizException / ValidationException / 未知异常都映射为 ApiResponse
```

## 6. 实体 ↔ 数据库 映射约定
- MyBatis-Plus `@TableName` / `@TableId` / `@TableField(fill = FieldFill.INSERT)`
- 自定义填充 `MetaObjectHandler` 写入 `createdAt` / `updatedAt`
- 逻辑删除：仅 `audit_log` 不使用；其它需要软删的表加 `deleted TINYINT`

