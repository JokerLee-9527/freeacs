# 如何贡献

# 构建 FreeACS

系统要求：

* JDK 8
* Maven 1.X
* libncurses5
* geckodriver (https://github.com/mozilla/geckodriver/releases)

贡献 FreeACS 的第一步是从 GitHub 克隆 FreeACS 仓库并使用 Maven 构建项目。

## 构建 FreeACS：

要构建 FreeACS，请执行以下步骤：

* 在 GitHub 上 Fork FreeACS 仓库 -
  [https://github.com/freeacs/freeacs](https://github.com/freeacs/freeacs)。

* 克隆 fork 仓库的 master 分支（或首选分支），不包含提交历史：

      git clone https://github.com/<github-username>/freeacs --branch master --single-branch --depth 1

* 添加主 FreeACS 仓库作为 upstream 以获取更新：

      git remote add upstream https://github.com/freeacs/freeacs

* 构建 master 分支：

      cd freeacs
      ./mvnw test

* 创建可部署的 zip 文件：

      ./mvnw package
      
后者将在以下位置创建各模块的可部署 zip 文件：
        
      ./<module>/target/<module>-<version>-bin.zip

例如，运行 tr069（如果尚未设置数据库、加载 acs 表并添加 acs 用户，则会崩溃）：

      cd /tr069/target
      unzip tr069-<version>-bin.zip
      cd ./tr069-<version>/
      ./start.sh

您可以在发布页面找到 tables.zip，但使用 Docker 设置数据库会更容易。

# 进行更改

进行更改时，最好先创建一个 issue，并在任何提交和拉取请求中引用该 issue 编号。

## GitHub

使用以下流程将您的自定义更改提交到 GitHub：

* 基于 upstream/master 创建一个主题分支来保存您的更改：

      git fetch upstream
      git checkout -b my-custom-change upstream/master

* 提交逻辑工作单元，包括对 issue 编号的引用。例如：

      #234 Make the example in CONTRIBUTING imperative and concrete

* 彻底测试您的更改！确保您在一个环境中的更改不会破坏另一个环境中的某些内容。这并不总是很重要，但请记住这一点。

* 在将分支推送到 GitHub 上的 fork 之前，最好在 upstream/master 的更新版本上进行 rebase：

      git fetch upstream
      git rebase upstream/master

* 将分支中的更改推送到您的 fork：

      git push origin my-custom-change

* 向 freeacs/freeacs 仓库提交拉取请求。

完成！嗯，不完全是——请务必回应您的拉取请求中的评论和问题，直到它被关闭。

# 其他资源

* [GitHub 通用文档](http://help.github.com/)
* [GitHub 拉取请求文档](http://help.github.com/send-pull-requests/)
