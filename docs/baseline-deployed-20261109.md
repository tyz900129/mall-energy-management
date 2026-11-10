# 基线说明：deployed-20261109

> 本分支 `baseline/deployed-20261109` 不是可发布的代码基线，而是一个**对照锚点**：
> 用来回答"2026-11-09 23:53 上线的那一版，到底包含哪些代码/迁移"。
> 2026-11-10 已把本分支补到**可通过 `mvn -B -DskipTests compile`**（见第 6 节），
> 但这只是"能编译、行为对齐"，仍**不要基于本分支提交修复或部署**。

## 1. 线上产物指纹（本次基线的依据）

| 项 | 值 |
| --- | --- |
| 线上 war | `/home/admin/energy-deploy/energy-management.war` |
| war 大小 | 185,329,931 字节 |
| war sha256 | `cca2127f7007f817ff5e3e9d6af3056ad7d18f9278a2d3471d53ae1fc1b37722` |
| war 修改时间 | 2026-11-09 23:53:18 (+08:00) |
| 部署镜像 tag | `mall-energy-backend:s6-20261109`（镜像 ID `f1a988942d5f`） |
| 镜像构建时间 | 2026-11-09 23:53:20 (+08:00) |
| 容器启动时间 | 2026-11-09T15:53:23Z（= 23:53:23 +08:00），`Started EnergyApplication in 7.01 seconds` |
| Flyway 已应用最大版本 | **102**（`flyway_schema_history` 共 102 条成功记录，失败 0） |
| 备份 war | `/home/admin/energy-deploy/backups/energy-management.war.20261110-173544` |
| 后续切换 | 2026-11-10 17:42 线上已切到 `mall-energy-backend:fix-3issues-20261110`（镜像 ID `7fe5d37265d0`，war sha256 `adbb040d…c8c4`），本基线描述的是**切换前**的版本 |

线上构建来源不可追溯到任何 git commit：11-09 那一版由某个包含 V98–V102 且已含巡检"任务归属/认领/改派"能力的工作区构建而成，该工作区快照未入库。

## 2. 本分支提交的内容

1. `server-spring/energy-management/src/main/resources/db/migration/V98–V102` 共 5 个迁移文件（从线上 war 逐字节取出）。
2. `com/wanyuecity/energy/inspection/InspectionService.java`、`InspectionController.java`（CFR 0.152 反编译还原，文件头有醒目警告）。
3. `com/wanyuecity/energy/core/GlobalExceptionHandler.java`（2026-11-10 按线上 class 补入等价实现）。
4. 本说明文档。

## 3. V98–V102 与线上的一致性核对

sha256 取自线上 war 内 `WEB-INF/classes/db/migration/*`；Flyway checksum 取自线上库 `flyway_schema_history`，并按 Flyway 算法（逐行 UTF-8 CRC32，不含换行符）在本地重算比对。

| 版本 | 文件 | war 内 sha256（前 16 位） | 本分支文件 sha256（前 16 位） | flyway 记录 checksum | 本地重算 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 98 | V98__org_department_tree_s1.sql | 92dbfd06b081d94b | 92dbfd06b081d94b | -1038718237 | -1038718237 | 一致 |
| 99 | V99__dept_permission_policy_s2.sql | 18d0b043cf111425 | 18d0b043cf111425 | 614272663 | 614272663 | 一致 |
| 100 | V100__dept_org_manage_s3.sql | 326752293a25ca8d | 326752293a25ca8d | -332928721 | -332928721 | 一致 |
| 101 | V101__role_as_post_s4.sql | 18bc6511aa0e01f4 | 18bc6511aa0e01f4 | 1715936111 | 1715936111 | 一致 |
| 102 | V102__task_center_owner_s5.sql | a0dc977d86228b12 | a0dc977d86228b12 | -1719858231 | -1719858231 | 一致 |

结论：5 个文件的字节内容与线上 war 完全相同，且 Flyway checksum 与线上历史记录逐个相等。把这 5 个文件放回 `db/migration` 后，`validate-on-migrate` 不会出现 `Detected applied migration not resolved locally`。

