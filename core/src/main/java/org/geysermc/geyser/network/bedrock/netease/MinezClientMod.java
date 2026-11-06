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
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MINEZ：中国版客户端须载入 MINEZ 客户端模组才能进入（{@code bedrock.netease.require-client-mod}）。
 * <p>
 * 模组随 Geyser 下发的资源包（UUID 为 {@link #PACK_UUID}）到达客户端，客户端在界面初始化完成后以 {@code ModEventC2S}
 * （命名空间 {@value NeteasePyRpc#MENU_NAMESPACE}、系统 {@value NeteasePyRpc#MENU_SYSTEM}、事件 {@value NeteasePyRpc#READY_EVENT}，
 * 数据 {@code {"version": "主.次.修订"}}）告知已载入的版本。登录闸门先生成等待世界、等这个事件，版本不低于下发的包时才交给闸门；
 * {@link #READY_TIMEOUT} 内没有收到时以 {@link #MISSING} 断开，版本更低时以 {@link #OUTDATED} 断开。
 * <p>
 * 要求的版本取自本次下发的包，不另外设置：更新模组只需替换 {@code packs} 中的包。下发的包中没有本模组时不检查。
 */
public final class MinezClientMod {
    /**
     * 模组资源包的 UUID，与模组的 {@code pack/manifest.json} 一致，不会改变。
     */
    public static final UUID PACK_UUID = UUID.fromString("a858dec6-447c-46c8-ae1f-dd384ae6dd27");
    /**
     * 生成等待世界之后，等模组告知版本的时限。等待世界是空的，加载很快，留足手机较慢的余地。
     */
    public static final Duration READY_TIMEOUT = Duration.ofSeconds(60);
    /**
     * 没有等到模组时断开的提示。
     */
    public static final String MISSING = "未能载入 MINEZ 客户端模组，请重新进入服务器。";
    /**
     * 模组的版本低于下发的包时断开的提示。
     */
    public static final String OUTDATED = "MINEZ 客户端模组不是最新版，请重新进入服务器以更新。";

    private static final Pattern VERSION = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})");

    private MinezClientMod() {
    }

    /**
     * 模组的版本，主.次.修订三段数字。
     */
    public record Version(int major, int minor, int patch) implements Comparable<Version> {
        /**
         * @return 解出的版本；不是字符串或不是三段数字时为 null。
         */
        public static @Nullable Version parse(@Nullable Object value) {
            if (!(value instanceof String text)) {
                return null;
            }
            Matcher matcher = VERSION.matcher(text);
            if (!matcher.matches()) {
                return null;
            }
            return new Version(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        }

        public static Version of(ResourcePackManifest.Version version) {
            return new Version(version.major(), version.minor(), version.patch());
        }

        @Override
        public int compareTo(Version other) {
            if (major != other.major) {
                return Integer.compare(major, other.major);
            }
            if (minor != other.minor) {
                return Integer.compare(minor, other.minor);
            }
            return Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    /**
     * @param packs 本次会话下发的资源包。
     * @return 其中有本模组时为它的版本，即客户端须载入的最低版本；没有时为 null。
     */
    public static @Nullable Version required(Collection<ResourcePack> packs) {
        for (ResourcePack pack : packs) {
            ResourcePackManifest.Header header = pack.manifest().header();
            if (PACK_UUID.equals(header.uuid())) {
                return Version.of(header.version());
            }
        }
        return null;
    }
}
