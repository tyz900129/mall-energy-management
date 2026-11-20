# 物业巡场菜单改名 + 整改日志区分 Implementation Plan

## Repository Research

### 现状
系统有两套巡场模块：
- **patrol**（V79，旧巡场）：菜单名"巡场管理"，前端 6 个页面。实际业务是**物业巡场管理**，用户要求改名。
- **ops-patrol**（V82，营运巡场管理）：菜单名正确，不动。

### 整改日志现状
`patrol_issue_log` 和 `ops_patrol_issue_log` 都有 `action` 字段，取值：

| action | patrol 模块含义 | ops-patrol 模块含义 |
|---|---|---|
| CREATE | 问题上报（**问题发现**） | 创建问题（**问题发现**） |
| ASSIGN / TRANSFER | 分派/转派整改部门 | 指派/变更跟进人 |
| FEEDBACK_CLOSE / RECTIFY | 部门整改反馈（**整改提交**） | 提交整改（**整改提交**） |
| REVIEW_PASS / REVIEW_REJECT | 复核通过/驳回 | 复核通过/驳回 |
| OVERDUE_NOTICE / UNASSIGNED_REMIND | 系统提醒 | 逾期提醒 |

**两个模块前端日志展示都只用 `<b>{{actionLabel}}</b>` 平铺一条文字，没有视觉标签区分**——问题发现和整改提交长得一模一样，用户一眼分辨不出。

### 影响文件
- `server-spring/energy-admin/src/router/menu.ts`（菜单标题）
- `server-spring/energy-admin/src/views/PatrolIssues.vue`（patrol 模块问题日志）
- `server-spring/energy-admin/src/views/OpsPatrolIssues.vue`（ops-patrol 模块问题日志）

后端不动——日志 action 语义已完备，只是前端没做区分展示。

## Files and Modules

- `src/router/menu.ts:137`：patrol-management 菜单标题 "巡场管理" → "物业巡场管理"；children 里 "巡场问题/巡场记录/巡场点位" 对应加 "物业" 前缀（保持菜单标题与业务名一致）。
- `src/views/PatrolIssues.vue:86-92`：日志区域加 `<el-tag>` 按 action 类型分色。
- `src/views/OpsPatrolIssues.vue:116-119`：时间线日志加 `<el-tag>` 按 action 类型分色。
- `src/router/index.ts:80-85`：后台 patrol 子路由 meta.title 同步改（"巡场问题"/"巡场记录"/"巡场点位" → "物业巡场问题"/"物业巡场记录"/"物业巡场点位"）；移动端 `/patrol/entry` 标题保持"今日巡场"不变（用户操作页，不是管理入口）。

## Implementation Steps

### Step 1: 菜单标题改名

`src/router/menu.ts`：
- patrol-management parent title: `"巡场管理"` → `"物业巡场管理"`
- 子项：
  - `"巡场问题"` → `"物业巡场问题"`
  - `"巡场记录"` → `"物业巡场记录"`
  - `"巡场点位"` → `"物业巡场点位"`
  - 其余子项（今日巡场/整改部门/值班排班/分类字典）不动

`src/router/index.ts`：
- `/patrol/issues` title `"巡场问题"` → `"物业巡场问题"`
- `/patrol/submissions` title `"巡场记录"` → `"物业巡场记录"`
- `/patrol/points` title `"巡场点位"` → `"物业巡场点位"`

### Step 2: PatrolIssues.vue 日志加标签

新增 action → tag type 映射：
```ts
const ACTION_TAGS: Record<string, 'danger'|'success'|'primary'|'warning'|'info'> = {
  CREATE: 'danger',          // 问题上报 = danger 红
  ASSIGN: 'primary',
  TRANSFER: 'primary',
  FEEDBACK_CLOSE: 'success', // 整改反馈 = success 绿
  REVIEW_PASS: 'success',
  REVIEW_REJECT: 'warning',
  OVERDUE_NOTICE: 'info',
  UNASSIGNED_REMIND: 'info',
}
function actionTagType(action: string) { return ACTION_TAGS[action] || 'info' }
```

日志模板改为：
```html
<div class="logs">
  <div class="log" v-for="(log,idx) in current.logs" :key="idx">
    <span>{{formatTime(log.createdAt)}}</span>
    <el-tag :type="actionTagType(log.action)" size="small" effect="light">{{actionLabel(log.action)}}</el-tag>
    <em>{{log.note||''}}</em>
  </div>
</div>
```

### Step 3: OpsPatrolIssues.vue 日志加标签

同样的 tag 映射（已有 `actionLabel`，补一个 `actionTagType`），时间线 item 里：
```html
<el-timeline-item ...>
  <b>{{ l.operatorName || '-' }}</b>
  <el-tag :type="actionTagType(l.action)" size="small" effect="light">{{ actionLabel(l.action) }}</el-tag>
  <span v-if="l.note">：{{ l.note }}</span>
</el-timeline-item>
```

`actionTagType` 复用同样的映射（ops-patrol 的 action 集合是 CREATE/ASSIGN/TRANSFER/RECTIFY/FEEDBACK_CLOSE/REVIEW_PASS/REVIEW_REJECT/OVERDUE_NOTICE，patrol 多一个 UNASSIGNED_REMIND）。

### Step 4: 前端类型检查 + 上下文校验

```bash
cd server-spring/energy-admin && npx vue-tsc --noEmit
cd g:\hik\mall-energy-management && python scripts/ai/generate-context.py && python scripts/ai/check-context.py
```

### Step 5: 汇报改动、等用户确认后再 commit + push + 重新部署前端

后端无改动，前端改动小（3 个文件），只需重新 build frontend dist 并重启 frontend 容器。

## Dependencies and Considerations

- 仅前端改动，后端零修改、无 DB 迁移。
- Flyway 版本仍停在 V115，不变动。
- `PatrolDeptWorkbench.vue` 和 `PatrolIssueFeedback.vue`（移动端）**不含 issue 日志展示**，不在改动范围。
- Dashboard 或 CalendarView 里可能有"营运巡场"文案的聚合展示——不动（那是 ops-patrol 模块，与 patrol/物业巡场是两套独立业务）。

## Validation

- `vue-tsc --noEmit` 零错误
- `check-context.py` 零错误零警告
- 视觉验证：后台菜单"物业巡场管理"出现，点击问题详情抽屉，日志中 CREATE/FEEDBACK_CLOSE 颜色明显区分

## Risks

- 低风险：仅菜单文字和展示层 tag，不影响任何业务逻辑或数据。
- 如果移动端 PatrolEntry.vue 页面 title 想同步改，可额外调整，但不在本次范围（用户只说"旧巡场实际为物业巡场管理"，菜单改名已覆盖主要场景）。
