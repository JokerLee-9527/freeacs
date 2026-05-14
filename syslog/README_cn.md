FreeACS Fusion - Syslog Server
==============================

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
此 syslog 服务器当然是为支持本产品而制作的，但理论上
它应该可以作为普通的 syslog 服务器工作。服务器具有许多功能，
在日志记录、磁盘空间处理、故障转移方面投入了大量精力
（我最近已禁用 - 太多问题）。

服务器的一个主要问题叫做 Syslog Event（在 Web/Shell 中指定）。
服务器将检查此类事件并根据它们执行某些操作。

更多信息请阅读 docs 文件夹
