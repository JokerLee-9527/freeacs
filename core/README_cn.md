FreeACS Fusion - Core Server
============================

本项目只是整个产品的一部分。完整信息请访问：
http://www.freeacs.com/

依赖
----
https://github.com/freeacs/common.git  
我建议将此项目作为部署装配项目引用

https://github.com/freeacs/dbi.git  
我建议将此项目作为部署装配项目引用

https://github.com/freeacs/shell.git  
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
包/导航视图：部署装配项目引用 freeacs-common、dbi 和 shell 项目
包/导航视图：在类路径中指定 Library->Server runtime - 例如：使用已安装的 Tomcat 7 服务器

概述
----
Core Server 也可以称为"后台服务器"，因为它就是做这类工作的。
简单来说，它执行清理、生成报告和其他后台处理。它在某些任务中
具有至关重要的作用（触发器处理、作业处理）。不应运行此服务器的
多个实例，那只会带来麻烦。它执行的功能包括：

* 删除旧作业
* 删除旧脚本
* 删除旧系统日志
* 检测设备心跳缺失（检查系统日志数据）
* 强制执行作业规则（必要时停止作业）
* 生成报告
* 执行脚本（这就是为什么有 Shell 依赖）
* 释放触发器

更多信息请参见 docs 文件夹
