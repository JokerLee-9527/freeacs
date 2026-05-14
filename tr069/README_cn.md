FreeACS Fusion - TR-069 Server
==============================

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
此服务器是本产品的重要组成部分，因为它运行与连接到它的各种设备
（路由器、ATA、RGW 等）的 TR-069 通信。服务器相当好地支持 TR-069，
支持 ACS 所需的所有方法（参见 http://www.broadband-forum.org/technical/download/TR-069_Amendment-5.pdf
第 3.6 章），唯一的例外是 AutonomousTransferComplete。

服务器响应迅速，提供广泛的功能。它已在 Telenor 测试了数十万台设备。
它还测试了一系列设备：

* Eltek R7121
* Eltek R7921
* Inteno 路由器
* Ping Communication RGW208EN
* Ping Communication IAD208AN
* Ping Communication NPA201E
* Speedtouch 585i
* Zyxel P2602

毫无疑问，服务器可以与任何其他 TR-069 设备配合使用，前提是设备
不包含非常棘手的错误（这种情况经常发生！）。

服务器提供的功能包括：

* TR-069 配置和固件配置
* TR-111 支持（与 STUN 服务器结合使用 https://github.com/freeacs/stun.git）
* TR-XXX 通用数据模型支持（TR-098、TR-104、TR-181 等）
* 自动发现新设备 - 在数据库中创建必要的对象
* 下载限制管理
* 不符合标准的设备的 Quirks 处理
* 执行复杂的配置、执行顺序、每周时间等
* 流量分散 - 避免设备连接的峰值负载问题
* 详细的日志记录，到文件和系统日志
* 两个测试系统，能够对设备进行真正复杂的测试

另请参阅 docs 文件夹中的文档

### 开发
```bash
docker run --name acs -e MYSQL_USER=acs -e MYSQL_PASSWORD=acs -e MYSQL_DATABASE=acs -e MYSQL_RANDOM_ROOT_PASSWORD=true -p 3306:3306 -d --platform=linux/amd64 mysql:5.7
mysql -h 127.0.0.1 -u acs -pacs -P 3306 acs < tables/src/main/resources/install.sql
```
