# EmbeddedTR069Server - 嵌入式 TR-069 库

## TL;DR

> **目标**: 新建 `EmbeddedTR069Server` 模块，打包为 **可复用的 JAR 库**，供其他程序调用。不依赖 Spring Boot，支持自由启停，使用 Netty 实现 HTTP 服务器，支持高并发。**目标 JDK 版本: Java 8**。
> 
> **核心策略**: 通过适配器模式解耦 Servlet 依赖，复用现有 `tr069` 模块的核心逻辑，仅替换 HTTP 层、认证层、调度层和配置层。提供清晰的 Builder API 和事件监听机制。
> 
> **Deliverables**:
> - `embedded-tr069-server` Maven 模块 → 打包为 JAR 库
> - `TR069Server` 主入口类 + Builder API
> - `TR069ServerConfig` 配置类（支持链式配置）
> - `TR069ServerListener` 事件监听接口
> - Netty HTTP 服务器实现
> - HttpRequestAdapter 接口 + Netty 实现
> - 独立认证模块 (Basic/Digest)
> - 独立调度器
> 
> **Estimated Effort**: Large
> **Parallel Execution**: YES - 4 waves
> **Critical Path**: Adapter接口 → HTTPRequestResponseData适配 → Netty服务器 → 主类集成

---

## Context

### Original Request

参考 tr069 模块，新建一个模块 EmbeddedTR069Server，要求：
1. 不依赖 Spring Boot，可在程序中自由启停
2. HTTP Server 用 Netty 实现
3. 尽量做到高并发
4. 可复用的模块尽量不要改动
5. **最终打包为 JAR 库，供其他程序调用**

### 调用方式示例

```java
// 示例1: 最简启动
TR069Server server = TR069Server.builder()
    .dataSource(dataSource)
    .build();
server.start();

// 示例2: 完整配置
TR069Server server = TR069Server.builder()
    .dataSource(dataSource)
    .port(8080)
    .contextPath("/tr069")
    .authMethod(AuthMethod.BASIC)
    .workerThreads(64)
    .listener(new TR069ServerListener() {
        @Override
        public void onDeviceConnected(String unitId, String ipAddress) {
            log.info("Device connected: {} from {}", unitId, ipAddress);
        }
        @Override
        public void onSessionCompleted(String unitId, SessionSummary summary) {
            log.info("Session completed: {} - {}", unitId, summary);
        }
        @Override
        public void onError(String unitId, Throwable error) {
            log.error("Error for device: {}", unitId, error);
        }
    })
    .build();
server.start();

// 程序退出时停止
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    server.stop();
}));

// 示例3: 程序化控制
server.kickDevice("device-001");  // 主动踢出设备
server.getConnectedDevices();      // 获取在线设备列表
server.isRunning();                // 检查服务状态
```

### 库的 API 设计

```java
// 主入口类
public class TR069Server {
    
    // Builder 模式创建
    public static TR069ServerBuilder builder();
    
    // 生命周期管理
    public void start();
    public void stop();
    public boolean isRunning();
    
    // 设备管理
    public void kickDevice(String unitId);
    public Set<String> getConnectedDevices();
    public int getConnectedCount();
    
    // 获取内部组件 (高级用法)
    public DBI getDBI();
    public TR069ServerConfig getConfig();
}

// 配置类
public class TR069ServerConfig {
    private int port = 8080;
    private String contextPath = "/tr069";
    private AuthMethod authMethod = AuthMethod.BASIC;
    private int workerThreads = 64;
    private int bossThreads = 1;
    private String publicUrl;
    // ... getters
}

// 事件监听接口
public interface TR069ServerListener {
    default void onDeviceConnected(String unitId, String ipAddress) {}
    default void onDeviceDisconnected(String unitId) {}
    default void onSessionCompleted(String unitId, SessionSummary summary) {}
    default void onError(String unitId, Throwable error) {}
    default void onServerStarted() {}
    default void onServerStopped() {}
}

// 认证方式枚举
public enum AuthMethod {
    NONE, BASIC, DIGEST
}
```

### 可复用的核心组件

```
tr069/src/main/java/com/github/freeacs/tr069/
├── methods/
│   ├── ProvisioningStrategy.java      # 核心处理编排
│   ├── request/*RequestProcessStrategy.java  # 请求处理
│   ├── decision/*DecisionStrategy.java       # 决策逻辑
│   └── response/*ResponseCreateStrategy.java # 响应构建
├── xml/
│   ├── Parser.java                    # XML 解析
│   └── Response.java                  # XML 响应
├── http/
│   ├── HTTPRequestResponseData.java   # 请求数据封装 (需适配)
│   ├── HTTPRequestData.java
│   └── HTTPResponseData.java
├── base/
│   ├── BaseCache.java                 # 缓存管理
│   └── Log.java                       # 日志
└── SessionData.java                   # 会话状态
```

### Spring Boot 依赖分析

