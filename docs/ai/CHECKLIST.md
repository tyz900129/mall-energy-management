# 提交前检查清单

> 与 `AGENTS.md` 配套使用。提交前逐项确认；未勾选的项不得合并。
> 本清单只约束文档与事实同步，不替代测试与代码评审。

## 每次改动

- [ ] 已读 `AGENTS.md`，并按 §6 读了涉及的模块卡。
- [ ] 未执行 `git commit` / `git push`，改动留在工作区等确认（除非用户明确要求提交）。
- [ ] 未修改已应用的 Flyway 迁移脚本，只新增。
- [ ] 未写入账号、密码、密钥、证书、生产 IP。

## 新增 / 修改接口

- [ ] 在 `security/RequestPermissionResolver` 登记路径 → 权限码；未登记的 `/api/v1/**` 默认 403。
- [ ] 使用权限码鉴权，未硬编码角色名。
- [ ] 更新对应模块卡的“接口”段（路径、权限码、入口方法）。
- [ ] `python scripts/ai/generate-context.py` 刷新 `generated/api-map.md`。
- [ ] `python scripts/ai/check-context.py` 通过。

## 新增表 / 迁移

- [ ] 迁移编号顺延，脚本幂等（`CREATE TABLE IF NOT EXISTS` / `ON DUPLICATE KEY`）。
- [ ] 更新模块卡“表/迁移”段的 `- 表：` 行与迁移号。
- [ ] 更新 `AGENTS.md` 顶部锚点中的“Flyway 最高 Vxxx”。
- [ ] 重新生成并校验（同上一节两条命令）。

## 新增权限码

- [ ] 权限码已写入迁移，并挂到对应角色（`sys_role_permission`）。
- [ ] 模块卡“权限码”段已补充，注明读写含义。
- [ ] `generated/permission-codes.md` 中能看到该码及其来源。

## 新增模块 / 页面

- [ ] 模块卡按 `modules/_template.md` 新建，≤ 80 行，含“最后核对：日期 / commit”。
- [ ] 在 `docs/ai/README.md` 模块索引与 `AGENTS.md` §6 路由表登记。
- [ ] 前端菜单与按钮由权限码驱动，未新增角色名判断。
- [ ] 页面接口走 `src/api/*.ts`，未在页面直接写 axios。

## 删除 / 高危操作

- [ ] 有二次确认，且使用对应 `*.delete` 权限码（高危仅 SUPER_ADMIN）。
- [ ] 级联清理了关联数据与文件。

## 文档

- [ ] 模块卡末尾“最后核对”已更新为当天日期与 commit。
- [ ] 文档与代码冲突处以代码为准，并已修正文档。
- [ ] 不确定的事实标注“待核实”，没有猜测性描述。
