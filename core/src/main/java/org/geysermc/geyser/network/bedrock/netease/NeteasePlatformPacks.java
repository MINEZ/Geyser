/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.network.bedrock.netease;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.geysermc.geyser.session.auth.NeteaseProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 平台预装包的启用信息，不提供可下载的包文件。 */
public record NeteasePlatformPacks(List<Pack> resources, List<Pack> behaviors) {
    public static final NeteasePlatformPacks EMPTY = new NeteasePlatformPacks(List.of(), List.of());

    public NeteasePlatformPacks {
        resources = List.copyOf(resources);
        behaviors = List.copyOf(behaviors);
    }

    public record Pack(UUID uuid, MinezClientMod.Version version) {
        ResourcePackStackPacket.Entry entry() {
            return new ResourcePackStackPacket.Entry(uuid.toString(), version.toString(), "");
        }
    }

    public static boolean appliesTo(@Nullable NeteaseProfile profile) {
        return profile != null && "obt".equals(profile.env()) && !profile.developer();
    }

    public static NeteasePlatformPacks parse(List<String> resourceIds, List<String> behaviorIds) {
        if (resourceIds.size() + behaviorIds.size() > 64) {
            throw new IllegalArgumentException("平台包总数不能超过 64 个。");
        }
        Set<UUID> seen = new HashSet<>();
        return new NeteasePlatformPacks(parseList(resourceIds, seen, "资源包"), parseList(behaviorIds, seen, "行为包"));
    }

    private static List<Pack> parseList(List<String> values, Set<UUID> seen, String kind) {
        List<Pack> packs = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            String location = kind + "列表第 " + (i + 1) + " 项";
            if (value == null || value.length() < 38 || value.charAt(36) != '_') {
                throw new IllegalArgumentException(location + "必须采用 UUID_主.次.修订 格式。");
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(value.substring(0, 36));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(location + "的 UUID 无效。");
            }
            if (!uuid.toString().equalsIgnoreCase(value.substring(0, 36))) {
                throw new IllegalArgumentException(location + "必须使用标准 UUID 格式。");
            }
            MinezClientMod.Version version = MinezClientMod.Version.parse(value.substring(37));
            if (version == null) {
                throw new IllegalArgumentException(location + "的版本号无效。");
            }
            if (!seen.add(uuid)) {
                throw new IllegalArgumentException(location + "与其他平台包的 UUID 重复。");
            }
            packs.add(new Pack(uuid, version));
        }
        return packs;
    }

    public boolean enabled() {
        return !resources.isEmpty() || !behaviors.isEmpty();
    }

    public Set<UUID> ids() {
        Set<UUID> ids = new HashSet<>();
        resources.forEach(pack -> ids.add(pack.uuid()));
        behaviors.forEach(pack -> ids.add(pack.uuid()));
        return Set.copyOf(ids);
    }

    public boolean isPlatformRequest(String id) {
        int separator = id.indexOf('_');
        String uuid = separator < 0 ? id : id.substring(0, separator);
        return ids().stream().anyMatch(value -> value.toString().equalsIgnoreCase(uuid));
    }

    public void applyInfoFlags(ResourcePacksInfoPacket packet) {
        if (!behaviors.isEmpty()) {
            packet.setScriptingEnabled(true);
            packet.setHasAddonPacks(true);
        }
    }

    /** 只匹配指定的正式版账号；空值不启用探测。 */
    public boolean probes(@Nullable NeteaseProfile profile, @Nullable String userId) {
        return !resources.isEmpty() && appliesTo(profile) && userId != null && !userId.isEmpty()
            && userId.equals(profile.uid());
    }

    /**
     * 让测试客户端对资源包 UUID 与版本返回协商结果。大小为 0 表示这里只提供诊断元信息；
     * 下载请求必须在进入任何文件发送逻辑前拒绝。不能把未请求下载解释为脚本已启动。
     */
    public void addResourceProbeEntries(ResourcePacksInfoPacket packet) {
        Set<UUID> resourceIds = new HashSet<>();
        resources.forEach(pack -> resourceIds.add(pack.uuid()));
        packet.getResourcePackInfos().removeIf(entry -> resourceIds.contains(entry.getPackId()));
        for (Pack pack : resources) {
            packet.getResourcePackInfos().add(new ResourcePacksInfoPacket.Entry(pack.uuid(), pack.version().toString(),
                0, "", "", pack.uuid().toString(), false, false, false, ""));
        }
    }

    public void applyStack(ResourcePackStackPacket packet) {
        if (!enabled()) {
            return;
        }
        packet.getResourcePacks().removeIf(entry -> isPlatformRequest(entry.getPackId()));
        packet.getBehaviorPacks().removeIf(entry -> isPlatformRequest(entry.getPackId()));
        packet.getResourcePacks().addAll(0, resources.stream().map(Pack::entry).toList());
        // 原版 898 不写此字段；网易序列化器写入扩展列表，其平台行为包加载效果仍待实测。
        packet.getBehaviorPacks().addAll(0, behaviors.stream().map(Pack::entry).toList());
    }

    public MinezClientMod.@Nullable Version requiredClientMod(MinezClientMod.@Nullable Version fallback) {
        for (Pack pack : resources) {
            if (MinezClientMod.PACK_UUID.equals(pack.uuid())) {
                return pack.version();
            }
        }
        return fallback;
    }
}
