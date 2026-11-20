# 二维码批量导出 + 商管自助下载 + 精臣 B1 打印 Implementation Plan

## Repository Research

### 现有二维码资源（两套，互不干扰）

| 类型 | 表 | token 字段 | URL | 尺寸 | 现有下载 |
|---|---|---|---|---|---|
| 铺位 | `stall` | `operation_qr_token`（V70，永久不变） | `/operation/stall/{token}` | 520×520 | 单张 PNG（OperationMerchantResponsibility） |
| 仓库/巡检设备 | `inspection_equipment` | `qr_token`（V47） | `/inspection/equipment/{token}` | 420×420 | 单张 PNG（InspectionEquipment） |

生成方式统一：ZXing `QRCodeWriter` + `MatrixToImageWriter.writeToStream("PNG")`，直接返回 byte[]。

### 现有商管责任范围
`stall` 表有 `manager_user_id` → `sys_user.id`，`OperationMerchantResponsibilityService.page()` 已按当前登录用户过滤：
- 商管（role.manager）：只看到 `s.manager_user_id = ?` 的铺位
- 运营（role.operation）：看所有

### 现有权限码
- `stall.menu` 菜单可见
- `operation.merchant.view` 责任商管管理页
- `stall.device.view` 计量设备绑定

**inspection_equipment 当前没有"谁负责"的字段**——它只归 category 管。商管自助下载"自己负责的仓库二维码"需要先定义"负责关系"。有两条路：
- ① 在 inspection_equipment 加 `manager_user_id`（与 stall 表对齐，推荐）
- ② 商管只下载自己负责铺位关联的设备（如果 stall ↔ equipment 关联存在的话）

### 打印/导出现状
- **零批量下载**：后端无 zip 接口，前端全是单张 `<a download>`
- **零打印模板**：没有 HTML print media CSS
- **零精臣集成**

### 精臣 B1 WebUSB 可行性
✅ **libniimai** npm 包（支持 WebUSB / WebBluetooth 两种传输层）已验证可用。精臣 B1 标签纸 25×50mm 是最常见规格，`libniimai` 有现成模板引擎把 canvas 渲染成位图后下发。

**限制**：
- 仅 Chrome / Edge 桌面版（WebUSB），**Firefox 不支持**，HTTPS 或 localhost 才生效
- 首次用要手动授权 USB 设备
- **可选替代**：精臣官方 Windows 云打印客户端（后台常驻 HTTP 服务 + 本地打印机队列），调用更稳但需要每台 Windows 电脑装软件

## 需求拆解（A/B/C/D 四件套）

### A. 管理端批量下载
后端新接口：

```
GET /api/v1/operation/merchants/qr-batch?stallIds=1,2,3,...   → 铺位二维码 zip
GET /api/v1/inspection/equipment/qr-batch?ids=1,2,3,...      → 仓库二维码 zip
```

后端用 `java.util.zip.ZipOutputStream` 把每张 PNG 命名为 `{编码}-{名称}-qr.png` 打包。

前端新页面（或两个现有页面加"批量导出"按钮）：
- 铺位管理页（已存在 `/stall`）顶部加「批量导出二维码」
- 巡检设备页（已存在 `/inspection/equipment`）顶部加「批量导出二维码」
- 弹多选框或按筛选条件自动全选

### B. 商管自助下载（后端公开接口 + 新权限码）
新增权限码：`stall.qr.self.view`、`inspection.equipment.qr.self.view`

新后端接口：
```
GET /api/v1/operation/merchants/my-stalls-qr         → 当前登录商管自己负责的铺位二维码 zip
GET /api/v1/inspection/equipment/my-qr                → 当前登录商管自己负责的仓库二维码 zip
```

**关键设计决策**：inspection_equipment 的"谁负责"关系怎么建？
- 方案 B1（推荐）：inspection_equipment 加 `manager_user_id` 字段，和 stall 表对齐。一次性给存量数据按 category → 默认负责人（若 category 有 responsible_dept/responsible_person）批量填，后续由 admin 在台账页分配。
- 方案 B2：通过 stall 间接关联——如果 inspection_equipment 有 `stall_id` 字段则直接用；如果没有则跳过商管自助下载仓库二维码功能（商管还是能自助下载铺位二维码）。

先调研 inspection_equipment 有没有 stall_id → 若没有，用方案 B1。

前端：商管登录后，顶部菜单加「**我的二维码**」入口（`qr-self-service`，权限 `stall.qr.self.view || inspection.equipment.qr.self.view`），里面两个 tab：铺位二维码、仓库二维码，各有「一键下载全部 zip」和逐张预览。

