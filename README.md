<div align="center">

# MINEZ Geyser

A fork of [Geyser](https://github.com/GeyserMC/Geyser) maintained for the MINEZ server.

<a href="README.md">English</a> ｜
<a href="docs/README_zh-CN.md">简体中文</a>

<br>

<div>
<a href="https://github.com/GeyserMC/Geyser"><img src="https://img.shields.io/badge/upstream-2.12.1--SNAPSHOT-2196f3" alt="upstream"></a>
<a href="#versioning"><img src="https://img.shields.io/badge/version-2.12.1--b1280.3-4caf50" alt="version"></a>
<a href="#supported-versions"><img src="https://img.shields.io/badge/Java-26.3-4caf50" alt="Java"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-9e9e9e" alt="license"></a>
</div>

<div>
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

Its purpose is to let players of NetEase's Minecraft China Edition, whose clients run Bedrock 1.21.130, join the MINEZ Java server alongside Java players. The protocol changes specific to China Edition are not part of this fork yet; for now it adds support for international Bedrock 1.21.130 clients.

### Supported Versions

| Edition | Supported Versions |
| --- | --- |
| Bedrock | 1.21.130, 1.21.131, 1.21.132 (added by this fork), 26.40, 26.41, 26.42, 26.43, 26.44, 26.45, 26.50, 26.51, 26.52, 26.60 |
| Java | 26.3 |

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
| Bedrock 1.21.130 (protocol 898) | Protocol | Added |
| `bedrock.transport: raknet` | Config | Kept |
| Packets the client cannot decode | Behaviour | Changed |
| Artifact file names | Build | Changed |
| Version string | Build | Changed |
| Startup notice and support links | Behaviour | Changed |

**Bedrock 1.21.130.** Upstream dropped every Bedrock version below 26.40, removing the data files those versions need along with the code paths that served them. This fork registers protocol 898 again and brings both back behind a single check for protocols below 26.40, so 26.40 and newer behave exactly as upstream. The block palette, item table, creative inventory and item components come from upstream's history; the item tags come from its last 26.0 data, since an older copy lacks the spear tags. Blocks that gained connection or corner states after 1.21.130 are stripped back to the states the client knows, and 68 sounds whose newer mappings point at names 1.21.130 lacks fall back to their earlier mappings. Recipes are merged into the older combined list format when they are sent.

Content that Java 26.1 to 26.3 added and 1.21.130 lacks is shown through stand-ins, so it never breaks the connection: blocks use upstream's existing fallback tables, new map decorations show as a treasure marker, and the newer explorer maps show as a plain filled map. Some things have no stand-in yet:

- The roughly 140 sound events added since 1.21.130 play no sound.
- The locator bar is turned off and waypoints are not tracked.
- New entity types are sent under their own identifiers, which the client does not know.

**RakNet-only transport.** Upstream removed the `raknet` transport, and now moves a config that names it over to `nethernet`. This fork keeps it, and leaves such a config as it is: China Edition clients only speak RakNet, and the WebRTC library NetherNet loads needs glibc 2.34 and OpenSSL 3, which older systems such as Debian 10 do not have, so every startup there logs an error and NetherNet never runs. With `raknet`, Geyser listens on the UDP `port` alone and never loads that library; signaling and `webrtc-port` are not used, and NetherNet-only clients, such as recent international Bedrock releases, cannot connect. The `nethernet` and `both` transports behave as upstream.

**Packets the client cannot decode.** Upstream assumes every packet and enum value it sends exists in the client's protocol, and an unknown one throws while encoding, which disconnects the player. For protocols below 26.40, packets that do not exist in the client's protocol, and sounds, particles, level events and entity events that the client lacks, are now dropped before sending, so the player only misses that one effect. The unsupported values are found once per protocol by trying to encode each of them, so no list has to be kept in step with the protocol library. Entity flags and entity data types are not filtered, as Geyser does not currently use any that 1.21.130 lacks.

**Artifact file names.** Upstream names its jars without a version, such as `Geyser-Velocity.jar`. They now carry this fork's version, such as `Geyser-Velocity-2.12.1-b1280.3.jar`, so builds can be told apart at a glance.

**Version string.** `/geyser version` and the plugin descriptors report this fork's version followed by the commit, such as `2.12.1-b1280.3 (git-master-abc1234)`.

**Startup notice and support links.** Upstream's startup banner asks for bugs on its Discord. It now says this is an unofficial MINEZ fork and points to [this repository's issue tracker](https://github.com/MINEZ/Geyser/issues), and so do the header of newly written config files and `/geyser connectiontest`. The header of an existing config file is only rewritten when its config version changes, so edit it by hand if you want it updated.

## Building

```bash
./gradlew build
```

On Windows, run `gradlew.bat build` instead. The build needs JDK 21 or newer; the Fabric and NeoForge modules compile against JDK 25, which Gradle downloads on its own if it is not installed. The build also runs the game tests; add `-x :gametest:runGametest` to skip them.

Take the jar for your platform:

| Platform | File |
| --- | --- |
| Velocity | `bootstrap/velocity/build/libs/Geyser-Velocity-2.12.1-b1280.3.jar` |
| Spigot / Paper | `bootstrap/spigot/build/libs/Geyser-Spigot-2.12.1-b1280.3.jar` |
| BungeeCord | `bootstrap/bungeecord/build/libs/Geyser-BungeeCord-2.12.1-b1280.3.jar` |
| Fabric | `bootstrap/mod/fabric/build/libs/Geyser-Fabric-2.12.1-b1280.3.jar` |
| NeoForge | `bootstrap/mod/neoforge/build/libs/Geyser-NeoForge-2.12.1-b1280.3.jar` |
| Standalone | `bootstrap/standalone/build/libs/Geyser-Standalone-2.12.1-b1280.3.jar` |
| ViaProxy | `bootstrap/viaproxy/build/libs/Geyser-ViaProxy-2.12.1-b1280.3.jar` |

The same folders also hold intermediate jars, such as `velocity-2.12.1-SNAPSHOT.jar`, which are not meant to be installed.

## Versioning

The version follows upstream's own release numbering, with this fork's revision added at the end. In `2.12.1-b1280.3`:

- `2.12.1` is the upstream version, from the `version` line in `gradle.properties` with its `-SNAPSHOT` suffix dropped. That line is left exactly as upstream has it, so it only conflicts when upstream changes it.
- `b1280` is the upstream build this fork is based on, from `upstreamBuild` in the same file — the same number upstream's official build of that commit carries, as in its own `2.12.1-b1280`.
- The trailing `.3` is this fork's revision, from `minezRevision` in the same file.

Upstream numbers a build for every commit pushed to `master`. After merging upstream, look up the build of the upstream commit you merged in the `changes` of `https://download.geysermc.org/v2/projects/geyser/versions/<version>/builds` (for example `https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds`), then:

- set `upstreamBuild` to that build, take upstream's `version` line if it changed, and reset `minezRevision` to `1`;
- for any other release of this fork, increment `minezRevision`, for example from `2.12.1-b1280.3` to `2.12.1-b1280.4`.

Update the version badges at the top of both READMEs, and the file names under [Building](#building), along with it.

The download links on the upstream project point at the official builds and do **not** include these changes.

## Upstream

Geyser is developed by [GeyserMC](https://geysermc.org/). For setting up and configuring Geyser itself, the [official documentation](https://geysermc.org/wiki/geyser/) and the [Discord](https://discord.gg/geysermc) remain the right places to go — for Geyser as upstream ships it, not for anything in this fork.

The changes here are built for one server's needs and are maintained separately. Please keep discussion of them out of upstream's channels.

## License

Geyser is open source under the MIT License, Copyright (c) 2019-2026 GeyserMC. Modifications in this fork are provided under the same terms. See [LICENSE](LICENSE) for the full text.