> 行尾说明：仓库未配置 `.gitattributes` 且 `core.autocrlf=true`，在 Windows 上重新检出时这些文件可能被转成 CRLF，届时磁盘上的 sha256 会变，但 Flyway checksum 是逐行 CRC32、不含换行符，**不受影响**。若要让 sha256 也稳定一致，需要给 `*.sql` 单独加 `.gitattributes`（`*.sql text eol=lf`），本次未改仓库配置。

## 4. 反编译还原的范围与质量

- 工具：CFR 0.152，输入为线上 war 内的 `InspectionService.class`、`InspectionService$Owner.class`、`InspectionController.class`（同时提供三个类作为额外 classpath）。
- 产物：`InspectionService.java`（705 行）、`InspectionController.java`（211 行），括号配对完整，无 `goto`/`label`/字节码注释等不可读结构。
- 内部类 `Owner` 被还原为 `InspectionService` 末尾的 `private record Owner(...)`，位置和字段与线上一致。
- 可读性：整体可读，能清晰对应方法职责（`resolveOwner` / `deptOwner` / `deptSnapshot` / `userRow` / `deptFullPath` / `claim` / `ownerOptions` / `reassign`）。
- 已知失真：
  1. 泛型全部擦除，出现大量裸 `List` / `Map`（编译所需的 4 处已补回 `List<Map<String, Object>>`）。
  2. `deptSnapshot` 被 CFR 合并重排（把赋值塞进 `d.put(...)` 的实参里）。2026-11-10 已按 javap 字节码复核并拆回两条语句，**这一处已不是失真项**，详见第 6 节。
  3. 注解写法（`value={"/x"}`）、局部变量名、注释、空行均已失真，且无法从字节码还原。

## 5. 与 `main` 的已知差异清单

| # | 差异 | 说明 |
| --- | --- | --- |
| 1 | 迁移 V98–V102 | `main` 缺这 5 个文件；线上库已应用到 102。本分支补入。 |
| 2 | `InspectionService` | 线上含 `Owner` record 与 `resolveOwner` / `deptOwner` / `deptSnapshot` / `userRow` / `deptFullPath`，`insertTask` 签名带 `Owner`；`main` 均无。 |
| 3 | `InspectionController` | 线上多 3 个接口：`POST /{id}/claim`、`GET /owner-options`、`PUT /{id}/reassign`；`main` 无。 |
| 4 | `inspection_plan` / `inspection_task` 的归属字段 | 线上 SQL 读写 `owner_type` / `owner_id` / `owner_name` / `owner_dept_*` / `owner_ancestors` / `claimed_by` / `claimed_at` / `assignee_status` / `target_mode` / `claim_mode`，由 V102 提供；`main` 代码不涉及。 |
| 5 | `GlobalExceptionHandler` | 线上含 `handleResponseStatus`、`handleNoResourceFound`、`buildStatusResponse`（`ResponseStatusException` / `NoResourceFoundException` 保留原始状态码）；`main` 不含。2026-11-10 已在本分支按线上字节码补入等价实现（见第 6 节）。 |
| 6 | V103 | `V103__task_center_foundation_p1.sql` 仅存在于本地工作区，线上 war 与线上库都没有，不属于本基线。 |

除上述 inspection 相关类与 `GlobalExceptionHandler` 外，线上 war 与 `main` 编译出的其余 class 在 2026-11-10 的逐 class 比对中一致（该结论来自修复包构建时的全量 sha256 比对）。

## 6. 可构建性（2026-11-10 已补齐）

### 6.1 本次改动（仅为"让基线可编译"，不是功能变更）

| 文件 | 改动 | 性质 |
| --- | --- | --- |
| `InspectionService.java` | `plans()` / `generate()` / `sendReminders()` 中 4 处裸 `List` → `List<Map<String, Object>>` | 纯类型标注，擦除后字节码不变 |
| `InspectionService.java` | `deptSnapshot()` 拆回"先构造赋值、再 put"两条语句，`d` 声明为 `Map<String, Object>` | 恢复原始语义（见 6.2） |
| `InspectionService.java` / `InspectionController.java` | 文件头标注重写为 UTF-8，并按"已复核 / 依据 / 仍未确认"细化 | 仅注释 |
| `GlobalExceptionHandler.java` | 补入 `handleResponseStatus` / `handleNoResourceFound` / `buildStatusResponse` | 与线上 class 行为等价（见 6.3） |

