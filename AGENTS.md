# FreeACS TR-069 ACS 知识库

**生成时间:** 2026-05-14
**版本:** 3.1-SNAPSHOT
**许可证:** MIT License

## 概述

FreeACS 是一个完整的 TR-069 自动配置服务器 (ACS) 实现，用于管理 CPE 设备（路由器、网关等）。基于 Java 17 + Maven 多模块架构，支持 MySQL 数据库。

## 项目结构

```
freeacs/
├── bom/           # 依赖版本管理 (Bill of Materials)
├── common/        # 共享工具类 (缓存、HTTP、调度器)
├── dbi/           # 数据库接口层 - 核心数据访问
├── core/          # 后台任务服务器 (清理、报告、触发器)
├── tr069/         # TR-069 协议服务器 - Spring Boot
├── web/           # Web 管理界面 - Spark Java
├── webservice/    # WebService API - Spring Boot
├── syslog/        # Syslog 日志服务器
├── stun/          # STUN 服务器 (NAT 穿透/TR-111)
├── monitor/       # 监控服务
├── shell/         # CLI 命令行工具
├── tables/        # 数据库表结构 (MySQL/PostgreSQL)
├── scripts/       # 安装和部署脚本
├── distribution/  # 打包分发配置
└── cache/         # Hazelcast 分布式缓存
```

## 查找指南

| 任务 | 位置 | 说明 |
|------|------|------|
| TR-069 协议实现 | `tr069/src/main/java/.../methods/` | 所有 TR-069 方法的请求/响应处理 |
| 数据库操作 | `dbi/src/main/java/.../dbi/` | 所有 SQL 操作集中于此 |
| 设备管理 | `dbi/ACSUnit.java`, `dbi/Unit.java` | 设备 CRUD 和参数管理 |
| 后台任务 | `core/src/main/java/.../task/` | 定时任务和作业处理 |
| Web 页面 | `web/src/main/java/.../page/` | 所有 Web 页面控制器 |
| CLI 命令 | `shell/src/main/java/.../menu/` | 命令行菜单实现 |
| 配置文件 | `*/src/main/resources/application.conf` | 各模块配置 |

## 架构特点

### 混合框架架构 (重要!)

项目使用 **两种不同的 Web 框架**:
- **Spring Boot**: `tr069/`, `webservice/` 模块
- **Spark Java**: `web/`, `core/`, `stun/`, `syslog/`, `monitor/` 模块

**注意**: 新功能开发需确认目标模块使用的框架。

### 核心模块依赖

```
common ← dbi ← { tr069, web, core, shell, syslog, stun, monitor }
         ↓
      tables (数据库 schema)
```

### 入口点

| 模块 | 入口类 | 框架 |
|------|--------|------|
| tr069 | `com.github.freeacs.Main` | Spring Boot |
| web | `com.github.freeacs.web.App` | Spark Java |
| core | `com.github.freeacs.core.App` | Spark Java |
| webservice | `com.github.freeacs.ws.Application` | Spring Boot |
| shell | `com.github.freeacs.shell.ACSShellExec` | 独立 CLI |
| syslog | `com.github.freeacs.syslogserver.App` | Spark Java |
| stun | `com.github.freeacs.stun.App` | Spark Java |
| monitor | `com.owera.xaps.monitor.App` | Spark Java |

## 编码规范

### Java 版本
- 目标: Java 17
- 编译器参数: `--add-opens jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED`

### 依赖管理
- 使用 BOM (`bom/pom.xml`) 集中管理版本
- Spring Boot 3.2.2
- HikariCP 连接池
- Lombok 1.18.30 (代码生成)

### 命名约定
- 包名: `com.github.freeacs.*` (新模块)
- 包名: `com.owera.xaps.*` (遗留代码，如 monitor)
- 类名: `*Strategy` (策略模式), `*Page` (Web 页面), `*Task` (后台任务)

### 数据库操作
- 所有 SQL 在 `dbi/` 模块中
- 使用 `DynamicStatement` 构建动态查询
- 支持批量操作 (`BatchStorage`)

## 禁止事项

- **不要** 在 `dbi/` 外编写 SQL 语句
- **不要** 修改 TR-xxx XML 规范文件 (`dbi/src/main/resources/tr-*.xml`) - 自动生成
- **不要** 忽略 `FIXME` 注释（如 Calendar.js 的 I18N 问题）
- **不要** 在生产环境使用 `application-no-security.properties`

## 测试

```bash
# 运行所有测试
./mvnw test

# 使用 Testcontainers (需要 Docker)
# MySQL 容器自动启动用于集成测试
```

测试基础设施:
- JUnit 5 + Mockito
- Testcontainers MySQL 用于集成测试
- 多环境配置: `application-basic-security.properties` 等

## 构建

```bash
# 完整构建
./mvnw clean package

# 生成分发包
# 每个模块生成: <module>/target/<module>-<version>-bin.zip
```

## Docker 部署

```bash
# 使用 docker-compose
cd scripts/
docker-compose up -d

# 服务: nginx, mysql, core, web, tr069, syslog, stun, monitor, webservice
```

## 注意事项

1. **包名不一致**: 部分遗留代码使用 `com.owera.xaps.*` 包名
2. **框架差异**: Spring Boot 和 Spark Java 模块配置方式不同
3. **测试数据库**: 集成测试需要 MySQL 或使用 Testcontainers
4. **密码编码**: 安全模块有 TODO 标记需更新密码编码方式

## 相关文档

- [TR-069 协议规范](https://www.broadband-forum.org/technical/download/TR-069.pdf)
- [GitHub Discussions](https://github.com/freeacs/freeacs/discussions)
- 各模块 README.md 有详细说明
