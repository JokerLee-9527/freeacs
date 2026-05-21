# ProvisioningStrategy 架构分析

## 1. 核心设计模式：三层策略链

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ProvisioningStrategy                              │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    process(HTTPRequestResponseData)                 │ │
│  │                                                                     │ │
│  │  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐   │ │
│  │  │ 1. Request   │   │ 2. Decision  │   │ 3. Response          │   │ │
│  │  │   Process    │ → │   Strategy   │ → │   Create Strategy    │   │ │
│  │  │   Strategy   │   │              │   │                      │   │ │
│  │  └──────────────┘   └──────────────┘   └──────────────────────┘   │ │
│  │         ↓                  ↓                     ↓                 │ │
│  │    解析 CPE 请求      决定下一步操作         生成 XML 响应          │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## 2. TR-069 协议报文处理流程

### 典型交互流程（设备注册 → 配置下发）

```
CPE                                    ACS (FreeACS)
 │                                          │
 │────── Inform (设备注册) ────────────────→│
 │     <soap:Envelope>                      │
 │       <soap:Header>                      │ ① InformRequestProcessStrategy
 │         <cwmp:ID .../>                   │   - 解析 DeviceIdStruct
 │       </soap:Header>                     │   - 提取 EventList (事件码)
 │       <soap:Body>                        │   - 解析 ParameterList
 │         <cwmp:Inform>                    │   - 从数据库加载设备信息
 │           <DeviceId>                     │
 │             <OUI>...</OUI>               │ ② InformDecisionStrategy
 │             <ProductClass>...</ProductClass> │   - 决策: 返回 InformResponse
 │             <SerialNumber>...</SerialNumber> │
 │           </DeviceId>                    │
 │           <Event soap:arrayType="...">   │ ③ InformResponseCreateStrategy
 │             <EventStruct>                │   - 生成 InformResponse XML
 │               <EventCode>2 PERIODIC</EventCode>
 │             </EventStruct>               │
 │           </Event>                       │
 │           <ParameterList>...</ParameterList>
 │         </cwmp:Inform>                   │
 │       </soap:Body>                       │
 │     </soap:Envelope>                     │
 │                                          │
 │←────── InformResponse ───────────────────│
 │     <soap:Envelope>                      │
 │       <soap:Body>                        │
 │         <cwmp:InformResponse>            │
 │           <MaxEnvelopes>1</MaxEnvelopes> │
 │         </cwmp:InformResponse>           │
 │       </soap:Body>                       │
 │     </soap:Envelope>                     │
 │                                          │
 │────── Empty (HTTP 200) ─────────────────→│
 │                                          │ ④ EmptyDecisionStrategy
 │                                          │   - 写入系统参数 (LCT, FCT, IP)
 │                                          │   - 决策: 发起 GetParameterValues
 │                                          │
 │←────── GetParameterValues ──────────────│
 │     <soap:Envelope>                      │
 │       <soap:Body>                        │ ⑤ GetParameterValuesResponseCreateStrategy
 │         <cwmp:GetParameterValues>        │   - 生成参数请求列表
 │           <ParameterNames>               │
 │             <string>InternetGatewayDevice.</string>
 │           </ParameterNames>              │
 │         </cwmp:GetParameterValues>       │
 │       </soap:Body>                       │
 │     </soap:Envelope>                     │
 │                                          │
 │────── GetParameterValuesResponse ───────→│
 │     <soap:Envelope>                      │
 │       <soap:Body>                        │ ⑥ GetParameterValuesDecisionStrategy
 │         <cwmp:GetParameterValuesResponse>│   - 比较 CPE 值 vs ACS 值
 │           <ParameterList>                │   - 检查作业
 │             <ParameterValueStruct>       │   - 决策: SetParameterValues
 │               <Name>...</Name>           │   - 或 Download, Reboot 等
 │               <Value>...</Value>         │
 │             </ParameterValueStruct>      │
 │           </ParameterList>               │
 │         </cwmp:GetParameterValuesResponse>
 │       </soap:Body>                       │
 │     </soap:Envelope>                     │
 │                                          │
 │←────── SetParameterValues ──────────────│
 │     <soap:Envelope>                      │
 │       <soap:Body>                        │ ⑦ SetParameterValuesResponseCreateStrategy
 │         <cwmp:SetParameterValues>        │   - 生成参数设置列表
 │           <ParameterList>                │   - 包含 PeriodicInformInterval
 │             <ParameterValueStruct>       │
 │               <Name>...</Name>           │
 │               <Value xsi:type="xsd:string">...</Value>
 │             </ParameterValueStruct>      │
 │           </ParameterList>               │
 │         </cwmp:SetParameterValues>       │
 │       </soap:Body>                       │
 │     </soap:Envelope>                     │
 │                                          │
 │────── SetParameterValuesResponse ───────→│
 │     <soap:Envelope>                      │
 │       <soap:Body>                        │ ⑧ SetParameterValuesDecisionStrategy
 │         <cwmp:SetParameterValuesResponse>│   - 完成作业验证
 │           <Status>0</Status>             │   - 决策: Empty (结束会话)
 │         </cwmp:SetParameterValuesResponse>
 │       </soap:Body>                       │
 │     </soap:Envelope>                     │
 │                                          │
 │←────── Empty (HTTP 200) ─────────────────│
 │                                          │
```

