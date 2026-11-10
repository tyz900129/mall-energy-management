# 基线说明：deployed-20261109

> 本分支 `baseline/deployed-20261109` 不是可发布的代码基线，而是一个**对照锚点**：
> 用来回答"2026-11-09 23:53 上线的那一版，到底包含哪些代码/迁移"。
> 分支内容不保证可编译（见第 6 节），**不要基于本分支提交修复或部署**。

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
3. 本说明文档。

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
  1. 泛型全部擦除，出现大量裸 `List` / `Map`。
  2. `deptSnapshot` 等少数位置被 CFR 合并重排，写法可读但语义可疑（例如把赋值塞进 `d.put(...)` 的实参里），**不可当作正确实现**。
  3. 注解写法（`value={"/x"}`）、局部变量名、注释、空行均已失真。

## 5. 与 `main` 的已知差异清单

| # | 差异 | 说明 |
| --- | --- | --- |
| 1 | 迁移 V98–V102 | `main` 缺这 5 个文件；线上库已应用到 102。本分支补入。 |
| 2 | `InspectionService` | 线上含 `Owner` record 与 `resolveOwner` / `deptOwner` / `deptSnapshot` / `userRow` / `deptFullPath`，`insertTask` 签名带 `Owner`；`main` 均无。 |
| 3 | `InspectionController` | 线上多 3 个接口：`POST /{id}/claim`、`GET /owner-options`、`PUT /{id}/reassign`；`main` 无。 |
| 4 | `inspection_plan` / `inspection_task` 的归属字段 | 线上 SQL 读写 `owner_type` / `owner_id` / `owner_name` / `owner_dept_*` / `owner_ancestors` / `claimed_by` / `claimed_at` / `assignee_status` / `target_mode` / `claim_mode`，由 V102 提供；`main` 代码不涉及。 |
| 5 | `GlobalExceptionHandler` | 线上含 `handleResponseStatus`、`handleNoResourceFound`、`buildStatusResponse`（`ResponseStatusException` / `NoResourceFoundException` 保留原始状态码）；`main` 不含，本地工作区有等价但未提交的实现。本分支为 diff 锚点，未补入该类。 |
| 6 | V103 | `V103__task_center_foundation_p1.sql` 仅存在于本地工作区，线上 war 与线上库都没有，不属于本基线。 |

除上述 inspection 相关类与 `GlobalExceptionHandler` 外，线上 war 与 `main` 编译出的其余 class 在 2026-11-10 的逐 class 比对中一致（该结论来自修复包构建时的全量 sha256 比对）。

## 6. 可构建性（如实记录）

本分支**不能**通过 `mvn -B -DskipTests compile`。实测 4 处编译错误，全部位于反编译的 `InspectionService.java`，原因均为泛型擦除后裸 `List` 上的增强 for 循环：

```
InspectionService.java:[128,24] incompatible types: java.lang.Object cannot be converted to java.util.Map   // plans() 中 for (Map row : rows)
InspectionService.java:[241,26] incompatible types: java.lang.Object cannot be converted to java.util.Map   // generate() 中 for (Map c : children)
InspectionService.java:[550,22] incompatible types: java.lang.Object cannot be converted to java.util.Map   // sendReminders() 中 for (Map x : due)
InspectionService.java:[554,22] incompatible types: java.lang.Object cannot be converted to java.util.Map   // sendReminders() 中 for (Map x : overdue)
```

**要让本分支真正可构建，还差**：

1. 上述 4 处补回泛型或加显式转型（如 `for (Map row : (List<Map>) rows)`）——纯类型标注，不改运行语义。
2. 人工复核 CFR 合并产生的可疑写法（至少 `deptSnapshot` 一处）。
3. 补入 `GlobalExceptionHandler` 的线上等价实现（否则线上 401/403/404 的错误码语义与 `main` 不同）。

按约定，本次**没有**为"能编译"去改这些代码，也没有补依赖或调整包结构。

## 7. 使用约定

- 允许：与 `main` 或 `fix/*` 分支做 diff、回答"线上这版有没有某段逻辑"、核对迁移与库状态。
- 禁止：直接在本分支上改代码并发布；把反编译文件的内容当作原始实现引用；删除本分支的 5 个迁移文件（它们是线上 Flyway 校验的前提）。
