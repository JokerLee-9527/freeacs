# STUN 模块 - NAT 穿透服务器

**职责**: 实现 STUN 协议和 TR-111 NAT 穿透，支持连接 NAT 后的设备。

**框架**: Spark Java

## 核心架构

```
STUN Client → StunServer → MappedAddress (公网IP:端口)
                    ↓
             Kick System → CPE Connection
```

## 关键组件

| 组件 | 职责 |
|------|------|
| `StunServer` | STUN 协议实现 (RFC 3489) |
| `Kick` | NAT 穿透连接机制 |
| `MessageStack` | UDP 消息队列 |
| `SingleKickThread` | 单设备连接处理 |
| `ActiveDeviceDetection` | 活跃设备监控 |

## NAT 穿透方法 (优先级)

1. **TCP/HTTP Kick**: 直接连接 ConnectionRequestURL
2. **UDP Kick (TR-111)**: 发送到 UDPConnectionRequestAddress
3. **端口转发 Kick**: 尝试预设端口转发
4. **动态 Kick**: 根据发现的公网 IP 构造 URL

## STUN 协议

需要双网络接口实现完整 NAT 检测:
- 主端口: 3478
- 辅端口: 3479

## 消息属性

- `MappedAddress`: 公网 IP:端口映射
- `ChangeRequest`: NAT 行为测试
- `ConnectionRequestBinding`: TR-111 连接请求

## 配置

```properties
primary.port = 3478
secondary.port = 3479
primary.ip = 0.0.0.0
secondary.ip = 0.0.0.0
kick.expect-port-forwarding = false
```

## 线程架构

- `SingleKickThread`: 处理单个设备连接请求
- `JobKickThread`: 批量设备连接
- `StunServerReceiverThread`: 多 Socket 包接收

## 与 TR-069 集成

通过 CPE 参数获取:
- `ConnectionRequestURL`
- `UDPConnectionRequestAddress`
- `STUNEnable`

## 安全

- HMAC-SHA1 认证 (UDP Kick)
- HTTP Digest/Basic 认证 (TCP Kick)
- 公网 IP 验证 (可选)

## 代码结构

```
stun/
├── de/javawi/jstun/   # JStun 库 (STUN 协议)
│   ├── StunServer.java
│   ├── header/        # STUN 消息头
│   └── attribute/     # STUN 属性
└── com/github/freeacs/stun/
    ├── Kick.java      # NAT 穿透
    └── MessageStack.java
```