### C. 打印模板设计（两张独立 CSS print 样式）

**核心原则**：二维码图片里只放**必要的识别信息**，不渲染商户名称。商户名称在纸张上、PDF 里是可删/可选的，二维码本身是纯 `url`，扫描后才看完整信息。

#### 铺位二维码标签（A4 8 张）
```
┌─────────────────┐  ┌─────────────────┐
│   ┌───────┐     │  │   ┌───────┐     │
│   │  QR   │     │  │   │  QR   │     │
│   └───────┘     │  │   └───────┘     │
│  A-001  1F      │  │  A-002  1F      │
│  [楼层] [编码]  │  │                  │
└─────────────────┘  └─────────────────┘
```
- 规格：A4 (210×297mm) → 2 列 × 4 行 = 8 张；每张标签区域 ~90×60mm
- 内容：QR 居中（约 45mm 正方形，比 B1 标签纸宽松），下方两行小字**铺位编码 + 楼层**（不用商户名称，因为转让了二维码不变）
- 设计：QR 45mm 放中间，下方文字左对齐，上方留白 5mm 供 B1 撕纸刀位

#### 仓库/巡检设备二维码标签（B1 25×50mm 专用）
```
┌──────────────────────┐
│  ┌───────┐            │
│  │  QR   │  空调外机  │  ← 设备名
│  └───────┘  E-1002    │  ← 设备编码
│              3F 机房  │  ← 安装位置
└──────────────────────┘
   25mm  ×    50mm
```
- 规格：25mm（宽）× 50mm（长），B1 默认装的就是这个规格的标签纸
- QR 18mm 正方形（左下或左中），右边竖排三行小字：**设备名 / 设备编码 / 安装位置**
- 二维码本身**不编码这些文字**——文字只是给"没扫码时人眼看的"辅助，扫码 URL 只存 `/inspection/equipment/{token}`

#### 打印输出方案
- ① **HTML print**：新建 `QrPrintLayout.vue`，`@media print` CSS，浏览器 Ctrl+P，选 A4 / 每页 8 张，**零后端零第三方依赖**，所有人都能用
- ② **PDF 导出**（可选）：后端加 `GET .../qr-pdf` 用 iTextPdf 或 Apache PDFBox 生成，管理端偏好的场景用；这个比较重，可以先不做
- **推荐先做 ①**，够用

### D. 精臣 B1 USB 标签机网页打印

技术栈：`libniimai` npm 包（依赖 WebUSB API）。

核心流程：
```
1. 用户首次点「连 B1 打印机」
2. 浏览器弹出 USB 设备选择器，用户选 NIIMBOT B1
3. 拿到 WebUSB device handle → 建 libniimai printer 实例
4. 用户点某张二维码右侧的「打印」→ 页面渲染好 25×50mm 标签 canvas（内容 = QR + 三行小字）
5. libniimai 把 canvas 位图转换成 B1 协议位图 + 发送打印指令
6. B1 吐出一张 25×50mm 标签
```

**两张页面都需要这套"连打印机 + 打印"按钮**：
- 管理端「批量导出」页面里，每张二维码旁边有「🖨️ 打」
- 商管自助下载「我的二维码」页面里，每张同样有「🖨️ 打」

**兼容降级**：
- 非 Chrome/Edge → 按钮禁用 + 提示「请用 Chrome 或 Edge 打印」
- 未连打印机 → 按钮置灰 + 提示「先连接 B1 打印机」
- **Windows 官方云打印客户端替代**：如果 WebUSB 在现场机器上搞不定，后端提供 `/api/v1/.../qr-base64` 返回标签位图 base64，本机 Python 脚本（调用精臣官方 SDK）转存精臣格式文件送打印队列。这个是备选路线，优先走 WebUSB。

## Files and Modules（预估）

### 后端 Java
- **新增** `OperationQrBatchController.java`：铺位二维码批量 zip 接口 + 商管自助下载 zip 接口
- **新增** `InspectionEquipmentQrBatchController.java`：设备二维码批量 zip + 商管自助 zip
- **修改** `InspectionEquipmentService.java`：加 `managerUserId` 过滤、分页接口补返回 qr_token
- **Flyway** 新迁移：inspection_equipment 加 `manager_user_id`（B1 方案）
- **权限码**：`stall.qr.self.view`、`inspection.equipment.qr.self.view`

