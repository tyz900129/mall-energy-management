# 模块：<名称>

> 复制本文件后填写。事实以代码为准，不确定的写“待核实”，不要猜。

- 后端：`server-spring/energy-management/src/main/java/com/wanyuecity/energy/<包>/`
  - Controller：`<路径>`
  - Service：`<路径>`
  - 定时任务：`<路径或“无”>`
- 前端：`server-spring/energy-admin/src/views/<页面>.vue`；`src/api/<模块>.ts`
- 权限码前缀：`<prefix>.*`
- 表：`<table1>`、`<table2>`
- 迁移：`V__` 至 `V__`
- 附件：`<business_type 或“无”>`

## 关键不变量

1. <业务规则，必须始终成立>
2. <状态流转/金额/权限/数据范围规则>

## 已知坑

- <历史问题、数据口径、性能陷阱、易错点>

## 测试

```bash
mvn -Dtest=<Pattern>*Test test
```

## 最后核对

YYYY-MM-DD / <branch>@<commit>
