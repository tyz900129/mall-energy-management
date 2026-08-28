# Mall Energy Management — 设计文档 V1

> 依据《mall_energy_management_trae_development_spec_v1.md》第 63 节输出。  
> 单工程 + 多 package 模式（与用户确认）。目标 war 名：`energy-management.war`，部署上下文：`/energy`。

---

## 1. 项目目录结构

```
/home/admin/wanyuecity/energy-management/
├── pom.xml                          # Spring Boot 3.3.x, Java 17
├── README.md
├── .gitignore
├── scripts/
│   ├── build.sh                     # mvn clean package -DskipTests
│   ├── deploy.sh                    # cp target/*.war /vol2/.../webapps/energy.war
│   └── gen-secret.sh                # openssl rand -base64 32
├── energy-management/
│   ├── src/main/java/com/wanyuecity/energy/
│   │   ├── EnergyApplication.java               # @SpringBootApplication
│   │   ├── common/                              # 通用工具
│   │   │   ├── api/ApiResponse.java             # {code, message, data}
│   │   │   ├── api/ApiError.java
│   │   │   ├── exception/BizException.java      # 业务异常基类
│   │   │   ├── exception/GlobalExceptionHandler.java
│   │   │   ├── util/CryptoUtils.java            # AES-256-GCM 加解密
│   │   │   ├── util/MerchantCodeParser.java     # 六位代码正则
│   │   │   └── util/RandomTokenUtils.java       # 核验 token 生成
│   │   ├── system/                              # 用户、角色、菜单、字典
│   │   │   ├── entity/SysUser.java
│   │   │   ├── entity/SysRole.java
│   │   │   ├── entity/SysMenu.java
│   │   │   ├── entity/SysUserRole.java
│   │   │   ├── entity/SysRoleMenu.java
│   │   │   ├── mapper/*.java                    # MyBatis-Plus Mapper
│   │   │   ├── service/impl/*.java
│   │   │   └── controller/AuthController.java   # /api/v1/auth/{login,logout,me}
│   │   ├── security/                            # Spring Security + JWT
│   │   │   ├── SecurityConfig.java
│   │   │   ├── JwtTokenProvider.java
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── UserDetailsServiceImpl.java
│   │   ├── platform/                            # 源平台数据 + 同步
│   │   │   ├── adapter/PlatformAdapter.java     # 统一接口
│   │   │   ├── adapter/LeoEnergyPlatformAdapter.java  # 第一期实现
│   │   │   ├── adapter/dto/PlatformAccountDTO.java
│   │   │   ├── adapter/dto/PlatformDeviceDTO.java
│   │   │   ├── adapter/dto/PlatformAccountDeviceDTO.java
│   │   │   ├── adapter/dto/EnergyDailyDTO.java
│   │   │   ├── adapter/dto/EnergyMonthlyDTO.java
│   │   │   ├── adapter/dto/LoginResult.java
│   │   │   ├── entity/PlatformConfig.java
│   │   │   ├── entity/PlatformAccountCurrent.java
│   │   │   ├── entity/PlatformDeviceCurrent.java
│   │   │   ├── entity/PlatformAccountDeviceCurrent.java
│   │   │   ├── entity/PlatformAccountSnapshot.java
│   │   │   ├── entity/PlatformDeviceSnapshot.java
│   │   │   ├── entity/PlatformAccountDeviceSnapshot.java
│   │   │   ├── entity/PlatformSyncBatch.java
│   │   │   ├── service/PlatformConfigService.java
│   │   │   ├── service/PlatformSyncService.java
│   │   │   ├── service/CredentialCryptoService.java  # AES-GCM
│   │   │   ├── controller/PlatformConfigController.java
│   │   │   ├── controller/PlatformSyncController.java
│   │   │   ├── controller/PlatformDataController.java  # 只读查询
│   │   │   └── job/PlatformSyncJob.java         # @Scheduled
│   │   ├── merchant/
│   │   │   ├── entity/Merchant.java             # 本地商户
│   │   │   ├── service/MerchantCodeParser.java  # (亦在 common)
│   │   │   └── service/MerchantService.java
│   │   ├── stall/
│   │   │   ├── entity/Stall.java
│   │   │   ├── entity/StallDevice.java
│   │   │   ├── service/StallService.java
│   │   │   └── controller/StallController.java
│   │   ├── device/
│   │   │   └── service/DeviceService.java       # 平台设备查询
│   │   ├── workflow/                            # Phase 3 启用
│   │   ├── verification/                        # Phase 4
│   │   ├── notification/                        # Phase 5
│   │   ├── reconciliation/                      # Phase 6
│   │   ├── analysis/                            # Phase 7 占位
│   │   └── audit/
│   │       ├── entity/AuditLog.java
│   │       ├── aspect/AuditLogAspect.java
│   │       └── service/AuditLogService.java
│   ├── src/main/resources/
│   │   ├── application.yml                      # 公共
│   │   ├── application-dev.yml                  # 开发
│   │   ├── application-prod.yml                 # 生产
│   │   ├── logback-spring.xml                   # 5 类日志
│   │   ├── db/migration/
│   │   │   ├── V1__init_system.sql              # 用户/角色/菜单/审计
│   │   │   ├── V2__platform_tables.sql          # 源平台配置 + 同步 + 镜像
│   │   │   ├── V3__stall.sql                    # 铺位
│   │   │   ├── V4__workflow.sql                 # 流程
│   │   │   ├── V5__verification.sql             # 核验
│   │   │   ├── V6__notification.sql             # Webhook
│   │   │   ├── V7__reconciliation.sql           # 异常
│   │   │   └── V8__energy.sql                   # 能耗
│   │   ├── mapper/                              # MyBatis-Plus XML
│   │   │   └── *.xml
│   │   ├── static/                              # Vue3 dist 产物（构建后拷贝）
│   │   │   ├── index.html
│   │   │   └── assets/
│   │   └── templates/                           # 若需服务端模板（暂空）
│   └── src/test/java/com/wanyuecity/energy/
│       ├── platform/CredentialCryptoServiceTest.java
│       ├── platform/PlatformSyncServiceTest.java
│       ├── platform/MultiAccountDetectorTest.java
│       ├── platform/UnauthorizedChangeDetectorTest.java
│       └── workflow/WorkflowStateMachineTest.java
└── energy-web/                                  # Vue3 前端
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── index.html
    ├── src/
    │   ├── main.ts
    │   ├── App.vue
    │   ├── router/
    │   ├── store/                               # Pinia
    │   ├── api/                                 # Axios 封装
    │   ├── views/
    │   │   ├── auth/Login.vue
    │   │   ├── home/Dashboard.vue
    │   │   ├── platform/Config.vue
    │   │   ├── platform/SyncLog.vue
    │   │   ├── platform/AccountList.vue
    │   │   ├── platform/DeviceList.vue
    │   │   ├── stall/StallList.vue
    │   │   ├── stall/StallDevice.vue
    │   │   ├── workflow/                         # Phase 3
    │   │   ├── verification/                     # Phase 4
    │   │   ├── exception/ExceptionList.vue
    │   │   └── system/User.vue
    │   ├── components/                           # 通用组件
    │   ├── utils/
    │   └── styles/
    └── public/
```
