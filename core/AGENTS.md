# Core 模块 - 后台任务服务器

**职责**: 执行后台定时任务 (报告生成、数据清理、触发器处理)。

**框架**: Spark Java

## 核心架构

```
App → CoreServlet → Task Scheduler → Tasks
```

## 任务基类

| 基类 | 用途 | 特点 |
|------|------|------|
| `DBIShare` | 只读任务 | 共享 DBI 对象，节省资源 |
| `DBIOwner` | 写入任务 | 独立 DBI 实例，防止并发冲突 |

## 主要任务

| 任务 | 调度 | 职责 |
|------|------|------|
| `JobRuleEnforcer` | 每秒 | 作业规则执行和终止条件 |
| `ReportGenerator` | 每小时/每天 | 报告生成 (VoIP/硬件/系统日志) |
| `TriggerReleaser` | 每分钟 | 触发器条件和脚本执行 |
| `DeleteOldJobs` | 每天 | 清理旧作业数据 |
| `DeleteOldSyslog` | 每天 | 清理旧系统日志 |

## 调度框架

使用 Quartz Cron 表达式:
```properties
# 示例
report.generator.daily.cron = 0 30 5 * * ?  # 每天 05:30
```

## 任务结构

```
task/
├── DBIOwner.java         # 写入任务基类
├── DBIShare.java         # 只读任务基类
├── ReportGenerator.java  # 报告生成
├── JobRuleEnforcer.java  # 作业规则
├── TriggerReleaser.java  # 触发器处理
└── DeleteOld*.java       # 清理任务
```

## 执行模式

- **轻量任务**: 高频执行 (秒/分钟级)
- **重量任务**: 低频执行 (小时/天级)
- **条件任务**: 功能开关控制

## 错误处理

所有任务实现统一异常处理，失败记录日志但不会崩溃。

## 配置

```properties
# 任务开关
report.enabled = true
trigger.enabled = false
script.enabled = false
```
