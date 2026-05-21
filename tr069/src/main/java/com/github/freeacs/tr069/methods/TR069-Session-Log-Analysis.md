# TR-069 会话日志分析

## 设备信息
| 字段 | 值 |
|------|-----|
| 设备ID | `202BC1-BM632w-000000` |
| 制造商 | Huawei Technologies Co., Ltd. |
| OUI | 202BC1 |
| 产品型号 | BM632w |
| 序列号 | 000000 |
| 软件版本 | V100R001IRQC56B017 |
| 硬件版本 | 40501 |

---

## 会话流程

```
时间线: 2026-05-21 14:20:54
设备:   202BC1-BM632w-000000
耗时:   187ms

CPE                                        ACS
 │                                            │
 │─── Inform (EventCode=4) ─────────────────→│ ① 设备上报参数变化
 │    EventCode: 4 VALUE CHANGE              │
 │                                            │
 │←── InformResponse ───────────────────────│ ② 确认收到
 │    MaxEnvelopes: 1                         │
 │                                            │
 │─── Empty (null) ─────────────────────────→│ ③ CPE 无更多请求
 │                                            │
 │←── GetParameterValues ───────────────────│ ④ ACS 请求关键参数
 │    请求 5 个参数                           │
 │                                            │
 │─── GetParameterValuesResponse ───────────→│ ⑤ 返回参数值
 │    PeriodicInformInterval: 69962           │
 │                                            │
 │←── SetParameterValues ───────────────────│ ⑥ ACS 修改配置
 │    PeriodicInformInterval: 70118           │
 │    ParameterKey: "No data in DB"           │
 │                                            │
 │─── SetParameterValuesResponse ───────────→│ ⑦ 确认修改成功
 │    Status: 0                               │
 │                                            │
 │←── Empty ─────────────────────────────────│ ⑧ 会话结束
 │                                            │
```

---

## 消息序列

| 序号 | 方向 | 消息类型 | 说明 |
|:----:|:----:|----------|------|
| ① | CPE → ACS | Inform | 事件码 4 (VALUE CHANGE)，设备参数变化触发 |
| ② | ACS → CPE | InformResponse | 确认收到 Inform |
| ③ | CPE → ACS | Empty | CPE 无更多请求，等待 ACS 指令 |
| ④ | ACS → CPE | GetParameterValues | 请求 5 个关键参数 |
| ⑤ | CPE → ACS | GetParameterValuesResponse | 返回参数值 |
| ⑥ | ACS → CPE | SetParameterValues | 修改 PeriodicInformInterval |
| ⑦ | CPE → ACS | SetParameterValuesResponse | Status=0 (成功) |
| ⑧ | ACS → CPE | Empty | 会话结束 |

---

## 参数变化

| 参数 | CPE 当前值 | ACS 设置值 | 变化 |
|------|-----------|-----------|------|
| PeriodicInformInterval | 69962 秒 | 70118 秒 | +156 秒 (+0.22%) |

---

## 关键信息

- **触发原因**: `4 VALUE CHANGE` - 设备参数发生变化
- **ParameterKey**: `"No data in DB"` - ACS 数据库无历史配置
- **会话耗时**: 187ms
- **流程标记**: `[IN(4)-INr] [EM-GPV] [GPVr-SPV] [SPVr-EM]`

---

## 为什么第 ④ 步请求 5 个参数？

这 5 个参数是 **ACS 必须始终获取的关键参数**，无论数据库中配置了什么。

### CPEParameters 定义的 6 个关键参数

| 参数 | 用途 |
|------|------|
| `DeviceInfo.SoftwareVersion` | 判断是否需要固件升级 |
| `ManagementServer.PeriodicInformInterval` | 控制定时上报间隔 |
| `ManagementServer.ConnectionRequestURL` | 用于 Kick (TR-111 主动连接) |
| `ManagementServer.ConnectionRequestUsername` | Kick 认证 |
| `ManagementServer.ConnectionRequestPassword` | Kick 认证 |
| `DeviceInfo.VendorConfigFile.` | 配置文件版本 |

### 请求逻辑

来自 `GetParameterValuesResponseCreateStrategy.addCPEParameters()`:

```java
for (String key : cpeParams.getCpeParams().keySet()) {
    if ((key.endsWith(".") && useVendorConfigFile)           // VendorConfigFile 以 "." 结尾
        || (paramValueMap.get(key) == null                    // 数据库中没有
            && utps.getByName(key) != null)) {                // 但 Unittype 中定义了
        paramValueMap.put(key, new ParameterValueStruct(key, "ExtraCPEParam"));
    }
}
```

### 本次请求的 5 个参数

1. `SoftwareVersion` — 判断固件升级
2. `PeriodicInformInterval` — 控制定时上报
3. `ConnectionRequestURL` — Kick 必需
4. `ConnectionRequestUsername` — Kick 认证
5. `ConnectionRequestPassword` — Kick 认证

`VendorConfigFile.` 未请求，可能原因：
- `useVendorConfigFile=false`（设备 Quirk）
- Unittype 中未定义该参数

### 总结

**这是 ACS 的硬编码策略——无论数据库配置了什么，这 5 个参数必须每次都从 CPE 获取，因为它们控制着：**

1. **固件升级** — SoftwareVersion
2. **定时上报** — PeriodicInformInterval  
3. **Kick (TR-111)** — ConnectionRequestURL/Username/Password