## 3. 关键类详解

### 3.1 `ProvisioningStrategy` (编排核心)

```java
public void process(HTTPRequestResponseData reqRes) throws Exception {
    // 0. 预处理 - 解析 XML 报文
    Parser xml = reqRes.getRequestData().getParser();
    reqRes.getRequestData().setMethod(xml.getCwmpMethod().name());
    
    // 1. 请求处理 - 解析 CPE 发来的 SOAP 消息
    RequestProcessStrategy.getStrategy(xml.getCwmpMethod(), properties, dbi)
        .process(reqRes);
    
    // 2. 决策 - 决定下一步做什么
    DecisionStrategy.getStrategy(xml.getCwmpMethod(), properties, dbi)
        .makeDecision(reqRes);
    
    // 3. 响应生成 - 创建 SOAP 响应
    ProvisioningMethod responseMethod = getResponseMethod(reqRes);
    Response response = ResponseCreateStrategy.getStrategy(responseMethod, properties)
        .getResponse(reqRes);
    reqRes.getResponseData().setXml(response.toXml());
}
```

**关键点**：
- 使用 `Parser` 解析 SOAP/XML 报文，提取 `CwmpMethod`（方法类型）
- `DecisionStrategy` 会**修改** `responseData.method`，决定响应类型
- 响应方法可能与请求方法不同（如 Inform → InformResponse，Empty → GetParameterValues）

### 3.2 `InformRequestProcessStrategy` (Inform 请求处理)

```java
public void process(HTTPRequestResponseData reqRes) throws Exception {
    Parser parser = reqRes.getRequestData().getParser();
    
    // 1. 提取设备标识
    DeviceIdStruct deviceIdStruct = parser.getDeviceIdStruct();
    String unitId = getUnitId(deviceIdStruct);  // OUI-ProductClass-SerialNumber
    
    // 2. 解析事件码 (EventCode)
    EventList eventList = parser.getEventList();
    // 事件码含义:
    //   0 = BOOTSTRAP (首次启动)
    //   1 = BOOT (重启)
    //   2 = PERIODIC (定时上报)
    //   4 = VALUE_CHANGE (参数变化)
    //   6 = KICKED (TR-111 唤醒)
    //   7 = TRANSFER_COMPLETE (下载完成)
    
    // 3. 解析参数列表
    ParameterList parameterList = parser.getParameterList();
    // 提取 KeyRoot: InternetGatewayDevice. 或 Device.
    
    // 4. 从数据库加载设备信息
    DBIActions.updateParametersFromDB(sessionData, isDiscoveryMode, dbi);
    
    // 5. 发现模式: 自动创建 Unittype/Profile/Unit
    if (isDiscoveryMode && sessionData.isFirstConnect()) {
        DBIActions.writeUnittypeProfileUnit(sessionData, unitTypeName, unitId, dbi);
    }
}
```

### 3.3 `GetParameterValuesDecisionStrategy` (核心决策逻辑)

这是最复杂的决策策略，实现了完整的配置下发逻辑：

```java
public void makeDecision(HTTPRequestResponseData reqRes) throws Exception {
    SessionData sessionData = reqRes.getSessionData();
    ProvisioningMode mode = sessionData.getUnit().getProvisioningMode();
    
    if (mode == ProvisioningMode.REGULAR) {
        // 1. 检查是否有作业
        UnitJob uj = JobLogic.checkNewJob(sessionData, dbi, concurrentDownloadLimit);
        Job job = sessionData.getJob();
        
        if (job != null) {
            // 作业驱动配置
            jobProvisioning(reqRes, job, uj, isDiscoveryMode, publicUrl);
        } else {
            // 常规配置流程
            normalPriorityProvisioning(reqRes, publicUrl, concurrentDownloadLimit);
        }
    }
}

private void normalPriorityProvisioning(...) {
    // 优先级: Reset > Reboot > Download > Config
    
    if ("1".equals(reset)) {
        reqRes.getResponseData().setMethod(ProvisioningMethod.FactoryReset.name());
    } else if ("1".equals(reboot)) {
        reqRes.getResponseData().setMethod(ProvisioningMethod.Reboot.name());
    } else if (DownloadLogic.isSoftwareDownloadSetup(...)) {
        reqRes.getResponseData().setMethod(ProvisioningMethod.Download.name());
    } else {
        // 配置参数比较
        prepareSPV(sessionData);  // 比较 CPE vs ACS 参数
        reqRes.getResponseData().setMethod(ProvisioningMethod.SetParameterValues.name());
    }
}
```