### 6.2 `deptSnapshot` 复核结论

CFR 原输出：

```java
d.put("path", ((d = new LinkedHashMap<String, Object>((Map)l.get(0))).get("ancestors") == null ? "/" : String.valueOf(d.get("ancestors"))) + id + "/");
```

线上 `javap -p -c` 字节码控制流：

```
36 new LinkedHashMap / 50 invokespecial <init>(Map) / 53 astore_3
54 aload_3（put 的 receiver） / 55 ldc "path" / 58-62 d.get("ancestors") / 67 ifnonnull
85 String.valueOf / 89 makeConcatWithConstants(String,Long) / 94 Map.put / 100 aload_3 / 101 areturn
```

**判断依据**：`53: astore_3` 把新建的 `LinkedHashMap` 落到局部变量 `d`，随后 `54: aload_3` 才把它作为 `put` 的 receiver。因此原始源码必然是两条语句；CFR 的合并写法会在 `d` 尚未赋值时以 null 调用 `put`（NPE），与线上行为不等价。另外线上字节码是 `invokeinterface Map.get/Map.put`，说明 `d` 的静态类型是 `Map` 而不是 `LinkedHashMap`（后者会发 `invokevirtual`）。

**复核后**：新编译出的 `deptSnapshot` 字节码与线上逐指令一致（仅常量池编号不同）。线上该方法的实际行为是：`path = (ancestors 为 null ? "/" : ancestors) + id + "/"`，返回包含 `id/name/ancestors/path` 的 `LinkedHashMap`。

**仍不确定的点**：局部变量名 `d`、声明位置、泛型参数（字节码已擦除）无法从字节码确认；这不影响可观察行为。

### 6.3 `GlobalExceptionHandler` 与线上的一致性

- `javap -p` 成员列表（方法与签名）与线上 `GlobalExceptionHandler.class` **完全一致**。
- `buildStatusResponse` 的 `javap -p -c` 字节码与线上**逐指令一致**（仅常量池编号不同）。
- 行为约定：HTTP 状态码原样保留（400/401/403/404/405 等 4xx 不降级，`NoResourceFoundException` 归 404）；响应体 `code` 仅 401/403/404/400 显式对应，其余 4xx 记 400、5xx 记 500；5xx 文案固定 `Internal server error`，异常只进日志。
- 唯一差异：线上 logger 由 Lombok `@Generated` 生成，本文件用 `@Slf4j`，两者字节码等价。

### 6.4 验收结果

| 项 | 结果 |
| --- | --- |
| `mvn -B -DskipTests compile` | **BUILD SUCCESS**（375 个源文件，javac release 17） |
| `mvn -B test` | **Tests run: 68, Failures: 0, Errors: 0, Skipped: 0**（全部为纯单测，不需要数据库） |
| `InspectionService` javap 成员列表 vs 线上 | **一致** |
| `InspectionController` javap 成员列表 vs 线上 | **一致**（含 `claim` / `ownerOptions` / `reassign`） |
| `InspectionService$Owner` javap vs 线上 | **一致** |
| `deptSnapshot` / `buildStatusResponse` 字节码 | 逐指令一致 |

编译与测试在 220 的临时目录 `/tmp/base-20261110` 内用 `maven:3.9-eclipse-temurin-17` 容器执行，未触碰线上容器与运行态。

### 6.5 本分支现在的可信程度

- **可直接当线上等价物使用**：`InspectionService` / `InspectionController` / `InspectionService$Owner` / `GlobalExceptionHandler` 的**结构、方法签名与关键方法字节码**；V98–V102 迁移文件（逐字节一致）。
- **只能做参考**：反编译文件里的局部变量名、注释、注解书写形态；未被复核的其余方法体（未做全文件逐条字节码比对，仅比对成员列表与关键方法）。
- **不能用于发布**：本分支没有 `main` 的其它修复，也不包含 V103；直接部署会退回到 11-09 的功能水平。

## 7. 使用约定

- 允许：与 `main` 或 `fix/*` 分支做 diff、回答"线上这版有没有某段逻辑"、核对迁移与库状态。
- 禁止：直接在本分支上改代码并发布；把反编译文件的内容当作原始实现引用；删除本分支的 5 个迁移文件（它们是线上 Flyway 校验的前提）。
