FreeACS Fusion - Monitor Server
===============================

本项目只是整个产品的一部分。完整信息请访问：
http://www.freeacs.com/

依赖
----
https://github.com/freeacs/common.git  
我建议将此项目作为部署装配项目引用

https://github.com/freeacs/dbi.git  
我建议将此项目作为部署装配项目引用

应安装 Tomcat7（或等效的 Web 容器）

制作 WAR 文件和运行项目所需的 JAR 文件是项目的一部分
（WebContent/WEB-INF/lib），当然在制作 WAR 文件时可以
替换为更新的版本（如有必要）。


Eclipse 设置
------------
Git 视图：导入 git 仓库
Git 视图：从 git 仓库导入项目，作为一般项目导入
包/导航视图：将项目 facets 更改为 Java 1.7 和 Dynamic Web Module 3.0
包/导航视图：部署装配项目引用 freeacs-common 和 dbi 项目
包/导航视图：在类路径中指定 Library->Server runtime - 例如：使用已安装的 Tomcat 7 服务器


概述
----
Monitor 服务器负责监控本产品的所有其他服务器
（TR-069 服务器、Web 服务器、STUN 服务器等）。
它还负责在某些"情况"发生时发送电子邮件。简要总结：

* 监控本产品的所有其他服务器
* 提供 Web 界面查看所有服务器的状态 + 版本
* 提供一个 URL 用于外部监控系统（例如：HP OpenView）
* 每天早上 7 点发送心跳消息，表明产品正在运行
* 发送触发器通知

此服务器非常轻量/简单，它大量使用 Common 项目的调度器系统。