| 层级 | Spring Boot 依赖 | 文件 |
|------|-----------------|------|
| 入口 | `@SpringBootApplication`, `SpringApplication.run()` | `Main.java` |
| HTTP 层 | `@RestController`, `@PostMapping`, `ResponseEntity` | `Tr069Controller.java` |
| 配置 | `@Value`, `@Configuration`, `@Bean`, `@Component` | `Properties.java`, `config/*.java` |
| 安全 | Spring Security (Authentication, UserDetails, UserDetailsService) | `security/*.java` |
| 调度 | `@Scheduled` | `Tr069Controller.java:138-162` |
| 会话 | `HttpServletRequest.getSession()` | `HTTPRequestResponseData.java` |

### HttpServletRequest 依赖明细

**HTTPRequestResponseData 内部使用 (4处)**:
- `rawRequest.getSession().getId()` - 获取会话 ID
- `rawRequest.getSession()` - 获取会话对象
- `rawRequest.getHeader("X-Real-IP")` - 获取真实 IP
- `rawRequest.getRemoteAddr()` / `getRemotePort()` - 获取远程地址

**SessionLogging 使用 (2处)**:
- `reqRes.getRawRequest().getRemoteHost()` (第 59 行)
- `reqRes.getRawRequest()` 获取完整请求对象 (第 119 行)

**Tr069Controller 使用 (2处)**:
- `request.getInputStream()` - 获取请求输入流
- `request.getContentLength()` - 获取内容长度

### 认证机制分析

**当前流程**:
```
Spring Security 过滤器链 → AcsUnitDetailsService.loadUserByUsername() → AcsUnit(UserDetails)
→ Tr069Controller.doPost(Authentication) → authentication.getPrincipal() → 设置 SessionData.unitId
```

**数据库模型**: 设备密码存储在 `Unit.unitParameters` 的 `SystemParameters.SECRET` 参数中

**认证方式**: HTTP Basic 和 HTTP Digest 两种，通过 `auth.method` 配置切换

### 调度任务分析

| 任务 | 频率 | 作用 | 依赖 |
|------|------|------|------|
| ActiveDeviceDetectionTask | 5分钟 | 检测设备活跃度，识别异常设备 | DBI, Syslog |
| ScheduledKickTask | 1秒 | 处理设备重连踢出逻辑 | DBI, ACSUnit |
| MessageListenerTask | 5秒 | 清理消息监听器已读消息 | Inbox, DBI |

### DBI 初始化分析

**手动初始化流程**:
```
1. 创建 DataSource (MariaDbDataSource / HikariCP)
2. 创建 Syslog 实例 (需要 Identity 和 User)
3. 调用 DBI.createAndInitialize(lifetimeSec, dataSource, syslog)
4. 获取 ACS/ACSUnit 实例
```

**Properties 类问题**: 依赖 `Spring Environment`，需要创建独立的 `TR069Config`

---

## 关键技术适配方案

### 1. Properties 适配方案

**问题**: `tr069` 模块的 `Properties.java` 依赖 Spring Environment

**解决方案**: 创建独立的 `TR069Properties` 类，在 Builder 中注入

```java
// 新建: TR069Properties.java (替代 Properties)
public class TR069Properties {
    private String authMethod = "BASIC";
    private String publicUrl;
    private boolean discoveryMode = false;
    private int concurrentDownloadLimit = 50;
    private boolean appendHwVersion = false;
    private Map<String, String[]> quirks = new HashMap<>();
    
    // 工厂方法: 从 TR069ServerConfig 创建
    public static TR069Properties fromConfig(TR069ServerConfig config) {
        TR069Properties props = new TR069Properties();
        props.setAuthMethod(config.getAuthMethod().name());
        props.setPublicUrl(config.getPublicUrl());
        // ...
        return props;
    }
}
```

**集成方式**: 在 `TR069Server` 内部创建 `TR069Properties` 实例，传递给 `ProvisioningStrategy`

---

### 2. BaseCache 多实例问题

**问题**: `BaseCache` 使用静态变量存储缓存

```java
// 当前问题
public class BaseCache {
  private static final Cache cache = new Cache();  // ❌ 静态，影响多实例
}
```

**解决方案**: 创建实例级缓存管理器

```java
// 新建: InstanceCache.java
public class InstanceCache {
    private final Cache cache = new Cache();
    private final String instanceId;
    
    public InstanceCache(String instanceId) {
        this.instanceId = instanceId;
    }
    
    public SessionDataI getSessionData(String unitKey) {
        String key = instanceId + ":" + unitKey + SESSION_KEY;
        // ...
    }
    
    public void putSessionData(String unitKey, SessionDataI sessionData) {
        String key = instanceId + ":" + unitKey + SESSION_KEY;
        // ...
    }
}
```

**集成方式**: 
- `TR069Server` 创建时生成唯一 `instanceId`
- 替换 `BaseCache` 静态调用为实例方法
- 或保持 `BaseCache` 不变，仅支持单实例场景

**决策**: 首版保持 `BaseCache` 不变，支持单实例。多实例作为后续增强。

---

### 3. SessionLogging 适配

**问题**: `SessionLogging.java` 使用 `getRawRequest().getRemoteHost()`

**解决方案**: 修改 `HTTPRequestResponseData` 添加 `getRemoteHost()` 方法

```java
// HTTPRequestResponseData.java 添加方法
public String getRemoteHost() {
    return requestAdapter.getRemoteHost();  // 委托给适配器
}

// HttpRequestAdapter 接口添加
String getRemoteHost();
```

