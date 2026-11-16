# 项目规则

本仓库的 AI 协作规则唯一来源是根目录 `AGENTS.md`，修改代码前必须先读。

1. 不自动 commit / push；不部署生产；不修改线上数据库。
2. 新增接口必须在 `security/RequestPermissionResolver` 登记权限码，未登记默认 403。
3. 功能鉴权一律用权限码，禁止硬编码角色名；不得恢复部门权限裁剪。
4. 模块细节按 `AGENTS.md` §6 路由到 `docs/ai/modules/` 对应模块卡。
5. 代码风格与参考文件见 `AGENTS.md` §4。
6. 文档与代码冲突时，以代码为准，并同步修正文档。
