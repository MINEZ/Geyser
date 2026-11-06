<div align="center">

# MINEZ Geyser

面向 MINEZ 服务器维护的 [Geyser](https://github.com/GeyserMC/Geyser) Fork。

<a href="../README.md">English</a> ｜
<a href="README_zh-CN.md">简体中文</a>

<br>

<div>
<a href="https://github.com/GeyserMC/Geyser"><img src="https://img.shields.io/badge/upstream-2.12.1--SNAPSHOT-2196f3" alt="upstream"></a>
<a href="#版本号"><img src="https://img.shields.io/badge/version-2.12.1--b1280.16-4caf50" alt="version"></a>
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
| 自签名的中国版登录 | 行为 | 新增 |
| 供插件使用的登录闸门与网易身份 | 接口 | 新增 |
| 供插件使用的网易商城通知 | 接口 | 新增 |
| MINEZ 客户端模组的服务器菜单按钮 | 行为 | 新增 |
| 基岩版中的对话框 | 行为 | 修改 |
| 点击后重新打开对话框 | 行为 | 修改 |
| 客户端正忙、无法打开的表单 | 行为 | 修改 |
| 基岩版无法显示的实体 | 行为 | 修改 |
| 切换维度时的下坠 | 行为 | 修改 |
| 文本中内联的图标与玩家头像 | 行为 | 修改 |
| `bedrock.netease` | 配置 | 新增 |
| `bedrock.transport: raknet` | 配置 | 保留 |
| 客户端无法解码的数据包 | 行为 | 修改 |
| 1.21.130 加载界面期间的传送 | 行为 | 修改 |
| 构建产物的文件名 | 构建 | 修改 |
| 版本号的显示 | 构建 | 修改 |
| 启动提示与反馈链接 | 行为 | 修改 |

**中国版客户端。** 网易的客户端只经 RakNet 连接。本 Fork 分五部分接入，都只对中国版的会话生效：

- **RakNet。** 客户端报的 RakNet 协议版本是 8，批处理与压缩却与 11 相同：VarInt 包头，`RequestNetworkSettings` 之前不压缩。现在 RakNet 在 11 之外也接受 8，在连接上记下客户端报的版本，并按 11 搭建管线。协商之后与国际版一样使用 raw deflate；个别构建发来的带 zlib 头的流同样能读。
- **协议。** 中国版 1.21.130 有自己的编解码器：以国际版协议 898 的编解码器为基础，按网易的改动修改 `PlayerAuthInput`、`Text`、`CommandRequest`、`Animate`、`ResourcePackStack`、`ContainerOpen`、`SetPlayerInventoryOptions`、`BiomeDefinitionList`、`PlayerEnchantOptions`，以及容器槽位类型与粒子的 ID。网易专有的数据包（例如 Mod SDK 的 `PyRpc`）能被识别，都不转发给 Java 端：其中网易商城的通知交给插件，MINEZ 客户端模组的服务器菜单按钮打开服务器菜单，见下文，其余记一条 debug 日志后丢弃。国际版会话使用的编解码器不变。
- **登录。** 登录链须由网易的根公钥或 `bedrock.netease.trusted-root-keys` 中的根公钥签发，签名与有效期的检查与微软的登录链相同。登录与其中的加密握手不再经过协议库的 `EncryptionUtils`，它在加载时会访问微软的服务；只允许中国版客户端加入时，Geyser 也不会因为访问不了这些服务而在启动时停止。开启 `debug-mode` 后，每次中国版登录都会在日志中写出登录链的信息：每一段的签发者、密钥指纹与有效期，完整的根公钥，以及 `env`、`engineVersion`、`patchVersion` 等字段，但不会写出令牌或网易会话 ID。
- **身份。** 网易 UID 等身份信息保存在会话上，插件可以读取，见下文的登录闸门。没有登录闸门时，Java 端的身份仍按 `auth-type` 处理：玩家以登录链中的名字进入；名字中有 Java 版不允许的字符时，改为 `NE` 加网易 UID。只替换这些字符的话，两名玩家可能得到同一个名字，在离线模式下就是同一个账号。
- **方块与物品。** 中国版按自己的方块调色板给方块编号，与 1.21.130 的不同：多了 `micro_block`、没有 `chalkboard`，大部分方块的编号因此错开；工作台与熔炉还多一个 `custom_appearance` 状态。中国版的会话使用单独的调色板与方块、物品映射，在启动时由 1.21.130 的数据按这些差异生成，不另带数据文件；物品表中去掉中国版没有的 `board` 与 `chalkboard`。国际版 1.21.130 会话的调色板不变。

加上 `-DGeyser.RakSendCookie=false` 时取不到客户端报的 RakNet 版本，中国版客户端只能靠登录链识别，带 zlib 头的流也不再能读。尚未处理的有：确认其他玩家的皮肤（`ConfirmSkin`）、转发 `PyRpc`，以及发送中国版的 VIP 资源包与行为包。

**中国版以外的客户端。** `bedrock.netease.only-netease-clients` 开启时（默认如此），登录链不是由网易签发的客户端会在登录时被拒绝，提示为“客户端版本错误，请使用中国版客户端加入游戏。”为了让客户端显示这条提示，连接不会被强制关闭。关闭这一项后，其他客户端照上游的方式加入。

**自签名的中国版登录。** 网易 PC 客户端的开发版（“Mod PC”）自己签名登录链，只有一段、没有网易的签名，其中的内容都无法核实。这种登录与其他客户端一样被拒绝，除非开启 `bedrock.netease.allow-self-signed-logins`。只有连接方式像中国版（RakNet 8、协议 898）、并且带 `netease_uid` 等网易特有字段的才会被认出；国际版的自签名登录仍被拒绝。放行时，玩家不会以其自报的名字、UID 或 XUID 进入，而是使用隔离的开发者身份：`DEV` 加昵称中的字母、数字与下划线，总长不超过 16 个字符；一个这样的字符都没有时，改为 `DEV` 加由昵称算出的短散列。会话上把这一身份标为开发者身份，供以后的账号方案排除。只用于测试服与网易的审核跑测。

**供插件使用的登录闸门与网易身份。** 与 Geyser 在同一进程中运行的插件（例如与 Geyser-Velocity 装在一起的 Velocity 插件）现在可以参与基岩版玩家的登录。Geyser 的接口新增三项：

- `GeyserConnection#neteaseIdentity()` 给出会话经验证的中国版身份：网易 UID（只取登录链中的 `uid`，不以 XUID 代替）、XUID、昵称、`env` 与平台信息，登录链是否由网易内置的根公钥签发（`officialRoot`），以及是否为自签名登录的开发者身份（`developer`）。只在基岩版登录（含加密握手）完成之后才有，其中不含网易会话 ID 与任何令牌。
- `GeyserApi#registerLoginGate` 注册登录闸门，同时至多一个。基岩版玩家通过 Geyser 的验证、资源包下发完成之后，在 Geyser 连接 Java 端之前，会话等待闸门的结果，期间闸门可以显示 Cumulus 表单；为此 Geyser 先生成一个空白的等待世界，与正版模式下登录时的做法相同。闸门随后以自己选定的 Java 端名字放行玩家（1~16 个字符，不含空白；中文等其他字符原样传给 Java 端），可以在会话上附带一个自己的对象（`GeyserConnection#loginGateResult()`），或者以给定的提示断开。等待默认 10 分钟超时，闸门可以另定时限；玩家中途离开、超时或闸门被注销时会通知闸门。闸门结束时仍然打开着的表单会被关掉，不调用它们的处理器。闸门放行一名看过表单的玩家时，等待世界中显示标题“正在进入服务器”与副标题“正在载入角色资料，请稍候…”，直到进入 Java 端的世界，免得连接 Java 服务器的这段时间看起来像是卡住了。`auth-type` 为 `online` 时 Java 端的身份来自微软账号，不使用闸门。
- `GeyserApi#connectionByProxySessionId` 按代理分配的会话 ID，找出代理经 Geyser 的进程内直连（`use-direct-connection`）收到的连接所属的会话。Geyser-Velocity 在 Velocity 建立每个这样的连接时登记 Velocity 的 `InboundConnection#getSessionId()`（Velocity 4.3.0 起），插件由此可以判断（例如在 `PreLoginEvent` 中）一个连接是否真的来自同一进程中的 Geyser；经网络连入的连接一律查不到。直连无法建立时，Geyser 现在会记一条错误日志，说明基岩版玩家将改经网络连入。

没有注册闸门时，基岩版玩家的登录与此前完全相同，中国版玩家的 Java 端名字仍按上文“身份”一条的规则。

**供插件使用的网易商城通知。** 中国版客户端自带网易的网络服商城，从屏幕左上角的商城图标打开。玩家在其中购买成功，或点击“催促发货”时，客户端经 `PyRpc` 通知服务器。现在 Geyser 解析中国版会话发来的 `PyRpc`，对这两种通知在事件总线上发出 `NeteaseShopEvent`，同一进程中的插件可以据此立即发货。事件含会话、种类（`BUY_SUCCESS` 或 `URGE_SHIP`）、消息中的事件名、命名空间与系统名，以及消息中的数据（只读的映射）。网易专有的空包 `StoreBuySuccess`（ID 202）同样发出 `BUY_SUCCESS`。

网易的文档写明了这两个事件：Mod SDK 中为 `StoreBuySuccServerEvent` 与 `UrgeShipEvent`，网易的服务端插件中为 `player_buy_item_success` 与 `player_urge_ship`，但没有写明客户端实际发出的消息。因此事件名为这四个之一的消息都认作商城通知，不论它是 Mod SDK 的客户端事件（`ModEventC2S`，不限命名空间与系统名），还是以事件名为方法名的消息。其余的 `PyRpc` 照旧丢弃，都不转发给 Java 端。开启 `debug-mode` 后，每条丢弃的 `PyRpc` 都会在日志中写出方法名、事件名与数据的键，不写数据的值，便于查看客户端实际发出的消息。

解析有限制：负载超过 64 KiB、嵌套超过 16 层、元素超过 4096 个、含 MessagePack 的扩展类型或格式不符时，丢弃这个包并记一条 debug 日志，玩家不会被断开。事件只表示客户端这样声称，可能重复，也可能被伪造，插件应当经网易的订单接口查询玩家未发货的订单（短时间内的多次通知合并为一次查询），不能只凭事件发货。事件在 Geyser 的网络线程上发出，监听者不能阻塞；玩家还在登录闸门中、尚未连接 Java 端时也可能收到。国际版会话不会发出这个事件。

**MINEZ 客户端模组的服务器菜单按钮。** 基岩版没有副手键，中国版玩家按不了 Java 版打开 MINEZ 服务器菜单的 Shift+F。MINEZ 为中国版做的客户端模组在屏幕上放了一个菜单按钮，点击时发出一个 Mod SDK 的客户端事件（`ModEventC2S`）：命名空间 `MinezClient`，系统 `MenuSystem`，事件名 `OpenServerMenu`。Geyser 收到后替玩家执行 `/menu`，与玩家自己输入命令相同；每秒最多一次，并且只在玩家已进入 Java 服务器的世界之后，其余的记一条 debug 日志后丢弃。与其他客户端事件一样，任何人都能发出它，但它只做玩家自己输入命令也能做到的事。国际版会话不会触发。

**基岩版中的对话框。** Geyser 把 Java 版的对话框显示为基岩版的表单。现在补上了原先丢失的几样内容：

- MINEZ 的 Java 版客户端把对话框的纯文本正文按 Markdown 排版，MINEZ 的对话框因此写着标题、列表、表格与引用，表单中却原样显示这些标记。现在正文按那个客户端的规则为表单排版：标题去掉“#”并加粗；“-”“*”“+”开头的列表项换成“•”，有序列表保留序号，子列表按层级缩进；引用去掉“>”，行首加灰色的竖条，自身没有颜色的文字为灰色；有表头的表格改为列表，每行数据一项，第一格之后接全角冒号，多于两列时其余各格前写上表头，例如“• /back 冷却：无月卡 7 天，黄金月卡 1 小时”，表头与对齐行本身不显示，因为表单太窄，放不下并排的多列；没有表头的表格，各格以灰色的“|”隔开；分隔线换成一行灰色的横线；“**”为粗体，“*”为斜体，“~~”为删除线；行内代码与代码块原样显示，链接写出文字与地址，反斜杠转义的字符显示为字符本身。与那个客户端相同：换行即换行，下划线从不表示强调，删除线须用两个波浪号，不解析 HTML。写在块标记之前的颜色代码（例如 `§6## 标题`、`§7-§f 内容`）照常生效，列表的圆点或序号取所在行的第一个颜色，写在块标记之后的也算；表格转成的列表项，圆点取该行开始时生效的颜色，即使这个颜色写在不显示的表头中。只排版纯文本正文，标题、按钮与输入框不变。
- 基岩版的按钮没有悬停提示，最多也只显示两行。现在把按钮提示的第一行写在按钮文字的下一行；提示有多行时，在正文之后的“按钮说明”下连同按钮文字完整写出；这些按钮都有的一行（例如各分区共同的注意事项）不逐个重复，只在最后写一次。带输入框的对话框把按钮放在下拉框中，那里仍只显示按钮文字。
- Java 版的对话框画在变暗的游戏画面上，文字多为白色、黄色等浅色；基岩版的标题栏、按钮与下拉框却是浅灰色的，这些文字几乎看不清，正文区域则是深色的。现在按文字所在的背景替换颜色，色相尽量不变：标题、按钮与下拉框的选项中，白色与灰色换成黑色（按钮在悬停时变为绿色，深灰色在绿色上看不清），黄色与金色换成铜色（`§n`，不支持的客户端显示默认的深色），浅绿、浅蓝、浅紫换成对应的深色；正文中，黑色与深灰色换成灰色。格式代码不变。

**点击后重新打开对话框。** Java 版中 `after_action` 为 `none` 的对话框，点了按钮后仍留在屏幕上，直到服务器打开别的对话框；基岩版的表单点了就已关闭，上游因此立即重新打开它。按钮交给服务器处理时（执行命令或自定义动作），服务器紧接着打开的对话框（例如“返回”通向的那个）几乎与它同时到达，客户端来不及关掉重新打开的表单，把它留在新对话框之下：看起来像是同一个对话框打开了两次，关掉新对话框后露出的旧表单点什么都没有反应。现在这样的点击之后，Geyser 先等 1 秒再重新打开，期间服务器打开或关闭了对话框就不再打开。与 Java 版相同，服务器打开容器时，原先打开的对话框也随之关闭；点了会打开容器的按钮（例如“打开物品栏”）后，不会再过 1 秒重新打开对话框、把刚打开的容器关掉。

**基岩版无法显示的实体。** 上游遇到没有对应基岩版实体的原版实体（例如物品展示实体与方块展示实体）时，每生成一个就记一条带堆栈的错误。现在这类实体记一条 debug 日志后跳过，基岩版玩家仍然看不到它们。

**客户端正忙、无法打开的表单。** 基岩版客户端还在关闭某个界面（例如上一个表单）时，会拒绝打开表单，回复自己正忙。上游把这个回复当作玩家关闭了表单，于是服务器紧接着打开的对话框（例如“返回”按钮通向的那个）立即执行了关闭的动作，多半就直接关闭了。现在这样的表单每 250 毫秒重新发送一次，最多 20 次，之后才当作关闭。开启 `debug-mode` 时，每次重新发送都记一条日志。

**切换维度时的下坠。** 玩家切换维度时（Geyser 在代理后为玩家换服也是这样做的），Geyser 把玩家放在 (0, 32767, 0) 的空区块上，直到 Java 服务器给出玩家的位置。期间客户端照常计算重力，玩家看起来像是坠入了虚空，新的世界才出现。现在位置到达之前，玩家没有重力、不能移动，两者随位置一起恢复，Java 服务器自己设定的无重力照旧保留。在代理后换服时还要更早：客户端先重新进入配置阶段，Geyser 在这时就清空了玩家周围的区块，而玩家还在原来的世界中，于是在新服务器的登录包到达之前就已开始下坠、看到世界消失；现在从这里起就定身。新的世界还要过一会儿才加载出来，期间看起来空无一物，因此整个换服期间还给玩家一个只在客户端的失明效果，位置到达时再加一次（以防客户端在切换维度时把它清掉），1.5 秒后移除；Java 服务器自己给了失明时不移除，最长 10 秒后也会自行消失。

**文本中内联的图标与玩家头像。** Java 版 1.21.9 新增了 object 组件，可以在文本中内联显示图集中的图标或玩家的头像，例如聊天消息前的头像。基岩版不能显示它们，原先显示为 `[unknown player head]`、`[item/iron_ingot@items]` 这样的文字。现在这类组件有备用文字时显示备用文字；没有时，原版物品或方块的图标（`minecraft` 命名空间下的 `item/<名称>`、`block/<名称>`）显示为该物品或方块在玩家语言中的名称，其余的（包括玩家头像）不显示。

**`bedrock.netease`。** 配置中新增的一节：

```yaml
bedrock:
  netease:
    only-netease-clients: true
    trusted-root-keys: []
    allow-self-signed-logins: false
```

`trusted-root-keys` 填写额外信任的根公钥（Base64 编码的 X.509 公钥），用于网易以其他密钥签发的中国版构建。根公钥可以从这种客户端第一次登录时的 debug 日志中复制。`allow-self-signed-logins` 见上文。已有的配置文件不会被改写；没有这一节时，三项都取上面的默认值。

**基岩版 1.21.130。** 上游移除了 26.40 以下的全部基岩版版本，连同这些版本所需的数据文件与相应的处理一并删去。本 Fork 重新注册协议 898，并以“协议低于 26.40”这一个条件把两者接回来，因此 26.40 及以上版本的行为与上游完全一致。方块调色板、物品表、创造模式物品栏与物品组件取自上游的历史提交；物品标签取自上游最后一份 26.0 的数据，因为更早的那份缺少长矛的标签。1.21.130 之后新增了连接或拐角状态的方块，会去掉这些状态，只保留客户端认识的部分；有 68 个音效的新映射指向 1.21.130 没有的名称，改用此前的映射。配方在发送时合并成旧版的混合列表格式。

Java 26.1~26.3 新增、而 1.21.130 没有的内容以替身显示，不会因此断开连接：方块使用上游现有的回退表，新的地图图标显示为宝藏标记，新的探险家地图显示为普通的已填充地图。以下内容暂时还没有替身：

- 1.21.130 之后新增的约 140 个音效事件没有声音。
- 定位栏关闭，也不跟踪路径点。
- 新的实体类型照原名下发，客户端并不认识。

**只用 RakNet 的传输方式。** 上游移除了 `raknet` 这一传输方式，并会把写着它的配置改为 `nethernet`。本 Fork 保留它，写着它的配置也保持原样：中国版客户端只走 RakNet，而 NetherNet 加载的 WebRTC 库要求 glibc 2.34 与 OpenSSL 3，Debian 10 等较旧的系统没有，每次启动都会报错，NetherNet 也始终起不来。使用 `raknet` 时，Geyser 只在 UDP 的 `port` 上监听，不加载这个库，也用不到信令与 `webrtc-port`；只支持 NetherNet 的客户端（例如近期的国际版基岩版）因此无法连接。`nethernet` 与 `both` 两种传输方式的行为与上游一致。

**客户端无法解码的数据包。** 上游假定它发送的每个数据包与枚举值都存在于客户端的协议中，遇到不存在的就会在编码时抛出异常，玩家随之断开连接。现在对于低于 26.40 的协议，客户端协议中没有的数据包，以及客户端没有的音效、粒子、关卡事件与实体事件，会在发送前被丢弃，玩家只是少看到这一个效果。各协议不支持的值在第一次用到时逐个试编码得出，不必随协议库维护一份列表。实体标志与实体数据类型不作过滤，因为 Geyser 目前没有用到 1.21.130 缺少的这类值。

**1.21.130 加载界面期间的传送。** 实测中，中国版 1.21.130 的客户端在加载界面期间（进服后，以及切换维度时；Geyser 在代理后跨服也是这样切换的）不理会发给自己的 `MovePlayer`，加载界面结束时停在最后一个 `RespawnPacket` 给出的位置；国际版 1.21.130 与它协议相同，按同样的方式处理。服务端紧接着把玩家传送到同一处时（插件在进服时常这样做），上游认为客户端已经在那里，不再等它确认传送。1.21.130 的客户端其实还没到，Geyser 便把它停留的旧位置当作移动，以移动过快为由拒绝，并发出同样被客户端忽略的位置纠正：跨服之后，玩家停在世界上方的占位坐标并开始下落。现在对于低于 26.40 的协议，传送到与尚未确认的传送相同的位置时继续等待确认，客户端到达之前不理会它的移动，客户端报告加载或切换维度完成时立即重发传送。26.40 及以上的行为与上游一致。

**构建产物的文件名。** 上游的 JAR 文件名不带版本号，例如 `Geyser-Velocity.jar`。现在会带上本 Fork 的版本号，例如 `Geyser-Velocity-2.12.1-b1280.16.jar`，一眼就能分清不同的构建。

**版本号的显示。** `/geyser version` 与插件描述文件中显示本 Fork 的版本号，后接提交，例如 `2.12.1-b1280.16 (git-master-abc1234)`。

**启动提示与反馈链接。** 上游的启动信息请用户到它的 Discord 反馈问题。现在改为说明这是 MINEZ 的非官方 Fork，并指向[本仓库的 issue](https://github.com/MINEZ/Geyser/issues)；新写入的配置文件开头的说明与 `/geyser connectiontest` 中的链接同样改为本仓库。已有配置文件开头的说明只在配置版本变化时才会重写，需要时请手动修改。

## 构建

```bash
./gradlew build
```

Windows 上改用 `gradlew.bat build`。构建需要 JDK 21 及以上版本；Fabric 与 NeoForge 模块用 JDK 25 编译，本机没有时 Gradle 会自动下载。构建还会运行游戏内测试，加上 `-x :gametest:runGametest` 可以跳过。

按平台取用对应的 JAR：

| 平台 | 文件 |
| --- | --- |
| Velocity | `bootstrap/velocity/build/libs/Geyser-Velocity-2.12.1-b1280.16.jar` |
| Spigot / Paper | `bootstrap/spigot/build/libs/Geyser-Spigot-2.12.1-b1280.16.jar` |
| BungeeCord | `bootstrap/bungeecord/build/libs/Geyser-BungeeCord-2.12.1-b1280.16.jar` |
| Fabric | `bootstrap/mod/fabric/build/libs/Geyser-Fabric-2.12.1-b1280.16.jar` |
| NeoForge | `bootstrap/mod/neoforge/build/libs/Geyser-NeoForge-2.12.1-b1280.16.jar` |
| 独立运行 | `bootstrap/standalone/build/libs/Geyser-Standalone-2.12.1-b1280.16.jar` |
| ViaProxy | `bootstrap/viaproxy/build/libs/Geyser-ViaProxy-2.12.1-b1280.16.jar` |

同一文件夹中还有构建过程的中间产物，例如 `velocity-2.12.1-SNAPSHOT.jar`，不要安装它们。

## 版本号

版本号沿用上游正式构建的写法，并在末尾加上本 Fork 的修订号。在 `2.12.1-b1280.16` 中：

- `2.12.1` 是上游版本，来自 `gradle.properties` 中的 `version` 一行，去掉了 `-SNAPSHOT` 后缀。这一行与上游保持一致，因此只有上游修改它时才会产生冲突。
- `b1280` 是本 Fork 所基于的上游构建号，来自同一文件中的 `upstreamBuild`，与上游为同一提交发布的官方构建一致，即上游的 `2.12.1-b1280`。
- 末尾的 `.16` 是本 Fork 的修订号，来自同一文件中的 `minezRevision`。

上游为推送到 `master` 的每个提交编一个构建号。合并上游后，在 `https://download.geysermc.org/v2/projects/geyser/versions/<版本>/builds`（例如 `https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds`）的 `changes` 中找到所合并的上游提交对应的构建号，然后：

- 把 `upstreamBuild` 改为该构建号，上游的 `version` 一行有变化时一并采用，并将 `minezRevision` 重新设为 `1`；
- 本 Fork 的其他每次发布，递增 `minezRevision`，例如从 `2.12.1-b1280.16` 到 `2.12.1-b1280.17`。

同时更新两份 README 顶部的版本徽章，以及[构建](#构建)一节中的文件名。

上游项目页面上的下载链接指向官方构建，**不包含**本 Fork 的改动。

## 上游

Geyser 由 [GeyserMC](https://geysermc.org/) 开发。Geyser 本身的安装与配置，仍应参阅[官方文档](https://geysermc.org/wiki/geyser/)，或前往官方 [Discord](https://discord.gg/geysermc) 咨询——但仅限于上游原本提供的内容，不包括本 Fork 的任何改动。

此处的改动为单一服务器的需求而作，独立维护。请勿将相关讨论带入上游的各个渠道。

## 许可证

Geyser 以 MIT 许可证开源，版权归 GeyserMC 所有（2019-2026）。本 Fork 的修改同样适用该许可证。完整条款见 [LICENSE](../LICENSE)。

`core/src/main/java/org/geysermc/geyser/network/bedrock/netease/serializer/` 中的中国版序列化器改写自 [CloudburstMC Protocol](https://github.com/CloudburstMC/Protocol) 中对应的序列化器，原作品版权归 NukkitX 所有（2020），以 Apache License 2.0 授权。这些文件的开头均有说明，许可证全文见 [core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt](../core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt)，每个构建产物中也附带一份。