---

### 4. Syslog 独立初始化

**问题**: `Syslog` 创建需要 `Identity` 和 `User`，这些有 Spring 依赖

**解决方案**: 直接使用 DBI 模块的构造函数

```java
// 在 TR069Server 中创建 Syslog
private Syslog createSyslog(DataSource dataSource) {
    // 创建 Identity
    Identity identity = new Identity();
    identity.setSyslogEventFacility(SyslogConstants.FACILITY_TR069);
    
    // 创建 User
    User user = new User();
    user.setUserId(1);
    user.setName("embedded-tr069");
    
    // 创建 Syslog
    return new Syslog(dataSource, identity, user);
}
```

---

### 5. 配置项完整清单

```java
public class TR069ServerConfig {
    // 网络配置
    private int port = 8080;                              // 监听端口
    private String host = "0.0.0.0";                      // 绑定地址
    private String contextPath = "/tr069";               // 上下文路径
    
    // 认证配置
    private AuthMethod authMethod = AuthMethod.BASIC;     // 认证方式
    private String digestSecret;                          // Digest 认证密钥
    
    // Netty 线程配置
    private int bossThreads = 1;                          // Boss 线程数
    private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;  // Worker 线程数
    private int businessThreads = 64;                     // 业务线程数
    
    // 连接配置
    private int maxContentLength = 10 * 1024 * 1024;     // 最大请求体 10MB
    private int connectionTimeout = 30000;                // 连接超时 30s
    private boolean tcpNoDelay = true;                    // 禁用 Nagle
    private boolean keepAlive = true;                     // HTTP Keep-Alive
    
    // 业务配置
    private String publicUrl;                             // 公网 URL
    private boolean discoveryMode = false;               // 发现模式
    private int concurrentDownloadLimit = 50;            // 并发下载限制
    private int sessionTimeout = 180;                     // 会话超时 3分钟
    
    // 调度配置
    private boolean scheduledTasksEnabled = true;         // 启用调度任务
    private long activeDeviceDetectionInterval = 5 * 60 * 1000;  // 5分钟
    private long kickTaskInterval = 1000;                 // 1秒
    private long messageListenerInterval = 5000;         // 5秒
}
```

---

### 6. 异常处理策略

```java
public class TR069ServerException extends RuntimeException {
    private final ErrorCode errorCode;
    
    public enum ErrorCode {
        DATABASE_CONNECTION_FAILED,
        AUTHENTICATION_FAILED,
        INVALID_CONFIGURATION,
        SERVER_ALREADY_RUNNING,
        SERVER_NOT_RUNNING,
        INTERNAL_ERROR
    }
}

// 在 TR069Server 中的异常处理
public void start() {
    try {
        // 启动逻辑
    } catch (SQLException e) {
        throw new TR069ServerException(ErrorCode.DATABASE_CONNECTION_FAILED, e);
    } catch (Exception e) {
        throw new TR069ServerException(ErrorCode.INTERNAL_ERROR, e);
    }
}
```

---

### 7. HTTP 请求处理流程

```
Netty 请求接收
    │
    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  TR069Handler.channelRead0()                                         │
│  ├── 1. 解析 HTTP 头 (Authorization)                                 │
│  ├── 2. 解析 SOAP XML (Parser)                                       │
│  ├── 3. 创建 HTTPRequestResponseData                                 │
│  │      └── 创建 HttpRequestAdapterImpl (Netty → Adapter)           │
│  ├── 4. 认证验证                                                     │
│  │      └── BasicAuthParser / DigestAuthParser                      │
│  ├── 5. 调用 ProvisioningStrategy.process()                         │
│  │      ├── RequestProcessStrategy.process()                        │
│  │      ├── DecisionStrategy.makeDecision()                         │
│  │      └── ResponseCreateStrategy.getResponse()                    │
│  ├── 6. 构建 Netty HTTP 响应                                         │
│  ├── 7. 触发事件监听器                                                │
│  └── 8. 返回响应                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## JDK 8 兼容性说明

### JDK 8 环境配置

```bash
# JDK 8 安装路径
export JAVA_HOME=/mnt/d/lijian/tools/jdk/jdk1.8.0_491_linux
export PATH=$JAVA_HOME/bin:$PATH

# 验证 JDK 版本
java -version
# 应输出: java version "1.8.0_491"
```

### 编码限制

| 特性 | JDK 8 状态 | 替代方案 |
|------|-----------|----------|
| **var 关键字** | ❌ 不支持 | 使用显式类型声明 |
| **Record** | ❌ 不支持 | 使用 Lombok `@Data` 或普通类 |
| **Text Blocks** | ❌ 不支持 | 使用字符串拼接或常量 |
| **Sealed Classes** | ❌ 不支持 | 使用普通继承 |
| **Stream.takeWhile()** | ❌ 不支持 | 使用 `filter()` + 自定义逻辑 |
| **Optional.ifPresentOrElse()** | ❌ 不支持 | 使用 `if (opt.isPresent())` |
| **LocalDate/DateTime** | ✅ 支持 | 可直接使用 |
| **Lambda** | ✅ 支持 | 可直接使用 |
| **Stream API** | ✅ 支持 (部分) | 大部分可用 |
| **Method Reference** | ✅ 支持 | 可直接使用 |

### 代码示例 (JDK 8 兼容)

```java
// ❌ JDK 11+ 写法
var server = TR069Server.builder().build();

