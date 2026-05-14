# DBI 模块 - 数据库接口层

**职责**: 所有数据库操作的唯一入口，提供对象模型、缓存和消息同步。

## 核心类

| 类名 | 职责 |
|------|------|
| `DBI` | 主入口，管理缓存和消息同步 |
| `ACS` | 核心对象模型，包含 Unittype/Profile/Group/Job |
| `ACSUnit` | 设备 CRUD 操作 |
| `ACSDao` | 现代化 DAO 实现 |
| `DynamicStatement` | 动态 SQL 构建 |

## 对象模型层级

```
Unittype → Profile → Group → Job
        → UnittypeParameter
        → Unit (设备实例)
```

## 消息同步机制

- DBI 轮询 `message` 表 (每秒)
- 消息类型: `PUBLISH-ADD/CHG/DEL`
- 支持跨模块实时同步

## 查询模式

- `UnitQueryWithinUnittype`: 单设备类型内查询
- `UnitQueryCrossUnittype`: 跨设备类型查询

## 关键文件

```
dbi/src/main/java/com/github/freeacs/dbi/
├── DBI.java           # 主入口
├── ACS.java           # 对象模型
├── ACSUnit.java       # 设备操作 (893行)
├── Jobs.java          # 作业管理 (787行)
├── Unit.java          # 设备实体
├── Profile.java       # 配置文件
├── DynamicStatement.java  # SQL构建
└── report/            # 报告生成
```

## 注意事项

- **所有 SQL 必须在此模块**
- 使用 `BatchStorage` 进行批量操作
- 大文件: `ACSUnit.java` (893行, 73个异常处理器)
