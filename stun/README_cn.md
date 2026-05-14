FreeACS Fusion - STUN Server
============================

本项目只是整个产品的一部分。完整信息请访问：
http://www.freeacs.com/

依赖
----
https://github.com/freeacs/common.git  
我建议将此项目作为部署装配项目引用

https://github.com/freeacs/dbi.git  
我建议将此项目作为部署装配项目引用

https://github.com/freeacs/prov.git  
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
包/导航视图：部署装配项目引用 freeacs-common、dbi 和 prov 项目
包/导航视图：在类路径中指定 Library->Server runtime - 例如：使用已安装的 Tomcat 7 服务器

概述
----
STUN 服务器用于支持 TR-111。该项目基于 JStun 构建，
只是稍作修改。最有趣的事情发生在 StunServer 中 - 
在内部类 StunServerReceiverThread 中。TR-069 服务器通过
Common 项目中提供的消息系统与 TR-069 服务器通信。
Kick 类负责监听来自 DBI 的消息。
