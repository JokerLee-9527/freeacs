# TR-069 模块 - 协议服务器

**职责**: 实现 TR-069 ACS 协议，处理 CPE 设备通信。

**框架**: Spring Boot 3.2.2

## 核心架构

```
CPE → Tr069Controller → ProvisioningStrategy → Decision → Response
                              ↓
                         SessionData (状态管理)
```

## 关键组件

| 组件 | 位置 | 职责 |
|------|------|------|
| HTTP 入口 | `Tr069Controller.java` | 处理所有 TR-069 HTTP 请求 |
| 编排核心 | `ProvisioningStrategy.java` | 请求处理/决策/响应生成 |
| 状态管理 | `SessionData.java` | 会话状态容器 |
| XML 解析 | `xml/Parser.java` | SOAP 消息解析 |
| XML 响应 | `xml/Response.java` | SOAP 响应构建 |

## 支持的 TR-069 方法

- Inform (设备注册)
- GetParameterValues / SetParameterValues
- GetParameterNames
- Download (固件/配置下载)
- Reboot / FactoryReset
- TransferComplete

## 策略模式

每个 TR-069 方法有三层策略:
1. **Request Process** - 处理 CPE 请求
2. **Decision** - 决定下一步操作
3. **Response Create** - 生成响应

```
methods/
├── request/    # 请求处理策略
├── decision/   # 决策策略
└── response/   # 响应生成策略
```

## 设备适配

- **Quirks 系统**: 设备特定行为调整
- **Key Root 检测**: 自动识别参数层级
- **发现模式**: 自动注册新设备

## 配置文件

- `application.conf` - 主配置
- `application-basic-security.properties` - 基础认证
- `application-digest-security.properties` - 摘要认证

## 测试

- XML fixtures: `src/test/resources/provision/cpe/`
- 集成测试使用 Testcontainers MySQL
