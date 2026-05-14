FreeACS Fusion - DBI
====================

本项目只是整个产品的一部分。完整信息请访问：
http://www.freeacs.com/

依赖
----
https://github.com/freeacs/common.git  
我建议将此项目作为项目引用设置

https://github.com/freeacs/lib.git  
类路径中需要以下 jar 文件：
* jcommon-1.0.21.jar
* jfreechar-1.0.17.jar
* mysql-connector-java-5.1.28-bin.jar

Eclipse 设置
------------
Git 视图：导入 git 仓库
Git 视图：从 git 仓库导入项目，作为一般项目导入
包/导航视图：将项目 facets 更改为 Java 1.7
包/导航视图：项目引用 freeacs-common 项目
包/导航视图：从 freeacs-lib"项目"添加库到类路径

概述
----
DBI 是数据库接口（Database Interface）的缩写，提供从本产品的所有各种服务器和模块访问数据库的功能。以下是本项目提供的功能简要总结（括号中为类名引用）：

* 数据库接口 - 所有 SQL 都应该在这里找到！（当然有一些例外 - 总会有一些例外）
* 本产品所有重要/通用概念的对象模型
* 不常更改数据的缓存（XAPS）
* 服务器之间的消息系统（DBI、Inbox、Message）
* 权限/授权处理（Users、Permissions）
* 证书处理（可能不再有用 - 因为已开源）
* 报告生成（report-package）
* TR069 测试（tr069-package + xml 文件）
* 系统日志客户端（util-package 中的 SyslogClient）
* 数据库版本检查（XAPSVersionCheck）

接口和对象模型
--------------
要开始使用 DBI，有两个或三个重要的类：

* com.owera.xaps.dbi.DBI
* com.owera.xaps.dbi.XAPS
* com.owera.xaps.dbi.XAPSUnit

必须首先实例化 DBI 类。然后它会设置一个线程并永远运行。
从这个对象，您始终可以获取 XAPS 对象，该对象保存缓存（也是最重要的部分）对象模型。
每当缓存中的某些对象被更新（在另一个服务器/模块/等上），DBI 会从数据库获取信息并更新 XAPS 对象。
因此，每次要使用 XAPS 对象时，使用 DBI 上的 getXAPS() 方法很重要。

XAPS 对象包含有关 Unittypes 和 Unittype 的信息。一旦检索到一个 Unittype 对象，您就可以访问 Profiles、Groups、Jobs 和其他对象。
您必须学会从 Unittype 开始遍历对象模型。

XAPSUnit 对象提供检索有关 Units 信息的方法（Unit 是物理设备的逻辑表示）。
您可以搜索单元、更改单元等。

消息系统
--------
所有服务器/模块共享同一个数据库，对数据库的更改最终会被所有模块检测到。
前面提到的最重要对象的缓存系统使用此消息系统来相互更新模型状态的更改。

除此之外，还有一些特殊情况，服务器需要紧密通信以执行某些复杂操作。
典型示例是 Web 界面需要与 STUN 服务器通信以发起 TR-111 请求。
所有消息在 message 表中可见至少 90 秒。

权限/授权处理
-------------
权限和用户信息会提前读取，以决定用户将在哪个授权级别操作。
对写入操作的各种控制/检查分散到适当的写入例程中，
而读取操作仅在 XAPS 对象中控制。假设是，除非您对 XAPS 对象有适当的访问权限，
否则您将无法使用任何其他对象。

证书处理
--------
有一个 crypto-package 可以帮助生成证书。
证书生成内容本身可能有用，但对于本产品可能不再有用。
几年前引入它是为了能够销售产品的某些部分，证书当时是访问这个或那个功能所必需的。
开源后这似乎不再相关。我很可能会删除所有证书障碍 - 但将代码保留在此项目中，
以便可能出现的新用例使用。

报告生成
--------
报告在 Core 服务器中通过使用 report-package 编译。
报告在 Web 服务器中显示，同样使用此 report-package。
报告内容有时有点复杂，因为我努力使类通用。抱歉。

TR-069 测试
----------
提供一些类来测试 TR-069 数据模型。使用本项目中的 xml 文件来验证来自设备的数据。