### 3.4 `EmptyDecisionStrategy` (默认决策)

当 CPE 发送空请求（HTTP POST 无 SOAP Body）时触发：

```java
public void makeDecision(HTTPRequestResponseData reqRes) throws Exception {
    String prevResponseMethod = sessionData.getPreviousResponseMethod();
    
    if (ProvisioningMethod.Inform.name().equals(prevResponseMethod)) {
        // Inform 后: 发起 GetParameterNames 或 GetParameterValues
        if (sessionData.discoverUnittype()) {
            reqRes.getResponseData().setMethod(ProvisioningMethod.GetParameterNames.name());
        } else {
            reqRes.getResponseData().setMethod(ProvisioningMethod.GetParameterValues.name());
        }
    }
}
```

## 4. 数据流图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              SessionData (会话状态容器)                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│  unitId          │ 设备唯一标识                                                │
│  unit/unittype   │ 数据库对象                                                  │
│  keyRoot         │ "InternetGatewayDevice." 或 "Device."                       │
│  fromDB          │ Map<String, ParameterValueStruct> │ 数据库中的参数值           │
│  valuesFromCPE   │ List<ParameterValueStruct>        │ CPE 返回的参数值          │
│  toCPE           │ ParameterList                     │ 需要下发给 CPE 的参数     │
│  toDB            │ List<ParameterValueStruct>        │ 需要写入数据库的参数      │
│  job/jobParams   │ Job, Map<String, JobParameter>    │ 当前执行的作业            │
│  parameterKey    │ ParameterKey                      │ 用于验证配置完整性        │
│  eventCodes      │ String                            │ "2,4" (事件码列表)        │
└─────────────────────────────────────────────────────────────────────────────────┘
                                    ↑
                                    │ 读写
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     HTTPRequestResponseData (请求/响应数据)                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│  requestData.method    │ "Inform", "GetParameterValues", etc.                  │
│  requestData.parser    │ Parser (SAX 解析器)                                   │
│  responseData.method   │ 决策后的响应方法                                       │
│  responseData.xml      │ 最终生成的 SOAP 响应                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 5. 设计亮点

1. **策略模式分离关注点**：每种 TR-069 方法的三个阶段独立实现，易于维护和扩展

2. **状态机式决策**：`DecisionStrategy` 根据前一个方法决定下一个操作，形成完整的状态机

3. **会话持久化**：`SessionData` 存储在 `BaseCache` 中，支持 HTTP 会话级别的状态保持

4. **XML 安全解析**：
   ```java
   factory.setFeature(FEATURE_DISALLOW_DOCTYPE_DECL, true);  // 防止 XXE 攻击
   ```

5. **设备适配 (Quirks)**：
   ```java
   if (properties.isParameterkeyQuirk(sessionData)) {
       // 某些设备不支持 ParameterKey，跳过验证
   }
   ```

## 6. 典型场景分析

### 场景: 固件升级

```
1. CPE 发送 Inform (EventCode=2 PERIODIC)
2. ACS 返回 InformResponse
3. CPE 发送 Empty
4. ACS 返回 GetParameterValues (获取当前版本)
5. CPE 返回 GetParameterValuesResponse (包含 SoftwareVersion)
6. GetParameterValuesDecisionStrategy 检测到固件版本不匹配
   → DownloadLogic.isSoftwareDownloadSetup() 返回 true
   → 设置 responseType = Download
7. ACS 返回 Download (包含文件 URL、文件大小等)
8. CPE 返回 TransferComplete (下载完成通知)
9. TransferCompleteDecisionStrategy → 设置 responseType = TransferComplete
10. ACS 返回 TransferCompleteResponse
11. 会话结束
```

## 7. 支持的 TR-069 方法

| 方法 | 缩写 | 说明 |
|------|------|------|
| Inform | IN | 设备注册 |
| GetParameterValues | GPV | 获取参数 |
| SetParameterValues | SPV | 设置参数 |
| GetParameterNames | GPN | 获取参数名 |
| Download | DO | 固件下载 |
| TransferComplete | TC | 下载完成 |
| Reboot | RE | 重启 |
| FactoryReset | FR | 恢复出厂 |
| GetRPCMethods | GRPC | 方法列表 |

## 8. 总结

`ProvisioningStrategy` 是 FreeACS TR-069 模块的核心编排器，采用**三层策略模式**实现了：

| 层次 | 职责 | 关键类 |
|------|------|--------|
| Request Process | 解析 SOAP 请求，提取设备信息 | `InformRequestProcessStrategy` |
| Decision | 根据当前状态决定下一步操作 | `GetParameterValuesDecisionStrategy` (最复杂) |
| Response Create | 生成符合 TR-069 规范的 SOAP 响应 | `*ResponseCreateStrategy` |

这种设计使得每个 TR-069 方法的处理逻辑高度模块化，同时 `SessionData` 作为状态容器贯穿整个会话生命周期，实现了完整的协议状态机。
