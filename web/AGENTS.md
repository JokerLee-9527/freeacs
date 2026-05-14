# Web 模块 - 管理界面

**职责**: 提供 Web UI 管理界面。

**框架**: Spark Java + FreeMarker 模板 + jQuery

## 核心架构

```
请求 → Route → Page Controller → Template → HTML
```

## 关键组件

| 组件 | 位置 | 职责 |
|------|------|------|
| 入口 | `App.java` | Spark 路由配置 |
| 页面定义 | `Page.java` | 40+ 页面枚举 |
| 页面基类 | `AbstractWebPage.java` | 通用页面功能 |
| 参数解析 | `input/ParameterParser.java` | 类型转换、文件上传 |
| 认证 | `routes/LoginRoute.java` | CSRF 保护的登录 |

## 页面结构

```
page/
├── unit/      # 设备管理 (UnitPage 830行)
├── unittype/  # 设备类型
├── profile/   # 配置文件
├── group/     # 设备分组
├── job/       # 作业管理
├── trigger/   # 触发器
├── report/    # 报告 (ReportPage 1022行)
└── window/    # 窗口管理
```

## 前端架构

- **模板**: FreeMarker (.ftl)
- **JS 框架**: jQuery + 自定义模块系统 (`acs.js`)
- **UI 组件**: jQuery UI, 表格排序, 模态框

## 输入处理

```java
ParameterParser parser = ...;
Integer id = parser.getInt("id");
String name = parser.getString("name");
FileItem file = parser.getFileItem("upload");
```

## 安全特性

- CSRF Token 保护
- 基于权限的数据过滤
- 会话超时配置 (默认 60 分钟)

## 模板位置

- FTL 模板: `src/main/resources/templates/`
- 静态资源: `src/main/resources/public/web/`

## 大文件

- `ReportPage.java` (1022行) - 最复杂页面
- `UnitStatusPage.java` (974行) - 实时状态
