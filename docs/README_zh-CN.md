<div align="center">

# MINEZ Geyser

面向 MINEZ 服务器维护的 [Geyser](https://github.com/GeyserMC/Geyser) Fork。

<a href="../README.md">English</a> ｜
<a href="README_zh-CN.md">简体中文</a>

<br>

<div>
<a href="https://github.com/GeyserMC/Geyser"><img src="https://img.shields.io/badge/upstream-2.12.1--SNAPSHOT-2196f3" alt="upstream"></a>
<a href="#版本号"><img src="https://img.shields.io/badge/version-2.12.1--b1280.5-4caf50" alt="version"></a>
<a href="#受支持的版本"><img src="https://img.shields.io/badge/Java-26.3-4caf50" alt="Java"></a>
<a href="../LICENSE"><img src="https://img.shields.io/badge/license-MIT-9e9e9e" alt="license"></a>
</div>

<div>
<img src="https://img.shields.io/badge/%E4%B8%AD%E5%9B%BD%E7%89%88-3.10-81c784" alt="中国版 3.10">
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

本 Fork 的目的是让网易《我的世界：中国版》的玩家（客户端内核为基岩版 1.21.130）与 Java 版玩家一同进入 MINEZ 的 Java 版服务器。它支持中国版特有的协议与登录方式，默认只允许中国版客户端加入。

### 受支持的版本

| 版本 | 受支持的版本 |
| --- | --- |
| 中国版 | 3.10，内核为基岩版 1.21.130（本 Fork 新增） |
| 基岩版 | 1.21.130、1.21.131、1.21.132（本 Fork 新增），26.40、26.41、26.42、26.43、26.44、26.45、26.50、26.51、26.52、26.60 |
| Java 版 | 26.3 |

默认只允许中国版客户端加入，其他基岩版客户端会在登录时被拒绝。把 `bedrock.netease.only-netease-clients` 改为 `false`，上表中的基岩版也能加入。

基岩版 1.21.130 与中国版客户端只能经 RakNet 连接。上游默认的传输方式 `nethernet` 不接受 RakNet 连接，因此需要把 Geyser 配置中的 `bedrock.transport` 改为本 Fork 保留的 `raknet`；也接纳 NetherNet 客户端时改为 `both`。使用 `raknet` 时只用到 UDP 的 `port`。

上游不为版本打标签，我们以合并的方式跟随它的 `master` 分支：

```bash
git remote add upstream https://github.com/GeyserMC/Geyser.git   # 只需一次
git fetch upstream
git merge upstream/master
git submodule update --init --recursive
```

合并不会改变我们已有提交的哈希。每次合并后，按[版本号](#版本号)一节的规则更新版本号。

## 相对上游的改动

| 名称 | 类型 | 改动 |
| --- | --- | --- |
| 中国版客户端 | 协议 | 新增 |
| 基岩版 1.21.130（协议 898） | 协议 | 新增 |
| 中国版以外的客户端 | 行为 | 修改 |
| `bedrock.netease` | 配置 | 新增 |
| `bedrock.transport: raknet` | 配置 | 保留 |
| 客户端无法解码的数据包 | 行为 | 修改 |
| 构建产物的文件名 | 构建 | 修改 |
| 版本号的显示 | 构建 | 修改 |
| 启动提示与反馈链接 | 行为 | 修改 |

**中国版客户端。** 网易的客户端只经 RakNet 连接。本 Fork 分四部分接入，都只对中国版的会话生效：

- **RakNet。** 客户端报的 RakNet 协议版本是 8，批处理与压缩却与 11 相同：VarInt 包头，`RequestNetworkSettings` 之前不压缩。现在 RakNet 在 11 之外也接受 8，在连接上记下客户端报的版本，并按 11 搭建管线。协商之后与国际版一样使用 raw deflate；个别构建发来的带 zlib 头的流同样能读。
- **协议。** 中国版 1.21.130 有自己的编解码器：以国际版协议 898 的编解码器为基础，按网易的改动修改 `PlayerAuthInput`、`Text`、`CommandRequest`、`Animate`、`ResourcePackStack`、`ContainerOpen`、`SetPlayerInventoryOptions`、`BiomeDefinitionList`、`PlayerEnchantOptions`，以及容器槽位类型与粒子的 ID。网易专有的数据包（例如 Mod SDK 的 `PyRpc`）能被识别，记一条 debug 日志后丢弃。国际版会话使用的编解码器不变。
- **登录。** 登录链须由网易的根公钥或 `bedrock.netease.trusted-root-keys` 中的根公钥签发，签名与有效期的检查与微软的登录链相同。登录与其中的加密握手不再经过协议库的 `EncryptionUtils`，它在加载时会访问微软的服务；只允许中国版客户端加入时，Geyser 也不会因为访问不了这些服务而在启动时停止。开启 `debug-mode` 后，每次中国版登录都会在日志中写出登录链的信息：每一段的签发者、密钥指纹与有效期，完整的根公钥，以及 `env`、`engineVersion`、`patchVersion` 等字段，但不会写出令牌或网易会话 ID。
- **身份。** 网易 UID 等身份信息保存在会话上，留给以后的账号方案。Java 端的身份仍按 `auth-type` 处理：玩家以登录链中的名字进入；名字中有 Java 版不允许的字符时，改为 `NE` 加网易 UID。只替换这些字符的话，两名玩家可能得到同一个名字，在离线模式下就是同一个账号。

加上 `-DGeyser.RakSendCookie=false` 时取不到客户端报的 RakNet 版本，中国版客户端只能靠登录链识别，带 zlib 头的流也不再能读。尚未处理的有：中国版的方块调色板与 1.21.130 不同（多了 `micro_block`、没有 `chalkboard`，工作台与熔炉带 `custom_appearance` 状态），方块可能显示错误；也还没有确认其他玩家的皮肤（`ConfirmSkin`）、转发 `PyRpc`，或发送中国版的 VIP 资源包与行为包。

**中国版以外的客户端。** `bedrock.netease.only-netease-clients` 开启时（默认如此），登录链不是由网易签发的客户端会在登录时被拒绝，提示为“客户端版本错误，请使用中国版客户端加入游戏。”为了让客户端显示这条提示，连接不会被强制关闭。关闭这一项后，其他客户端照上游的方式加入。

**`bedrock.netease`。** 配置中新增的一节：

```yaml
bedrock:
  netease:
    only-netease-clients: true
    trusted-root-keys: []
```

`trusted-root-keys` 填写额外信任的根公钥（Base64 编码的 X.509 公钥），用于网易以其他密钥签发的中国版构建，例如 PC 客户端的开发版。根公钥可以从这种客户端第一次登录时的 debug 日志中复制。已有的配置文件不会被改写；没有这一节时，两项都取上面的默认值。

**基岩版 1.21.130。** 上游移除了 26.40 以下的全部基岩版版本，连同这些版本所需的数据文件与相应的处理一并删去。本 Fork 重新注册协议 898，并以“协议低于 26.40”这一个条件把两者接回来，因此 26.40 及以上版本的行为与上游完全一致。方块调色板、物品表、创造模式物品栏与物品组件取自上游的历史提交；物品标签取自上游最后一份 26.0 的数据，因为更早的那份缺少长矛的标签。1.21.130 之后新增了连接或拐角状态的方块，会去掉这些状态，只保留客户端认识的部分；有 68 个音效的新映射指向 1.21.130 没有的名称，改用此前的映射。配方在发送时合并成旧版的混合列表格式。

Java 26.1~26.3 新增、而 1.21.130 没有的内容以替身显示，不会因此断开连接：方块使用上游现有的回退表，新的地图图标显示为宝藏标记，新的探险家地图显示为普通的已填充地图。以下内容暂时还没有替身：

- 1.21.130 之后新增的约 140 个音效事件没有声音。
- 定位栏关闭，也不跟踪路径点。
- 新的实体类型照原名下发，客户端并不认识。

**只用 RakNet 的传输方式。** 上游移除了 `raknet` 这一传输方式，并会把写着它的配置改为 `nethernet`。本 Fork 保留它，写着它的配置也保持原样：中国版客户端只走 RakNet，而 NetherNet 加载的 WebRTC 库要求 glibc 2.34 与 OpenSSL 3，Debian 10 等较旧的系统没有，每次启动都会报错，NetherNet 也始终起不来。使用 `raknet` 时，Geyser 只在 UDP 的 `port` 上监听，不加载这个库，也用不到信令与 `webrtc-port`；只支持 NetherNet 的客户端（例如近期的国际版基岩版）因此无法连接。`nethernet` 与 `both` 两种传输方式的行为与上游一致。

**客户端无法解码的数据包。** 上游假定它发送的每个数据包与枚举值都存在于客户端的协议中，遇到不存在的就会在编码时抛出异常，玩家随之断开连接。现在对于低于 26.40 的协议，客户端协议中没有的数据包，以及客户端没有的音效、粒子、关卡事件与实体事件，会在发送前被丢弃，玩家只是少看到这一个效果。各协议不支持的值在第一次用到时逐个试编码得出，不必随协议库维护一份列表。实体标志与实体数据类型不作过滤，因为 Geyser 目前没有用到 1.21.130 缺少的这类值。

**构建产物的文件名。** 上游的 JAR 文件名不带版本号，例如 `Geyser-Velocity.jar`。现在会带上本 Fork 的版本号，例如 `Geyser-Velocity-2.12.1-b1280.5.jar`，一眼就能分清不同的构建。

**版本号的显示。** `/geyser version` 与插件描述文件中显示本 Fork 的版本号，后接提交，例如 `2.12.1-b1280.5 (git-master-abc1234)`。

**启动提示与反馈链接。** 上游的启动信息请用户到它的 Discord 反馈问题。现在改为说明这是 MINEZ 的非官方 Fork，并指向[本仓库的 issue](https://github.com/MINEZ/Geyser/issues)；新写入的配置文件开头的说明与 `/geyser connectiontest` 中的链接同样改为本仓库。已有配置文件开头的说明只在配置版本变化时才会重写，需要时请手动修改。

## 构建

```bash
./gradlew build
```

Windows 上改用 `gradlew.bat build`。构建需要 JDK 21 及以上版本；Fabric 与 NeoForge 模块用 JDK 25 编译，本机没有时 Gradle 会自动下载。构建还会运行游戏内测试，加上 `-x :gametest:runGametest` 可以跳过。

按平台取用对应的 JAR：

| 平台 | 文件 |
| --- | --- |
| Velocity | `bootstrap/velocity/build/libs/Geyser-Velocity-2.12.1-b1280.5.jar` |
| Spigot / Paper | `bootstrap/spigot/build/libs/Geyser-Spigot-2.12.1-b1280.5.jar` |
| BungeeCord | `bootstrap/bungeecord/build/libs/Geyser-BungeeCord-2.12.1-b1280.5.jar` |
| Fabric | `bootstrap/mod/fabric/build/libs/Geyser-Fabric-2.12.1-b1280.5.jar` |
| NeoForge | `bootstrap/mod/neoforge/build/libs/Geyser-NeoForge-2.12.1-b1280.5.jar` |
| 独立运行 | `bootstrap/standalone/build/libs/Geyser-Standalone-2.12.1-b1280.5.jar` |
| ViaProxy | `bootstrap/viaproxy/build/libs/Geyser-ViaProxy-2.12.1-b1280.5.jar` |

同一文件夹中还有构建过程的中间产物，例如 `velocity-2.12.1-SNAPSHOT.jar`，不要安装它们。

## 版本号

版本号沿用上游正式构建的写法，并在末尾加上本 Fork 的修订号。在 `2.12.1-b1280.5` 中：

- `2.12.1` 是上游版本，来自 `gradle.properties` 中的 `version` 一行，去掉了 `-SNAPSHOT` 后缀。这一行与上游保持一致，因此只有上游修改它时才会产生冲突。
- `b1280` 是本 Fork 所基于的上游构建号，来自同一文件中的 `upstreamBuild`，与上游为同一提交发布的官方构建一致，即上游的 `2.12.1-b1280`。
- 末尾的 `.5` 是本 Fork 的修订号，来自同一文件中的 `minezRevision`。

上游为推送到 `master` 的每个提交编一个构建号。合并上游后，在 `https://download.geysermc.org/v2/projects/geyser/versions/<版本>/builds`（例如 `https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds`）的 `changes` 中找到所合并的上游提交对应的构建号，然后：

- 把 `upstreamBuild` 改为该构建号，上游的 `version` 一行有变化时一并采用，并将 `minezRevision` 重新设为 `1`；
- 本 Fork 的其他每次发布，递增 `minezRevision`，例如从 `2.12.1-b1280.5` 到 `2.12.1-b1280.6`。

同时更新两份 README 顶部的版本徽章，以及[构建](#构建)一节中的文件名。

上游项目页面上的下载链接指向官方构建，**不包含**本 Fork 的改动。

## 上游

Geyser 由 [GeyserMC](https://geysermc.org/) 开发。Geyser 本身的安装与配置，仍应参阅[官方文档](https://geysermc.org/wiki/geyser/)，或前往官方 [Discord](https://discord.gg/geysermc) 咨询——但仅限于上游原本提供的内容，不包括本 Fork 的任何改动。

此处的改动为单一服务器的需求而作，独立维护。请勿将相关讨论带入上游的各个渠道。

## 许可证

Geyser 以 MIT 许可证开源，版权归 GeyserMC 所有（2019-2026）。本 Fork 的修改同样适用该许可证。完整条款见 [LICENSE](../LICENSE)。

`core/src/main/java/org/geysermc/geyser/network/bedrock/netease/serializer/` 中的中国版序列化器改写自 [CloudburstMC Protocol](https://github.com/CloudburstMC/Protocol) 中对应的序列化器，原作品版权归 NukkitX 所有（2020），以 Apache License 2.0 授权。这些文件的开头均有说明，许可证全文见 [core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt](../core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt)，每个构建产物中也附带一份。
