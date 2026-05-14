# Syslog 模块 - 日志服务器

**职责**: 接收、处理和存储 TR-069 设备的系统日志。

**框架**: Spark Java

## 核心架构

```
UDP Packet → SyslogServer → SyslogPackets → Syslog2DB → Database
                ↓                              ↓
           Rate Limiter              Event Matching
```

## 关键组件

| 组件 | 职责 |
|------|------|
| `SyslogServer` | UDP 服务器 (默认端口 9116) |
| `Syslog2DB` | 消息处理和数据库存储 |
| `SyslogPacket` | 日志消息数据结构 |
| `SyslogPackets` | 线程安全消息缓冲区 |
| `DuplicateCheck` | 重复消息检测 |

## 消息处理流程

1. **接收**: UDP Socket 接收 RFC 3164 格式日志
2. **缓冲**: 添加到内存队列 (最大 100K 条)
3. **解析**: 提取 Facility/Severity/设备标识
4. **匹配**: 根据配置的事件规则处理
5. **存储**: 写入 MySQL/PostgreSQL

## 故障转移

- 失败消息写入磁盘文件
- 后台线程处理故障文件
- 24 小时后自动清理

## 限流保护

```properties
max.packet.rate = 10000  # 每分钟最大包数
receive.buffer.size = 10485760  # 10MB 接收缓冲区
```

## 事件匹配

在 Web UI 中配置 `syslog_event`:
- Regex 模式匹配
- Store 策略: STORE / DISCARD / DUPLICATE_CHECK
- 触发器关联

## 数据库表

```sql
syslog (
  syslog_id, collector_timestamp, device_timestamp,
  facility, severity, unit_id, profile_name,
  unit_type_name, content, ipaddress, syslog_event_id
)
```

## 配置

```properties
port = 9116
max.message.age.hours = 24
unknown.unit.policy = ALLOW  # ALLOW/DISCARD/CREATE
```

## 健康检查

`/ok` 端点返回组件状态

## 大文件

- `Syslog2DB.java` - 核心处理逻辑
- `dbi/Syslog.java` - 数据库操作
