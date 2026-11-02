<div align="center">

# MINEZ Geyser

面向 MINEZ 服务器维护的 [Geyser](https://github.com/GeyserMC/Geyser) Fork。

<a href="../README.md">English</a> ｜
<a href="README_zh-CN.md">简体中文</a>

<br>

<div>
<a href="https://github.com/GeyserMC/Geyser"><img src="https://img.shields.io/badge/upstream-2.12.1--SNAPSHOT-2196f3" alt="upstream"></a>
<a href="#版本号"><img src="https://img.shields.io/badge/version-2.12.1.1-4caf50" alt="version"></a>
<a href="#受支持的版本"><img src="https://img.shields.io/badge/Java-26.3-4caf50" alt="Java"></a>
<a href="../LICENSE"><img src="https://img.shields.io/badge/license-MIT-9e9e9e" alt="license"></a>
</div>

<div>
<img src="https://img.shields.io/badge/Bedrock-1.21.130-81c784" alt="Bedrock 1.21.130">
<img src="https://img.shields.io/badge/Bedrock-26.40%E2%80%9326.45-81c784" alt="Bedrock 26.40–26.45">
<img src="https://img.shields.io/badge/Bedrock-26.50%E2%80%9326.52-81c784" alt="Bedrock 26.50–26.52">
<img src="https://img.shields.io/badge/Bedrock-26.60-81c784" alt="Bedrock 26.60">
</div>

<br>

<a href="https://geysermc.org/wiki/geyser/">查阅文档</a> ｜
<a href="https://github.com/MINEZ/Geyser/issues">问题反馈</a> ｜
<a href="https://github.com/MINEZ/Geyser/releases">版本发布</a> ｜
<a href="#相对上游的改动">改动一览</a>

</div>

> [!IMPORTANT]
> **本项目是非官方 Fork，与 GeyserMC 团队没有关系，既未经其认可，也不由其提供支持。**
>
> 与本 Fork 有关的一切，请提交至[本仓库的 issue](https://github.com/MINEZ/Geyser/issues)，不要带到上游仓库或官方 Discord。
>
> 区分方法是先在未经修改的上游构建上复现：
>
> - **脱离本 Fork 仍会出现** —— 属于上游的问题，照常向上游反馈，不必也不要提及本 Fork。
> - **脱离本 Fork 便不再出现** —— 属于我们的问题，只应提交到这里。

## 关于

本仓库跟随上游 `master` 分支，仅承载 MINEZ 服务器所需的少量改动。除下文列出的内容外，其余部分与上游完全一致。

本 Fork 的目的是让网易《我的世界：中国版》的玩家（客户端内核为基岩版 1.21.130）与 Java 版玩家一同进入 MINEZ 的 Java 版服务器。中国版特有的协议改动尚未包含在内，目前支持的是国际版基岩版 1.21.130 客户端。

### 受支持的版本

| 版本 | 受支持的版本 |
| --- | --- |
| 基岩版 | 1.21.130、1.21.131、1.21.132（本 Fork 新增），26.40、26.41、26.42、26.43、26.44、26.45、26.50、26.51、26.52、26.60 |
| Java 版 | 26.3 |

基岩版 1.21.130 客户端只能经 RakNet 连接。上游默认的传输方式 `nethernet` 不接受 RakNet 连接，因此需要把 Geyser 配置中的 `bedrock.transport` 改为 `both`。服务端的基岩版端口默认仍为 UDP 19132。

上游不为版本打标签，我们以合并的方式跟随它的 `master` 分支：

```bash
git remote add upstream https://github.com/GeyserMC/Geyser.git   # 只需一次
git fetch upstream
git merge upstream/master
git submodule update --init --recursive
```

合并不会改变我们已有提交的哈希。上游修改 `gradle.properties` 中的 `version` 一行时会产生冲突，按[版本号](#版本号)一节的规则解决即可。

## 相对上游的改动

| 名称 | 类型 | 改动 |
| --- | --- | --- |
| 基岩版 1.21.130（协议 898） | 协议 | 新增 |
| 客户端无法解码的数据包 | 行为 | 修改 |
| 构建产物的文件名 | 构建 | 修改 |
| 版本号的显示 | 构建 | 修改 |

**基岩版 1.21.130。** 上游移除了 26.40 以下的全部基岩版版本，连同这些版本所需的数据文件与相应的处理一并删去。本 Fork 重新注册协议 898，并以“协议低于 26.40”这一个条件把两者接回来，因此 26.40 及以上版本的行为与上游完全一致。方块调色板、物品表、创造模式物品栏与物品组件取自上游的历史提交；物品标签取自上游最后一份 26.0 的数据，因为更早的那份缺少长矛的标签。1.21.130 之后新增了连接或拐角状态的方块，会去掉这些状态，只保留客户端认识的部分；有 68 个音效的新映射指向 1.21.130 没有的名称，改用此前的映射。配方在发送时合并成旧版的混合列表格式。

Java 26.1~26.3 新增、而 1.21.130 没有的内容以替身显示，不会因此断开连接：方块使用上游现有的回退表，新的地图图标显示为宝藏标记，新的探险家地图显示为普通的已填充地图。以下内容暂时还没有替身：

- 1.21.130 之后新增的约 140 个音效事件没有声音。
- 定位栏关闭，也不跟踪路径点。
- 新的实体类型照原名下发，客户端并不认识。

**客户端无法解码的数据包。** 上游假定它发送的每个数据包与枚举值都存在于客户端的协议中，遇到不存在的就会在编码时抛出异常，玩家随之断开连接。现在对于低于 26.40 的协议，客户端协议中没有的数据包，以及客户端没有的音效、粒子、关卡事件与实体事件，会在发送前被丢弃，玩家只是少看到这一个效果。各协议不支持的值在第一次用到时逐个试编码得出，不必随协议库维护一份列表。实体标志与实体数据类型不作过滤，因为 Geyser 目前没有用到 1.21.130 缺少的这类值。

**构建产物的文件名。** 上游的 JAR 文件名不带版本号，例如 `Geyser-Velocity.jar`。现在会带上本 Fork 的版本号，例如 `Geyser-Velocity-2.12.1.1.jar`，一眼就能分清不同的构建。

**版本号的显示。** `/geyser version` 与插件描述文件中显示本 Fork 的版本号，后接提交，例如 `2.12.1.1 (git-master-abc1234)`。

## 构建

```bash
./gradlew build
```

Windows 上改用 `gradlew.bat build`。构建需要 JDK 21 及以上版本；Fabric 与 NeoForge 模块用 JDK 25 编译，本机没有时 Gradle 会自动下载。构建还会运行游戏内测试，加上 `-x :gametest:runGametest` 可以跳过。

按平台取用对应的 JAR：

| 平台 | 文件 |
| --- | --- |
| Velocity | `bootstrap/velocity/build/libs/Geyser-Velocity-2.12.1.1.jar` |
| Spigot / Paper | `bootstrap/spigot/build/libs/Geyser-Spigot-2.12.1.1.jar` |
| BungeeCord | `bootstrap/bungeecord/build/libs/Geyser-BungeeCord-2.12.1.1.jar` |
| Fabric | `bootstrap/mod/fabric/build/libs/Geyser-Fabric-2.12.1.1.jar` |
| NeoForge | `bootstrap/mod/neoforge/build/libs/Geyser-NeoForge-2.12.1.1.jar` |
| 独立运行 | `bootstrap/standalone/build/libs/Geyser-Standalone-2.12.1.1.jar` |
| ViaProxy | `bootstrap/viaproxy/build/libs/Geyser-ViaProxy-2.12.1.1.jar` |

同一文件夹中还有构建过程的中间产物，例如 `velocity-2.12.1-SNAPSHOT.jar`，不要安装它们。

## 版本号

版本号由去掉 `-SNAPSHOT` 后缀的上游版本加上本 Fork 的修订号组成：在 `2.12.1.1` 中，`2.12.1` 来自 `gradle.properties` 中的 `version` 一行，末尾的 `.1` 是同一文件中的 `minezRevision`。`version` 一行本身与上游保持一致，因此只有上游修改它时才会产生冲突。

- 合并时带来了新的上游版本：采用上游的 `version` 一行，并将 `minezRevision` 重新设为 `1`；
- 本 Fork 的其他每次发布，递增 `minezRevision`，例如从 `2.12.1.1` 到 `2.12.1.2`。

同时更新两份 README 顶部的版本徽章，以及[构建](#构建)一节中的文件名。

上游项目页面上的下载链接指向官方构建，**不包含**本 Fork 的改动。

## 上游

Geyser 由 [GeyserMC](https://geysermc.org/) 开发。Geyser 本身的安装与配置，仍应参阅[官方文档](https://geysermc.org/wiki/geyser/)，或前往官方 [Discord](https://discord.gg/geysermc) 咨询——但仅限于上游原本提供的内容，不包括本 Fork 的任何改动。

此处的改动为单一服务器的需求而作，独立维护。请勿将相关讨论带入上游的各个渠道。

## 许可证

Geyser 以 MIT 许可证开源，版权归 GeyserMC 所有（2019-2026）。本 Fork 的修改同样适用该许可证。完整条款见 [LICENSE](../LICENSE)。