### 前端 Vue
- **新增** `QrBatchExport.vue` 管理端批量导出页（tab：铺位 / 仓库，多选 + zip 下载 + B1 单张打印）
- **新增** `MyQrSelfService.vue` 商管自助下载页（tab：铺位 / 仓库，一键 zip + 单张 B1 打印）
- **新增** `QrPrintLayout.vue` 纯 print 样式组件（被两个页面引入）
- **新增** `useNiimbotPrinter.ts` composable（封装 libniimai 连接 + 打印，降级处理）
- **router/menu.ts**：加 `qr-batch-management` 和 `qr-self-service` 两个菜单
- **menu.ts** 权限码同步加

### 第三方依赖
- `libniimai`（npm，WebUSB + 精臣协议）

## Implementation Steps（依赖顺序）

1. 调研 inspection_equipment 表结构（有没有 stall_id / manager 字段）→ 确定 B1 还是 B2
2. Flyway 迁移（如果要加 manager_user_id）
3. 后端 batch zip 接口 + 商管 self 接口 + 权限码注册
4. 前端 npm 装 `libniimai`
5. 前端 composable `useNiimbotPrinter.ts`（封装连接/打印/降级）
6. 前端 `QrPrintLayout.vue` print CSS
7. 管理端 `QrBatchExport.vue` + 路由菜单
8. 商管 `MyQrSelfService.vue` + 路由菜单
9. 类型检查 + context 校验 + 部署
10. 商管权限补授（V116 迁移）

## Dependencies and Considerations

- **zip 流式打包**：`ZipOutputStream` 直接写 response body，单张 PNG 已在内存里（ZXing 已生成 byte[]），1000 张铺位 = 约 20MB zip，内存安全
- **商管自助下载防越权**：后端接口必须 `WHERE manager_user_id = currentUserId`，不能信任前端传的 id
- **精臣 B1 必须 USB 版**：蓝牙版要切 WebBluetooth，API 完全不同；用户说 USB，按 USB 来
- **B1 标签纸规格**：默认 25×50mm，设计模板按这个；管理员如果换了规格（如 15×40mm）需要前端提供规格切换
- **生产部署**：HTTPS + WebUSB 生效；本地开发 localhost 也生效

## Validation

- 功能：选 5 个铺位 → 点 zip 下载 → 解压 → 每张 PNG 能扫码进对应 stall
- 功能：商管登录 → 自助下载 → 只拿到自己 manager_user_id 匹配的铺位
- 打印：Chrome + USB B1 → 连成功 → 点打印 → B1 吐出标签、文字对齐
- 降级：Firefox → 打印按钮禁用有提示
- 类型检查 `vue-tsc --noEmit` 零错误
- `check-context.py` 零错误

## Risks

| 风险 | 处理 |
|---|---|
| inspection_equipment 无 manager 字段，无法商管自助下载仓库二维码 | 加 manager_user_id（B1），存量数据批量填 NULL 不影响 |
| libniimai 在某些 Chrome 版本或 Windows 10 机器上 WebUSB 权限问题 | 降级提示 + 提供 Python 精臣 CLI 脚本备选路线 |
| zip 1000 张时后端响应超时 | Spring Boot 改 `async-request-timeout` 或用 DeferredResult；目前单张 PNG 约 20KB，100 张 2MB 无压力，1000 张也 OK |
| 二维码 URL 泄露 / token 被别人用 | stall.operation_qr_token 本身就公开（打印贴在墙上），inspection_equipment.qr_token 也是；token 泄露的风险已存在，不是新引入 |
| 生产 HTTPS 环境下 WebUSB 第一次用要手动授权 | 正常行为，有一次性体验 |

## 开放问题（需要你定）

1. **inspection_equipment 要不要加 manager_user_id？** 如果要商管自助下载仓库二维码，必须加。如果你说"商管只管铺位二维码、设备码只有运维管"，那这条可跳过，简单很多。
2. **打印模板 A4 8 张 vs 其他方案**——你现场用的标签纸规格是什么？25×50mm 是 B1 标配；如果有更大的（如 B21 32×64mm），模板设计不一样。
3. **方案 D 精臣 B1**——现场有没有一台 Chrome/Edge 桌面机+USB 线？还是说想用手机扫了打？手机走 WebBluetooth（蓝牙版 B1），流程差不多但 API 不同，需确认是 USB 版还是蓝牙版。

---

**当前推荐优先级**：A（管理端批量 zip）> B（商管自助 zip）> C（打印模板 HTML print）> D（精臣 B1 WebUSB，可选）。ABCD 四件套可以分批交付：A+B 一周内，C 两天，D 需要现场实测才稳。