// ✅ JDK 8 写法
TR069Server server = TR069Server.builder().build();

// ❌ JDK 14+ Record
public record SessionSummary(String unitId, String method, long durationMs) {}

// ✅ JDK 8 + Lombok
@Data
public class SessionSummary {
    private String unitId;
    private String method;
    private long durationMs;
}

// ❌ JDK 15+ Text Block
String xml = """
    <soap:Envelope>
        <soap:Body/>
    </soap:Envelope>
    """;

// ✅ JDK 8 String
String xml = "<soap:Envelope>\n" +
             "    <soap:Body/>\n" +
             "</soap:Envelope>";
```

### Lombok 依赖 (JDK 8 兼容)

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>
</dependency>
```

## Work Objectives

### Core Objective

创建 `EmbeddedTR069Server` 模块，打包为 **可复用的 JAR 库**，通过适配器模式解耦 Servlet/Spring 依赖，使用 Netty 实现高并发 HTTP 服务器，复用现有 TR-069 核心协议逻辑。提供清晰的 Builder API 和事件监听机制，供其他程序集成调用。

### Concrete Deliverables

- `embedded-tr069-server/` Maven 模块 → 打包为 JAR 库
- `TR069Server` 主入口类 + Builder API
- `TR069ServerConfig` 配置类（支持链式配置）
- `TR069ServerListener` 事件监听接口
- `SessionSummary` 会话摘要类
- `HttpRequestAdapter` 接口 + Netty 实现
- `BasicAuthParser` + `DigestAuthParser` 认证模块
- `ExecutorTaskScheduler` 调度器
- `NettyHttpServer` HTTP 服务器
- `TR069Handler` 请求处理器
- `NettySessionManager` Session 管理
- **`embedded-tr069-server-test/` 测试程序模块** → 独立可运行的测试程序

### Definition of Done

- [ ] JAR 包可通过 Maven 依赖引入其他项目
- [ ] `TR069Server.builder().dataSource(ds).build().start()` 可正常启动
- [ ] CPE 设备可连接并完成 Inform → GetParameterValues → SetParameterValues 完整流程
- [ ] Basic/Digest 认证正常工作
- [ ] 事件监听器正确回调
- [ ] 后台调度任务正常运行
- [ ] `server.stop()` 可正常停止，资源正确释放
- [ ] 现有 `tr069` 模块代码未被修改（仅通过适配器复用）
- [ ] 多实例可同时运行（不同端口）
- [ ] **测试程序可独立启动，连接 MySQL 数据库运行**

### Must Have

- 打包为 JAR 库，可被其他项目依赖
- 清晰的 Builder API 创建服务器
- 事件监听接口 (TR069ServerListener)
- 不依赖 Spring Boot
- Netty HTTP 服务器
- 支持启停和状态查询
- 复用现有核心逻辑
- Basic + Digest 认证
- 三个后台调度任务
- 高并发 (I/O 线程与业务线程隔离)
- 线程安全，支持多实例运行
- **JDK 8 兼容**

### Must NOT Have (Guardrails)

- 不修改 `tr069` 模块中的现有核心逻辑文件 (methods/, xml/, base/)
- 不引入 Spring Boot 依赖
- 不引入 Servlet API 依赖 (在新模块中)
- 不破坏现有 `tr069` 模块的功能
- 不在 Netty Worker 线程中执行阻塞操作 (数据库调用等)
- 不使用静态变量存储状态（影响多实例运行）
- 不依赖外部配置文件（配置通过 API 传入）

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed.

### Test Decision

- **Infrastructure exists**: NO (新模块)
- **Automated tests**: YES (tests-after)
- **Framework**: JUnit 5

### QA Policy

Every task MUST include agent-executed QA scenarios.
Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **API/Backend**: Use Bash (curl) - Send requests, assert status + response fields
- **Library/Module**: Use Bash (java/junit) - Import, call functions, verify output

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation - 接口定义 + API设计):
├── Task 1: 创建 Maven 模块结构 + pom.xml (JAR打包配置) [quick]
├── Task 2: 定义 HttpRequestAdapter 接口 [quick]
├── Task 3: 定义 TR069ServerConfig 配置类 [quick]
├── Task 4: 定义 TR069ServerListener 事件接口 + SessionSummary [quick]
├── Task 5: 定义 AuthenticationParser 接口 [quick]
├── Task 6: 定义 TaskScheduler 接口 [quick]
└── Task 7: 定义 SessionManager 接口 [quick]

Wave 2 (Core Implementation - 核心实现):
├── Task 8: 实现 HttpRequestAdapterImpl (Netty版) (depends: 2) [unspecified-high]
├── Task 9: 实现 BasicAuthParser (depends: 5) [unspecified-high]
├── Task 10: 实现 DigestAuthParser (depends: 5) [unspecified-high]
├── Task 11: 实现 ExecutorTaskScheduler (depends: 6) [quick]
├── Task 12: 实现 NettySessionManager (depends: 7) [unspecified-high]
└── Task 13: 适配 HTTPRequestResponseData (depends: 2) [deep]

