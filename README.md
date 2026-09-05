# 商场能源设备与商户业务管理系统

用于商场水电设备、铺位、商户账户和水电费变更流程的内部管理系统。当前正式版本采用 Spring Boot 3、MySQL 8 和 Vue 3。

## 主要功能

- 原能源平台账户、设备及绑定关系同步
- 设备本地分组（平台同步不会覆盖）
- 设备历史日用量增量同步、同步批次与产品测量点配置
- 按日期和设备分组进行用量趋势及设备排行分析
- 商户账户、商户代码、铺位和设备绑定管理
- 新商户开户、已有商户增铺、商户撤柜流程
- 手机端现场设备核验、读数和照片采集
- OA 提交资料、申请批注、OA 流程编号与完结备注
- 企业微信、飞书、钉钉多机器人 Webhook 路由
- 异常检测、平台预期变化匹配、审计日志
- 用户注册、设备指纹登录与角色权限管理

## 项目结构

```text
server-spring/
  energy-management/  Spring Boot 后端
  energy-admin/       Vue 3 管理端
  design/             系统设计文档
```

## 本地开发

后端需要 Java 17、Maven 和 MySQL 8。所有密码、JWT 密钥和平台凭据必须通过环境变量提供，不得写入源码。

```powershell
cd server-spring/energy-management
mvn test
mvn spring-boot:run
```

前端需要 Node.js 20 或更高版本：

```powershell
cd server-spring/energy-admin
npm install
npm run dev
```

生产构建：

```powershell
cd server-spring/energy-management
mvn package

cd ../energy-admin
npm run build
```

## 安全说明

- `.env`、上传附件、日志、数据库备份和部署包均被排除在版本控制之外。
- 平台密码和 Webhook 地址在数据库中加密保存。
- 生产部署前必须设置独立的 `JWT_SECRET`、`PLATFORM_CREDENTIAL_SECRET`、数据库密码和管理员初始密码。
