# Shell 模块 - CLI 命令行工具

**职责**: 提供命令行界面进行设备管理和自动化脚本执行。

**框架**: 独立应用 (无 Web 框架)

## 核心架构

```
输入 → Processor → Command → Menu → Execution
         ↓
      Context (层级导航)
```

## 上下文导航

类似文件系统的层级导航:
```
/ut:RouterA/pr:Default/un:device123>
cd ../gr:TestGroup
```

**上下文元素**:
- `ut:` - Unittype (设备类型)
- `pr:` - Profile (配置文件)
- `un:` - Unit (设备)
- `gr:` - Group (分组)
- `jo:` - Job (作业)
- `up:` - UnittypeParameter (参数)

## 关键组件

| 组件 | 职责 |
|------|------|
| `ACSShell` | 主入口 |
| `Processor` | 命令处理引擎 |
| `Command` | 命令解析和变量替换 |
| `Context` | 上下文状态管理 |
| `Script` | 脚本执行和流程控制 |

## 脚本语言特性

### 变量
```
setvar name value
echo ${name}
${_ParameterName}  # 访问当前设备参数
```

### 流程控制
```
while ${count} LT 10
    echo "Iteration ${count}"
    setvar count ${count} + 1
done
```

### 文件操作
```
cat /tmp/input.txt > output.log
```

## 菜单结构

```
menu/
├── GenericMenu.java   # 通用命令 (echo, sleep, help)
├── RootMenu.java      # 根级管理命令
├── UnitMenu.java      # 设备操作
├── ProfileMenu.java   # 配置文件操作
├── GroupMenu.java     # 分组操作
└── JobMenu.java       # 作业操作
```

## 批量操作

- `BatchStorage` 累积数据库操作
- 批量提交优化性能
- 默认批量大小: 1000

## 安全模式

- **Restricted Mode**: 限制文件系统访问
- 强制认证
- 基于用户的数据过滤

## 脚本示例

`shell/scripts/examples/` 和 `shell/scripts/testcases/`

## 入口

```bash
java -jar shell.jar
# 或脚本模式
java -jar shell.jar script.txt
```