Wave 3 (Integration - 集成):
├── Task 14: 实现 NettyHttpServer (depends: 8, 11) [deep]
├── Task 15: 实现 TR069Handler (depends: 8, 9, 10, 12, 13) [deep]
├── Task 16: 实现 TR069Server 主类 + Builder (depends: 3, 4, 14, 15) [unspecified-high]
├── Task 17: 集成后台调度任务 + 事件回调 (depends: 11, 16) [quick]
└── Task 18: 创建测试程序模块 embedded-tr069-server-test (depends: 16) [unspecified-high]

Wave FINAL (Verification - 验证):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
-> Present results -> Get explicit user okay

Critical Path: Task 2 → Task 8 → Task 13 → Task 15 → Task 16
Parallel Speedup: ~60% faster than sequential
Max Concurrent: 7 (Wave 1)
```

### Dependency Matrix

| Task | Depends On | Blocks | Wave |
|------|-----------|--------|------|
| 1 | - | 2-7, 8-17 | 1 |
| 2 | 1 | 8, 13 | 1 |
| 3 | 1 | 16 | 1 |
| 4 | 1 | 16, 17 | 1 |
| 5 | 1 | 9, 10 | 1 |
| 6 | 1 | 11 | 1 |
| 7 | 1 | 12 | 1 |
| 8 | 2 | 14, 15 | 2 |
| 9 | 5 | 15 | 2 |
| 10 | 5 | 15 | 2 |
| 11 | 6 | 14, 17 | 2 |
| 12 | 7 | 15 | 2 |
| 13 | 2 | 15 | 2 |
| 14 | 8, 11 | 16 | 3 |
| 15 | 8, 9, 10, 12, 13 | 16 | 3 |
| 16 | 3, 4, 14, 15 | 17, 18 | 3 |
| 17 | 4, 11, 16 | F1-F4 | 3 |
| 18 | 16 | F1-F4 | 3 |

### Agent Dispatch Summary

- **Wave 1**: **7** - T1-T7 → `quick`
- **Wave 2**: **6** - T8 → `unspecified-high`, T9 → `unspecified-high`, T10 → `unspecified-high`, T11 → `quick`, T12 → `unspecified-high`, T13 → `deep`
- **Wave 3**: **5** - T14 → `deep`, T15 → `deep`, T16 → `unspecified-high`, T17 → `quick`, T18 → `unspecified-high`
- **FINAL**: **4** - F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

### 任务详情

#### Wave 1: 基础接口定义

- [ ] **Task 1: 创建 Maven 模块结构 + pom.xml**
  - **What to do**:
    - 创建 `embedded-tr069-server/` 目录
    - 创建 `pom.xml`，配置 JAR 打包
    - 添加 Netty、DBI、common 依赖
    - 排除 Spring Boot 传递依赖
  - **References**:
    - `tr069/pom.xml` - 参考依赖配置
  - **Acceptance Criteria**:
    - `mvn compile -pl embedded-tr069-server` 成功
  - **QA Scenario**: 编译通过，无 Spring Boot 依赖

- [ ] **Task 2: 定义 HttpRequestAdapter 接口**
  - **What to do**:
    - 创建 `HttpRequestAdapter.java` 接口
    - 定义所有需要的方法: `getSessionId()`, `getRemoteAddr()`, `getRemoteHost()`, `getRemotePort()`, `getHeader()`, `getInputStream()`, `getContentLength()`
  - **References**:
    - `HTTPRequestResponseData.java:38,66-69` - 使用的方法
    - `SessionLogging.java:59,119` - getRemoteHost 使用
  - **Acceptance Criteria**:
    - 接口包含所有 HttpServletRequest 使用的方法

- [ ] **Task 3: 定义 TR069ServerConfig 配置类**
  - **What to do**:
    - 创建配置类，包含所有配置项
    - 提供默认值
    - 支持 Builder 模式
  - **References**: 见上方"配置项完整清单"

- [ ] **Task 4: 定义 TR069ServerListener 事件接口 + SessionSummary**
  - **What to do**:
    - 创建 `TR069ServerListener.java` 接口
    - 创建 `SessionSummary.java` 类
    - 定义所有事件回调方法

- [ ] **Task 5: 定义 AuthenticationParser 接口**
  - **What to do**:
    - 创建 `AuthenticationParser.java` 接口
    - 创建 `AuthenticationResult.java` 类

- [ ] **Task 6: 定义 TaskScheduler 接口**
  - **What to do**:
    - 创建 `TaskScheduler.java` 接口
    - 定义 `scheduleAtFixedRate()`, `scheduleWithCron()`, `shutdown()` 方法

- [ ] **Task 7: 定义 SessionManager 接口**
  - **What to do**:
    - 创建 `SessionManager.java` 接口
    - 定义会话创建、获取、删除方法

#### Wave 2: 核心实现

- [ ] **Task 8: 实现 HttpRequestAdapterImpl (Netty版)**
  - **What to do**:
    - 实现 `HttpRequestAdapter` 接口
    - 从 Netty `FullHttpRequest` 提取所有信息
    - 实现 Session ID 生成/获取逻辑
  - **References**:
    - `io.netty.handler.codec.http.FullHttpRequest`
  - **QA Scenario**: 单元测试验证所有方法正确返回

- [ ] **Task 9: 实现 BasicAuthParser**
  - **What to do**:
    - 解析 `Authorization: Basic xxx` 头
    - Base64 解码获取用户名密码
    - 返回 `AuthenticationResult`
  - **References**:
    - `security/BasicSpringSecurityConfig.java`

- [ ] **Task 10: 实现 DigestAuthParser**
  - **What to do**:
    - 解析 `Authorization: Digest xxx` 头
    - 实现 Digest 认证算法 (MD5, nonce, cnonce)
    - 返回 `AuthenticationResult`
  - **References**:
    - `security/DigestSpringSecurityConfig.java`
    - RFC 2617 Digest Access Authentication

- [ ] **Task 11: 实现 ExecutorTaskScheduler**
  - **What to do**:
    - 基于 `ScheduledExecutorService` 实现
    - 支持 `scheduleAtFixedRate()`
    - 支持优雅关闭

- [ ] **Task 12: 实现 NettySessionManager**
  - **What to do**:
    - 管理设备 Session
    - 提供会话创建、获取、删除
    - 支持超时清理

- [ ] **Task 13: 适配 HTTPRequestResponseData**
  - **What to do**:
    - 修改 `HTTPRequestResponseData` 构造函数
    - 接受 `HttpRequestAdapter` 而非 `HttpServletRequest`
    - 添加 `getRemoteHost()` 方法
    - 保持向后兼容
  - **Must NOT do**:
    - 不要破坏现有 `tr069` 模块的功能
  - **References**:
    - `HTTPRequestResponseData.java:32-63`
    - `SessionLogging.java:59,119`

#### Wave 3: 集成

- [ ] **Task 14: 实现 NettyHttpServer**
  - **What to do**:
    - 配置 Boss/Worker/Business 线程组
    - 配置 Channel Pipeline
    - 实现 `start()`, `stop()`, `isRunning()`
    - 配置 HTTP 编解码器
  - **References**:
    - Netty ServerBootstrap 文档
  - **QA Scenario**: 启动后能接收 HTTP 请求

- [ ] **Task 15: 实现 TR069Handler**
  - **What to do**:
    - 继承 `SimpleChannelInboundHandler<FullHttpRequest>`
    - 实现完整请求处理流程
    - 集成认证、XML解析、ProvisioningStrategy
    - 构建 HTTP 响应
    - 触发事件监听器
  - **References**:
    - `Tr069Controller.java:79-129`
    - 见上方"HTTP 请求处理流程"

- [ ] **Task 16: 实现 TR069Server 主类 + Builder**
  - **What to do**:
    - 实现 Builder 模式
    - 实现 `start()`, `stop()`, `isRunning()`
    - 实现 `kickDevice()`, `getConnectedDevices()`
    - 初始化 DBI、Syslog
    - 创建 TR069Properties
  - **References**:
    - 见上方"Syslog 独立初始化"
    - 见上方"Properties 适配方案"

- [ ] **Task 17: 集成后台调度任务 + 事件回调**
  - **What to do**:
    - 启动 ActiveDeviceDetectionTask (5分钟)
    - 启动 ScheduledKickTask (1秒)
    - 启动 MessageListenerTask (5秒)
    - 实现事件回调触发逻辑
  - **References**:
    - `Tr069Controller.java:138-162`
    - `background/*.java`

- [ ] **Task 18: 创建测试程序模块**
  - **What to do**:
    - 创建 `embedded-tr069-server-test/` 模块
    - 实现 `TR069ServerTestApp.java`
    - 实现 `TestListener.java`
    - 配置可执行 JAR 打包
  - **QA Scenario**: 
    - 运行测试程序，能启动服务器
    - 能接收 CPE 连接

### 模块结构

```
embedded-tr069-server/
├── pom.xml                              # JAR 打包配置
├── src/main/java/com/github/freeacs/embedded/
│   ├── TR069Server.java                 # 主入口类
│   ├── TR069ServerBuilder.java          # Builder 模式
│   ├── TR069ServerConfig.java           # 配置类
│   ├── TR069ServerListener.java         # 事件监听接口
│   ├── SessionSummary.java              # 会话摘要
│   ├── AuthMethod.java                  # 认证方式枚举
│   ├── config/
│   │   └── ConfigValidator.java         # 配置验证
│   ├── netty/
│   │   ├── NettyHttpServer.java         # Netty 服务器
│   │   ├── TR069ChannelInitializer.java # Channel 初始化
│   │   ├── TR069Handler.java            # 请求处理器
│   │   └── HttpRequestAdapterImpl.java  # 请求适配器
│   ├── auth/
│   │   ├── AuthenticationParser.java    # 认证解析接口
│   │   ├── BasicAuthParser.java         # Basic 认证
│   │   ├── DigestAuthParser.java        # Digest 认证
│   │   └── AuthenticationResult.java    # 认证结果
│   ├── scheduler/
│   │   ├── TaskScheduler.java           # 调度器接口
│   │   └── ExecutorTaskScheduler.java   # 实现
│   ├── session/
│   │   └── NettySessionManager.java     # Session 管理
│   └── adapter/
│       ├── HttpRequestAdapter.java      # 请求适配接口
│       └── HTTPRequestResponseDataAdapter.java # 数据适配
└── src/test/java/
    ├── TR069ServerTest.java             # 主类测试
    ├── LifecycleTest.java               # 生命周期测试
    └── integration/
        └── DeviceConnectionTest.java    # 设备连接集成测试

embedded-tr069-server-test/              # 测试程序模块 (独立运行)
├── pom.xml                              # 可执行 JAR 配置
├── src/main/java/com/github/freeacs/embedded/test/
│   ├── TR069ServerTestApp.java          # 主类，main 入口
│   ├── DatabaseConfig.java              # 数据库配置
│   └── TestListener.java                # 测试用事件监听器
├── src/main/resources/
│   ├── application.properties           # 数据库连接配置
│   └── logback.xml                      # 日志配置
└── src/test/java/
    └── IntegrationTest.java             # 集成测试
```

### 测试程序代码

**TR069ServerTestApp.java**:
```java
package com.github.freeacs.embedded.test;

import com.github.freeacs.embedded.*;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;

@Slf4j
public class TR069ServerTestApp {
    
    public static void main(String[] args) {
        log.info("=== TR069Server Test Application ===");
        
        // 1. 创建数据源
        HikariDataSource dataSource = createDataSource();
        
        // 2. 创建服务器
        TR069Server server = TR069Server.builder()
            .dataSource(dataSource)
            .port(8080)
            .contextPath("/tr069")
            .authMethod(AuthMethod.BASIC)
            .workerThreads(64)
            .listener(new TestListener())
            .build();
        
        // 3. 启动服务器
        log.info("Starting TR069Server on port 8080...");
        server.start();
        log.info("TR069Server started successfully!");
        log.info("TR-069 endpoint: http://localhost:8080/tr069");
        
        // 4. 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down TR069Server...");
            server.stop();
            dataSource.close();
            log.info("TR069Server stopped.");
        }));
        
        // 5. 等待用户输入退出
        log.info("Press 'q' + Enter to quit, or Ctrl+C to force shutdown");
        Scanner scanner = new Scanner(System.in);
        while (!"q".equalsIgnoreCase(scanner.nextLine())) {
            log.info("Connected devices: {}", server.getConnectedCount());
        }
        
        server.stop();
        dataSource.close();
    }
    
    private static HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://localhost:3306/acs");
        ds.setUsername("acs");
        ds.setPassword("acs");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        return ds;
    }
}
```

**TestListener.java**:
```java
package com.github.freeacs.embedded.test;

import com.github.freeacs.embedded.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestListener implements TR069ServerListener {
    
    @Override
    public void onServerStarted() {
        log.info("=== Server Started ===");
    }
    
    @Override
    public void onDeviceConnected(String unitId, String ipAddress) {
        log.info(">>> Device Connected: {} from {}", unitId, ipAddress);
    }
    
    @Override
    public void onDeviceDisconnected(String unitId) {
        log.info("<<< Device Disconnected: {}", unitId);
    }
    
    @Override
    public void onSessionCompleted(String unitId, SessionSummary summary) {
        log.info("✓ Session Completed: {} - Method: {}, Duration: {}ms", 
            unitId, summary.getMethod(), summary.getDurationMs());
    }
    
    @Override
    public void onError(String unitId, Throwable error) {
        log.error("✗ Error for device {}: {}", unitId, error.getMessage());
    }
    
    @Override
    public void onServerStopped() {
        log.info("=== Server Stopped ===");
    }
}
```

**application.properties**:
```properties
# Database Configuration
db.url=jdbc:mysql://localhost:3306/acs
db.username=acs
db.password=acs
db.pool.size=10

# TR069 Server Configuration
tr069.port=8080
tr069.contextPath=/tr069
tr069.authMethod=BASIC
tr069.workerThreads=64
```

### pom.xml 关键配置

```xml
<project>
    <artifactId>embedded-tr069-server</artifactId>
    <packaging>jar</packaging>
    
    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <java.version>1.8</java.version>
    </properties>
    
    <dependencies>
        <!-- 复用现有模块 -->
        <dependency>
            <groupId>com.github.freeacs</groupId>
            <artifactId>tr069</artifactId>
            <version>${project.version}</version>
            <exclusions>
                <!-- 排除 Spring Boot -->
                <exclusion>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>com.github.freeacs</groupId>
            <artifactId>dbi</artifactId>
        </dependency>
        
        <!-- Netty (JDK 8 兼容版本) -->
        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-all</artifactId>
            <version>4.1.100.Final</version>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- JDK 8 编译 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
            <!-- 生成可依赖的 JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <configuration>
                    <archive>
                        <manifest>
                            <addDefaultImplementationEntries>true</addDefaultImplementationEntries>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 测试程序 pom.xml

```xml
<project>
    <artifactId>embedded-tr069-server-test</artifactId>
    <packaging>jar</packaging>
    
    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>
    
    <dependencies>
        <!-- 依赖 embedded-tr069-server -->
        <dependency>
            <groupId>com.github.freeacs</groupId>
            <artifactId>embedded-tr069-server</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- 数据库连接池 -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <version>4.0.3</version>  <!-- JDK 8 兼容版本 -->
        </dependency>
        <dependency>
            <groupId>org.mariadb.jdbc</groupId>
            <artifactId>mariadb-java-client</artifactId>
            <version>3.1.4</version>
        </dependency>
        
        <!-- 日志 -->
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.12</version>  <!-- JDK 8 兼容版本 -->
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.30</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- JDK 8 编译 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
            <!-- 可执行 JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.4.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.github.freeacs.embedded.test.TR069ServerTestApp</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### 运行测试程序

```bash
# 设置 JDK 8 环境
export JAVA_HOME=/mnt/d/lijian/tools/jdk/jdk1.8.0_491_linux
export PATH=$JAVA_HOME/bin:$PATH

# 1. 编译整个项目
./mvnw clean package -DskipTests

# 2. 运行测试程序
java -jar embedded-tr069-server-test/target/embedded-tr069-server-test-3.1-SNAPSHOT.jar

# 或者使用 Maven
./mvnw exec:java -pl embedded-tr069-server-test \
    -Dexec.mainClass="com.github.freeacs.embedded.test.TR069ServerTestApp"

# 3. 测试 CPE 连接 (使用 curl 模拟)
curl -X POST http://localhost:8080/tr069 \
    -H "Content-Type: text/xml" \
    -H "SOAPAction: " \
    -u "device-001:secret" \
    -d @inform.xml
```

### 编译验证

```bash
# 检查编译目标版本 (确保是 JDK 8)
javap -verbose embedded-tr069-server/target/classes/com/github/freeacs/embedded/TR069Server.class | grep "major version"
# 应输出: major version: 52 (JDK 8 对应 major version 52)
```

---

## Final Verification Wave

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, run command). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .sisyphus/evidence/. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `mvn compile` + check for compiler warnings. Review all files for: unused imports, empty catches, console.log in prod, commented-out code. Check for Netty best practices: proper ByteBuf release, channel pipeline order, thread model compliance. Check AI slop: excessive comments, over-abstraction, generic names.
  Output: `Build [PASS/FAIL] | Quality [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high`
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test: server start/stop, CPE Inform flow, authentication, scheduled tasks. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff. Verify 1:1 — everything in spec was built, nothing beyond spec was built. Check "Must NOT do" compliance. Verify tr069 module core files were NOT modified. Flag unaccounted changes.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Wave 1**: `feat(embedded): add module scaffolding and interface definitions` - all Wave 1 files
- **Wave 2**: `feat(embedded): implement core adapters and authentication` - all Wave 2 files
- **Wave 3**: `feat(embedded): implement Netty server and main entry point` - all Wave 3 files
- Each commit: `mvn compile -pl embedded-tr069-server` before commit

---

## Success Criteria

### Maven 依赖使用

```xml
<!-- 其他项目引入依赖 -->
<dependency>
    <groupId>com.github.freeacs</groupId>
    <artifactId>embedded-tr069-server</artifactId>
    <version>3.1-SNAPSHOT</version>
</dependency>
```

### 编译验证

```bash
# 编译检查
./mvnw compile -pl embedded-tr069-server

# 打包 JAR
./mvnw package -pl embedded-tr069-server -DskipTests

# 安装到本地仓库
./mvnw install -pl embedded-tr069-server -DskipTests
```

### 集成测试代码

```java
// 测试用例示例
@Test
public void testServerLifecycle() {
    DataSource ds = createTestDataSource();
    
    TR069Server server = TR069Server.builder()
        .dataSource(ds)
        .port(18080)
        .build();
    
    assertFalse(server.isRunning());
    server.start();
    assertTrue(server.isRunning());
    assertEquals(0, server.getConnectedCount());
    
    server.stop();
    assertFalse(server.isRunning());
}

@Test
public void testDeviceConnection() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> connectedDevice = new AtomicReference<>();
    
    TR069Server server = TR069Server.builder()
        .dataSource(ds)
        .port(18080)
        .listener(new TR069ServerListener() {
            @Override
            public void onDeviceConnected(String unitId, String ip) {
                connectedDevice.set(unitId);
                latch.countDown();
            }
        })
        .build();
    server.start();
    
    // 模拟 CPE 连接
    sendInformRequest("device-001");
    
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals("device-001", connectedDevice.get());
    
    server.stop();
}
```

### Final Checklist

- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] JAR 包可被其他项目依赖
- [ ] No Spring Boot dependencies in embedded-tr069-server module
- [ ] tr069 module core files unmodified (except HTTPRequestResponseData adapter)
- [ ] Netty thread model correct (no blocking in Worker threads)
- [ ] Basic and Digest authentication working
- [ ] All 3 scheduled tasks running
- [ ] Server can start and stop cleanly
- [ ] Event listeners correctly invoked
- [ ] Multiple instances can run simultaneously (different ports) - 单实例优先
- [ ] No static state affecting multi-instance - BaseCache 保持不变
- [ ] Test program runs and accepts CPE connections
