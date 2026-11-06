<div align="center">

# MINEZ Geyser

A fork of [Geyser](https://github.com/GeyserMC/Geyser) maintained for the MINEZ server.

<a href="README.md">English</a> ｜
<a href="docs/README_zh-CN.md">简体中文</a>

<br>

<div>
<a href="https://github.com/GeyserMC/Geyser"><img src="https://img.shields.io/badge/upstream-2.12.1--SNAPSHOT-2196f3" alt="upstream"></a>
<a href="#versioning"><img src="https://img.shields.io/badge/version-2.12.1--b1280.14-4caf50" alt="version"></a>
<a href="#supported-versions"><img src="https://img.shields.io/badge/Java-26.3-4caf50" alt="Java"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-9e9e9e" alt="license"></a>
</div>

<div>
<img src="https://img.shields.io/badge/China%20Edition-3.10-81c784" alt="China Edition 3.10">
<img src="https://img.shields.io/badge/Bedrock-1.21.130-81c784" alt="Bedrock 1.21.130">
<img src="https://img.shields.io/badge/Bedrock-26.40%E2%80%9326.45-81c784" alt="Bedrock 26.40–26.45">
<img src="https://img.shields.io/badge/Bedrock-26.50%E2%80%9326.52-81c784" alt="Bedrock 26.50–26.52">
<img src="https://img.shields.io/badge/Bedrock-26.60-81c784" alt="Bedrock 26.60">
</div>

<br>

<a href="https://geysermc.org/wiki/geyser/">Documentation</a> ｜
<a href="https://github.com/MINEZ/Geyser/issues">Issue Tracker</a> ｜
<a href="https://github.com/MINEZ/Geyser/releases">Releases</a> ｜
<a href="#changes-from-upstream">What's Changed</a>

</div>

> [!IMPORTANT]
> **This is an unofficial fork and is not affiliated with, endorsed by, or supported by the GeyserMC team.**
>
> Never take anything about this fork to the upstream repository or the official Discord. Use [this repository's issue tracker](https://github.com/MINEZ/Geyser/issues) instead.
>
> To tell the two apart, reproduce the problem on an unmodified upstream build first:
>
> - **It still happens without this fork** — an upstream problem. Report it upstream as you normally would, with no mention of this fork.
> - **It does not happen without this fork** — ours. It belongs here, and only here.

## About

This repository tracks the upstream `master` branch and adds a small set of changes needed by the MINEZ server. Everything not listed below is identical to upstream.

Its purpose is to let players of NetEase's Minecraft China Edition, whose clients run Bedrock 1.21.130, join the MINEZ Java server alongside Java players. It speaks China Edition's own protocol and login, and by default only lets China Edition clients join.

### Supported Versions

| Edition | Supported Versions |
| --- | --- |
| China Edition | 3.10, on Bedrock 1.21.130 (added by this fork) |
| Bedrock | 1.21.130, 1.21.131, 1.21.132 (added by this fork), 26.40, 26.41, 26.42, 26.43, 26.44, 26.45, 26.50, 26.51, 26.52, 26.60 |
| Java | 26.3 |

By default only China Edition clients may join, and every other Bedrock client is refused while logging in. Set `bedrock.netease.only-netease-clients` to `false` to let the Bedrock versions above join as well.

Bedrock 1.21.130 and China Edition clients connect over RakNet only. Upstream's default transport, `nethernet`, does not accept RakNet connections, so set `bedrock.transport` in Geyser's config to `raknet`, which this fork keeps, or to `both` to accept NetherNet clients as well. With `raknet`, only the UDP `port` is used.

Upstream does not tag releases, so we follow its `master` branch by merging:

```bash
git remote add upstream https://github.com/GeyserMC/Geyser.git   # once
git fetch upstream
git merge upstream/master
git submodule update --init --recursive
```

Merging keeps our commit hashes unchanged. After each merge, update the version as described under [Versioning](#versioning).

## Changes from Upstream

| Name | Type | Change |
| --- | --- | --- |
| China Edition clients | Protocol | Added |
| Bedrock 1.21.130 (protocol 898) | Protocol | Added |
| Clients other than China Edition | Behaviour | Changed |
| Self-signed China Edition logins | Behaviour | Added |
| Login gate and NetEase identities for plugins | API | Added |
| NetEase shop notifications for plugins | API | Added |
| Server menu button of the MINEZ client mod | Behaviour | Added |
| Dialogs on Bedrock | Behaviour | Changed |
| Reopening a dialog after a click | Behaviour | Changed |
| Forms the client is too busy to open | Behaviour | Changed |
| Falling while changing dimension | Behaviour | Changed |
| Inline sprites and player heads | Behaviour | Changed |
| `bedrock.netease` | Config | Added |
| `bedrock.transport: raknet` | Config | Kept |
| Packets the client cannot decode | Behaviour | Changed |
| Teleports during 1.21.130 loading screens | Behaviour | Changed |
| Artifact file names | Build | Changed |
| Version string | Build | Changed |
| Startup notice and support links | Behaviour | Changed |

**China Edition clients.** NetEase's clients connect over RakNet only. This fork handles them in five parts, which apply to China Edition sessions alone:

- **RakNet.** They report RakNet protocol 8, yet batch and compress like 11: VarInt packet headers, and no compression before `RequestNetworkSettings`. RakNet now accepts 8 alongside 11, notes the reported version on the connection, and builds the pipeline of 11 for it. After negotiation they use raw deflate like international clients; batches that some builds send as zlib-wrapped streams are read as well.
- **Protocol.** China Edition 1.21.130 has its own codec, built on the international one for protocol 898 with NetEase's changes to `PlayerAuthInput`, `Text`, `CommandRequest`, `Animate`, `ResourcePackStack`, `ContainerOpen`, `SetPlayerInventoryOptions`, `BiomeDefinitionList` and `PlayerEnchantOptions`, and to the IDs of container slot types and particles. The packets only NetEase has, such as `PyRpc` of its Mod SDK, are recognised and never forwarded to the Java server: the notifications of NetEase's shop are passed on to plugins, the server menu button of MINEZ's client mod opens the server menu, see below, and the rest are dropped with a debug message. International sessions keep their codecs unchanged.
- **Login.** The login chain has to be signed by NetEase's root key, or by one listed in `bedrock.netease.trusted-root-keys`; its signatures and validity windows are checked as for Microsoft's. The login and its encryption handshake no longer go through the protocol library's `EncryptionUtils`, which contacts Microsoft's services when it loads, and while only China Edition clients may join, Geyser no longer stops at startup when it cannot reach them. With `debug-mode` on, every China Edition login logs its chain: the issuer, key fingerprints and validity of each entry, the full root key, and fields such as `env`, `engineVersion` and `patchVersion`, but never a token or the NetEase session ID.
- **Identity.** The NetEase UID and the other identity fields are kept on the session, and plugins can read them, see the login gate below. Without a login gate, the Java side still follows `auth-type`: a player joins under the name in their login chain, or as `NE` followed by their NetEase UID when that name has characters Java does not allow. Replacing just those characters could give two players the same name, and so the same account in offline mode.
- **Blocks and items.** China Edition numbers blocks by its own block palette, which differs from 1.21.130's: it has `micro_block` but no `chalkboard`, which shifts most block IDs, and the crafting table and furnaces carry a `custom_appearance` state. China Edition sessions get their own palette and block and item mappings, generated at startup from the 1.21.130 data by those differences rather than shipped as a data file; the item table drops `board` and `chalkboard`, which China Edition lacks. International 1.21.130 sessions keep the palette they had.

With `-DGeyser.RakSendCookie=false` the reported RakNet version is lost, so China Edition clients are told apart by their login chain alone, and zlib-wrapped batches are no longer read. Not covered yet: other players' skins are not confirmed (`ConfirmSkin`), nor is `PyRpc` forwarded, nor are China Edition's VIP resource packs and behaviour packs sent.

**Clients other than China Edition.** While `bedrock.netease.only-netease-clients` is on, which it is by default, any client whose login chain NetEase did not sign is refused while logging in with 客户端版本错误，请使用中国版客户端加入游戏。 ("Wrong client version, please join with the China Edition client."). The connection is not forcibly closed, so the client shows the message. Turned off, other clients join as upstream lets them.

**Self-signed China Edition logins.** The development build of NetEase's PC client ("Mod PC") signs its own login, a single entry with no NetEase signature, so nothing in it can be verified. Such a login is refused like any other unless `bedrock.netease.allow-self-signed-logins` is on. It is only recognised on a connection that behaves like China Edition (RakNet 8, protocol 898) and carries NetEase's own fields such as `netease_uid`; a self-signed international login is still refused. When let in, the player never joins as the name, UID or XUID they claim, but under an isolated developer identity: `DEV` followed by their nickname, keeping only letters, digits and underscores, at most 16 characters, or `DEV` followed by a short hash of the nickname when nothing is left. The session marks the identity as a developer one, for a later account scheme to leave out. Meant for test servers and NetEase's review runs only.

**Login gate and NetEase identities for plugins.** Plugins running in the same process as Geyser, such as a Velocity plugin next to Geyser-Velocity, can now take part in the login of Bedrock players. Geyser's API gains three things:

- `GeyserConnection#neteaseIdentity()` gives the verified China Edition identity of a session: the NetEase UID (only the `uid` of the login chain, never replaced by the XUID), the XUID, the nickname, `env` and the platform details, whether NetEase's built-in root key signed the chain (`officialRoot`), and whether it is the developer identity of a self-signed login (`developer`). It is only there once the Bedrock login, encryption handshake included, has finished, and it never holds the NetEase session ID or any token.
- `GeyserApi#registerLoginGate` registers a login gate, at most one at a time. Once a Bedrock player has passed Geyser's checks and received the resource packs, and before Geyser connects to the Java server, the session waits for the gate, which may show Cumulus forms meanwhile; for these, Geyser first spawns a blank waiting world, as it does for online-mode logins. The gate then lets the player in under a Java username of its choosing, up to 16 characters without whitespace (other characters, such as Chinese, are passed on as they are), optionally keeping an object of its own on the session (`GeyserConnection#loginGateResult()`), or disconnects them with a message. The wait times out after ten minutes unless the gate sets another limit, and the gate is told when the player leaves, the wait times out or the gate is unregistered. Forms still open when the gate ends are closed without calling their handlers. When the gate lets in a player who was shown forms, the waiting world shows the title 正在进入服务器 ("Joining the server") with the subtitle 正在载入角色资料，请稍候… ("Loading your character, please wait…") until the Java world is joined, so that the time spent connecting to the Java server does not look like a hang. With `auth-type: online` the Java identity comes from the Microsoft account, and the gate is not used.
- `GeyserApi#connectionByProxySessionId` finds the session behind a connection the proxy received over Geyser's in-process direct connection (`use-direct-connection`), by the session ID the proxy gave it. Geyser-Velocity registers Velocity's `InboundConnection#getSessionId()` (Velocity 4.3.0 and newer) for each such connection while Velocity sets it up, so a plugin can tell, for example in `PreLoginEvent`, whether a connection really comes from Geyser in the same process. Connections over the network are never found. When the direct connection cannot be set up, Geyser now logs an error saying that Bedrock players will join over the network instead.

Without a registered gate, Bedrock players join exactly as before, and the Java names of China Edition players still follow the rule under Identity above.

**NetEase shop notifications for plugins.** China Edition clients come with NetEase's own shop for network servers, opened from the shop icon at the top left of the screen. When a purchase there succeeds, or the player presses the button that urges delivery, the client tells the server through `PyRpc`. Geyser now decodes the `PyRpc` packets that China Edition sessions send, and for these two notifications fires `NeteaseShopEvent` on its event bus, so that plugins in the same process can deliver right away. The event carries the session, the kind (`BUY_SUCCESS` or `URGE_SHIP`), the event name, namespace and system name found in the message, and the message's data as a read-only map. NetEase's empty `StoreBuySuccess` packet (ID 202) fires `BUY_SUCCESS` as well.

NetEase documents the two events, as `StoreBuySuccServerEvent` and `UrgeShipEvent` in its Mod SDK and as `player_buy_item_success` and `player_urge_ship` in its server plugins, but not the message the client actually sends. A message therefore counts as a shop notification when its event name is any of these four, whether it is a Mod SDK client event (`ModEventC2S`) in any namespace and system, or a message named after the event itself. Every other `PyRpc` is still dropped, and none is forwarded to the Java server. With `debug-mode` on, each dropped `PyRpc` is logged with its method and event names and the keys of its data, never the values, which helps to see what a client really sends.

Decoding is bounded: a payload over 64 KiB, nesting deeper than 16 levels, more than 4096 elements, MessagePack extension types or any malformed data drop the packet with a debug message, and the player stays connected. The event only reports what the client claims. It may be repeated or forged, so a plugin should look up the player's undelivered orders through NetEase's order API, merging bursts into one lookup, rather than deliver anything on the event alone. It is fired on Geyser's network thread, so listeners must not block, and it may come while the player is still at the login gate, before the Java server is joined. International sessions never fire it.

**Server menu button of the MINEZ client mod.** Bedrock has no off-hand key, so China Edition players cannot press Shift+F, which opens MINEZ's server menu on Java. MINEZ's client mod for China Edition puts a menu button on the screen, which sends a Mod SDK client event (`ModEventC2S`) in namespace `MinezClient`, system `MenuSystem`, named `OpenServerMenu`. Geyser runs `/menu` for the player when it gets one, as if they had typed it, at most once a second, and only once they have spawned in the Java world; other requests are dropped with a debug message. Like any client event, it can be sent by anyone, and only does what the player could do by typing the command. International sessions never trigger it.

**Dialogs on Bedrock.** Geyser shows Java dialogs as Bedrock forms. Several things are now carried over that were lost:

- MINEZ's Java client lays out the plain-text bodies of dialogs as Markdown, so MINEZ's dialogs are written with headings, lists, tables and quotes, which forms showed as raw marks. Bodies are now laid out for forms by the rules of that client: headings lose their `#` and are bold; `-`, `*` and `+` list items get a `•`, ordered items keep their numbers, and nested lists are indented; quotes lose their `>` and get a grey bar, with text that has no colour of its own in grey; a table with a header becomes a list, one item per row, with a full-width colon after the first cell and, when there are more than two columns, each further cell labelled with its header, such as `• /back cooldown：none 7 days，gold 1 hour`, while the header and alignment rows themselves are not shown, as forms are too narrow for columns side by side; rows of a table without a header separate their cells with a grey `|`; thematic breaks become a grey line; `**` is bold, `*` italic and `~~` strikethrough; inline code and code blocks are shown as they are, links show their text and address, and backslash escapes are shown as the plain character. As in that client, a line break is a line break, underscores never mean emphasis, strikethrough needs two tildes, and HTML is not parsed. Colour codes written before a block mark, such as `§6## Title` or `§7-§f item`, apply as usual, and a list's bullet or number takes the first colour of its line, even one written after the mark. Only plain-message bodies are laid out; titles, buttons and inputs are not.
- Bedrock buttons have no tooltips, and show at most two lines. The first line of a button's tooltip is now written under its label; when a tooltip has more lines, all of them are written after the body, under 按钮说明 ("About the buttons"), with the button's label. Buttons in the dropdown of a dialog with inputs still show their label alone.
- Java draws dialogs over the dimmed game, so their text is mostly light, such as white or yellow; on Bedrock the title bar, buttons and dropdown are light grey, where such text can hardly be read, and the body is dark. Colours are now swapped for the background they sit on, keeping the hue where possible: on the title, buttons and dropdown options, white and grey become black (a hovered button turns green, where dark grey can hardly be read), yellow and gold become copper (`§n`, which clients without it show in the default dark colour), and light green, aqua and light purple become their dark shades; in the body, black and dark grey become grey. Formatting codes are kept.

**Reopening a dialog after a click.** A Java dialog whose `after_action` is `none` stays on screen after a click until the server shows another dialog, while a Bedrock form is gone once clicked, so upstream reopened the dialog at once. When the click was handled by the server (a command or a custom action), the dialog the server opened next, such as the one a Back button leads to, arrived almost together with it: the client could not close the reopened form in time and kept it under the new one, so the dialog seemed to open twice, and the stale form left after closing the new one did nothing when clicked. After such a click, Geyser now waits a second before reopening the dialog, and does not reopen it if the server has opened or closed a dialog meanwhile.

**Forms the client is too busy to open.** A Bedrock client that is still closing a screen, for example the previous form, refuses to open a form and answers that it is busy. Upstream took that answer as the player closing the form, so a dialog the server opened right away (such as the one a Back button leads to) ran its close action at once, and usually just closed. Such a form is now sent again every 250 milliseconds, up to 20 times, and only then treated as closed. With `debug-mode` on, each retry is logged.

**Falling while changing dimension.** When the player changes dimension, which is also how Geyser moves a player between servers behind a proxy, Geyser parks them at (0, 32767, 0) over empty chunks until the Java server sends their position. The client kept applying gravity meanwhile, so the player seemed to fall into the void before the new world appeared. Until that position arrives, the player now has no gravity and cannot move; both come back with the position, and a Java server's own no-gravity setting is kept. The new world also takes a moment to load, during which it looked empty, so the player is given a client-side blindness effect while changing dimension; it is removed a second after the position arrives, unless the Java server has given them blindness of its own, and in any case wears off after ten seconds.

**Inline sprites and player heads.** Java 1.21.9 added object components, which show an atlas sprite or a player's face inline in text, such as the face before a chat message. Bedrock cannot show them, and they came out as `[unknown player head]` or `[item/iron_ingot@items]`. Such a component now shows its fallback text when it has one; without one, a sprite of a vanilla item or block (`item/<name>` or `block/<name>` in the `minecraft` namespace) shows the name of that item or block in the player's language, and anything else, player heads included, shows nothing.

**`bedrock.netease`.** A new section of the config:

```yaml
bedrock:
  netease:
    only-netease-clients: true
    trusted-root-keys: []
    allow-self-signed-logins: false
```

`trusted-root-keys` takes further root keys, as Base64-encoded X.509 public keys, for China Edition builds that NetEase signs with other keys. Copy the key from the debug log of such a client's first login. `allow-self-signed-logins` is described above. Existing config files are not rewritten; without the section, all three options take the defaults above.

**Bedrock 1.21.130.** Upstream dropped every Bedrock version below 26.40, removing the data files those versions need along with the code paths that served them. This fork registers protocol 898 again and brings both back behind a single check for protocols below 26.40, so 26.40 and newer behave exactly as upstream. The block palette, item table, creative inventory and item components come from upstream's history; the item tags come from its last 26.0 data, since an older copy lacks the spear tags. Blocks that gained connection or corner states after 1.21.130 are stripped back to the states the client knows, and 68 sounds whose newer mappings point at names 1.21.130 lacks fall back to their earlier mappings. Recipes are merged into the older combined list format when they are sent.

Content that Java 26.1 to 26.3 added and 1.21.130 lacks is shown through stand-ins, so it never breaks the connection: blocks use upstream's existing fallback tables, new map decorations show as a treasure marker, and the newer explorer maps show as a plain filled map. Some things have no stand-in yet:

- The roughly 140 sound events added since 1.21.130 play no sound.
- The locator bar is turned off and waypoints are not tracked.
- New entity types are sent under their own identifiers, which the client does not know.

**RakNet-only transport.** Upstream removed the `raknet` transport, and now moves a config that names it over to `nethernet`. This fork keeps it, and leaves such a config as it is: China Edition clients only speak RakNet, and the WebRTC library NetherNet loads needs glibc 2.34 and OpenSSL 3, which older systems such as Debian 10 do not have, so every startup there logs an error and NetherNet never runs. With `raknet`, Geyser listens on the UDP `port` alone and never loads that library; signaling and `webrtc-port` are not used, and NetherNet-only clients, such as recent international Bedrock releases, cannot connect. The `nethernet` and `both` transports behave as upstream.

**Packets the client cannot decode.** Upstream assumes every packet and enum value it sends exists in the client's protocol, and an unknown one throws while encoding, which disconnects the player. For protocols below 26.40, packets that do not exist in the client's protocol, and sounds, particles, level events and entity events that the client lacks, are now dropped before sending, so the player only misses that one effect. The unsupported values are found once per protocol by trying to encode each of them, so no list has to be kept in step with the protocol library. Entity flags and entity data types are not filtered, as Geyser does not currently use any that 1.21.130 lacks.

**Teleports during 1.21.130 loading screens.** A China Edition 1.21.130 client was seen to ignore the `MovePlayer` packets sent to it while a loading screen is up, after joining and while changing dimension (which is also how Geyser moves a player between servers behind a proxy), and to stand where the last `RespawnPacket` put it when the screen closes; international 1.21.130 shares its protocol and is handled the same way. Upstream stops waiting for the client to confirm a teleport when the server teleports the player again to the same spot, as plugins often do on join, assuming the client is already there. On 1.21.130 the client was not, so Geyser took its stale position as movement, rejected it as moving too quickly, and sent corrections that the client also ignored: after a server switch the player was left at the placeholder position high above the world, and fell. For protocols below 26.40, a teleport to the same spot as one still unconfirmed now keeps waiting, the player's movement is ignored until the client arrives, and the teleport is sent again as soon as the client reports that it finished loading or changing dimension. 26.40 and newer behave as upstream.

**Artifact file names.** Upstream names its jars without a version, such as `Geyser-Velocity.jar`. They now carry this fork's version, such as `Geyser-Velocity-2.12.1-b1280.14.jar`, so builds can be told apart at a glance.

**Version string.** `/geyser version` and the plugin descriptors report this fork's version followed by the commit, such as `2.12.1-b1280.14 (git-master-abc1234)`.

**Startup notice and support links.** Upstream's startup banner asks for bugs on its Discord. It now says this is an unofficial MINEZ fork and points to [this repository's issue tracker](https://github.com/MINEZ/Geyser/issues), and so do the header of newly written config files and `/geyser connectiontest`. The header of an existing config file is only rewritten when its config version changes, so edit it by hand if you want it updated.

## Building

```bash
./gradlew build
```

On Windows, run `gradlew.bat build` instead. The build needs JDK 21 or newer; the Fabric and NeoForge modules compile against JDK 25, which Gradle downloads on its own if it is not installed. The build also runs the game tests; add `-x :gametest:runGametest` to skip them.

Take the jar for your platform:

| Platform | File |
| --- | --- |
| Velocity | `bootstrap/velocity/build/libs/Geyser-Velocity-2.12.1-b1280.14.jar` |
| Spigot / Paper | `bootstrap/spigot/build/libs/Geyser-Spigot-2.12.1-b1280.14.jar` |
| BungeeCord | `bootstrap/bungeecord/build/libs/Geyser-BungeeCord-2.12.1-b1280.14.jar` |
| Fabric | `bootstrap/mod/fabric/build/libs/Geyser-Fabric-2.12.1-b1280.14.jar` |
| NeoForge | `bootstrap/mod/neoforge/build/libs/Geyser-NeoForge-2.12.1-b1280.14.jar` |
| Standalone | `bootstrap/standalone/build/libs/Geyser-Standalone-2.12.1-b1280.14.jar` |
| ViaProxy | `bootstrap/viaproxy/build/libs/Geyser-ViaProxy-2.12.1-b1280.14.jar` |

The same folders also hold intermediate jars, such as `velocity-2.12.1-SNAPSHOT.jar`, which are not meant to be installed.

## Versioning

The version follows upstream's own release numbering, with this fork's revision added at the end. In `2.12.1-b1280.14`:

- `2.12.1` is the upstream version, from the `version` line in `gradle.properties` with its `-SNAPSHOT` suffix dropped. That line is left exactly as upstream has it, so it only conflicts when upstream changes it.
- `b1280` is the upstream build this fork is based on, from `upstreamBuild` in the same file — the same number upstream's official build of that commit carries, as in its own `2.12.1-b1280`.
- The trailing `.14` is this fork's revision, from `minezRevision` in the same file.

Upstream numbers a build for every commit pushed to `master`. After merging upstream, look up the build of the upstream commit you merged in the `changes` of `https://download.geysermc.org/v2/projects/geyser/versions/<version>/builds` (for example `https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds`), then:

- set `upstreamBuild` to that build, take upstream's `version` line if it changed, and reset `minezRevision` to `1`;
- for any other release of this fork, increment `minezRevision`, for example from `2.12.1-b1280.14` to `2.12.1-b1280.15`.

Update the version badges at the top of both READMEs, and the file names under [Building](#building), along with it.

The download links on the upstream project point at the official builds and do **not** include these changes.

## Upstream

Geyser is developed by [GeyserMC](https://geysermc.org/). For setting up and configuring Geyser itself, the [official documentation](https://geysermc.org/wiki/geyser/) and the [Discord](https://discord.gg/geysermc) remain the right places to go — for Geyser as upstream ships it, not for anything in this fork.

The changes here are built for one server's needs and are maintained separately. Please keep discussion of them out of upstream's channels.

## License

Geyser is open source under the MIT License, Copyright (c) 2019-2026 GeyserMC. Modifications in this fork are provided under the same terms. See [LICENSE](LICENSE) for the full text.

The China Edition serializers under `core/src/main/java/org/geysermc/geyser/network/bedrock/netease/serializer/` are adapted from the corresponding serializers of [CloudburstMC Protocol](https://github.com/CloudburstMC/Protocol), Copyright 2020 NukkitX, licensed under the Apache License 2.0. Each of those files says so at the top, and the full license text is at [core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt](core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt), which every built jar also carries.
