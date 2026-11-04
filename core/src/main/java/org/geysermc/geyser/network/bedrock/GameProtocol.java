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

package org.geysermc.geyser.network.bedrock;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168_hotfix4;
import org.cloudburstmc.protocol.bedrock.codec.v2169.Bedrock_v2169;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.geysermc.geyser.api.util.MinecraftVersion;
import org.geysermc.geyser.impl.MinecraftVersionImpl;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains information about the supported protocols in Geyser.
 */
public final class GameProtocol {

    /**
     * All Bedrock protocol codecs that Geyser uses
     */
    static final List<BedrockCodec> SUPPORTED_BEDROCK_CODECS = new ArrayList<>();

    /**
     * MINEZ：网易中国版客户端用的编解码器，以同一协议的国际版编解码器为基础，见 {@link NeteaseCodec}。
     */
    static final List<BedrockCodec> NETEASE_BEDROCK_CODECS = new ArrayList<>();

    /**
     * All bedrock protocol versions that Geyser supports
     */
    public static final IntList SUPPORTED_BEDROCK_PROTOCOLS = new IntArrayList();

    /**
     * All bedrock minecraft versions that Geyser supports.
     * There may be multiple MinecraftVersions with the same protocol version.
     */
    public static final List<MinecraftVersion> SUPPORTED_BEDROCK_VERSIONS = new ArrayList<>();

    /**
     * The latest Bedrock protocol version that Geyser supports.
     */
    public static final int DEFAULT_BEDROCK_PROTOCOL;

    /**
     * The latest Bedrock Minecraft version that Geyser supports.
     */
    public static final String DEFAULT_BEDROCK_VERSION;

    /**
     * Java codec that is supported. We only ever support one version for
     * Java Edition.
     */
    private static final PacketCodec DEFAULT_JAVA_CODEC = MinecraftCodec.CODEC;

    static {
        // Strict ordering
        // MINEZ：1.21.130 与网易中国版同内核，低于 26.40 的分支见 is26_40orHigher 的调用处。
        register(Bedrock_v898.CODEC, "1.21.130", "1.21.131", "1.21.132");
        register(Bedrock_v2168_hotfix4.CODEC, "26.40", "26.41", "26.42", "26.43", "26.44");
        register(Bedrock_v2169.CODEC, "26.45");
        register(Bedrock_v2193.CODEC, "26.50", "26.51", "26.52");
        register(Bedrock_v2225.CODEC, "26.60");

        // MINEZ：网易中国版 3.10（内核 1.21.130）。
        NETEASE_BEDROCK_CODECS.add(NeteaseCodec.build(getBedrockCodec(Bedrock_v898.CODEC.getProtocolVersion())));

        MinecraftVersion latestBedrock = SUPPORTED_BEDROCK_VERSIONS.getLast();
        DEFAULT_BEDROCK_VERSION = latestBedrock.versionString();
        DEFAULT_BEDROCK_PROTOCOL = latestBedrock.protocolVersion();
    }

    /**
     * Registers a bedrock codec, along with its protocol version and minecraft version(s).
     * This method must be called in ascending order in terms of protocol version.
     *
     * @param codec the codec to register
     * @param minecraftVersions all versions the codec supports, in ascending order
     */
    private static void register(BedrockCodec codec, String... minecraftVersions) {
        // modify packet serializers to better fit our use
        codec = CodecProcessor.processCodec(codec);

        SUPPORTED_BEDROCK_CODECS.add(codec);
        SUPPORTED_BEDROCK_PROTOCOLS.add(codec.getProtocolVersion());

        for (String version : minecraftVersions) {
            SUPPORTED_BEDROCK_VERSIONS.add(new MinecraftVersionImpl(version, codec.getProtocolVersion()));
        }
    }

    /**
     * Registers a bedrock codec, its protocol version, and a single minecraft version which is taken from the codec.
     * This method must be called in ascending order in terms of protocol version.
     *
     * @param codec the codec to register
     */
    private static void register(BedrockCodec codec) {
        register(codec, codec.getMinecraftVersion());
    }

    /**
     * Gets the {@link BedrockPacketCodec} of the given protocol version.
     * @param protocolVersion The protocol version to attempt to find
     * @return The packet codec, or null if the client's protocol is unsupported
     */
    public static @Nullable BedrockCodec getBedrockCodec(int protocolVersion) {
        for (BedrockCodec packetCodec : SUPPORTED_BEDROCK_CODECS) {
            if (packetCodec.getProtocolVersion() == protocolVersion) {
                return packetCodec;
            }
        }
        return null;
    }

    /**
     * MINEZ：取网易中国版客户端用的编解码器。
     *
     * @param protocolVersion 客户端报的协议版本。
     * @return 网易的编解码器，不支持这一版本时为 null。
     */
    public static @Nullable BedrockCodec getNeteaseCodec(int protocolVersion) {
        for (BedrockCodec packetCodec : NETEASE_BEDROCK_CODECS) {
            if (packetCodec.getProtocolVersion() == protocolVersion) {
                return packetCodec;
            }
        }
        return null;
    }

    /**
     * MINEZ：是否为网易中国版客户端用的编解码器，即会话是否为网易客户端的会话。
     */
    public static boolean isNeteaseCodec(@Nullable BedrockCodec codec) {
        return codec != null && NETEASE_BEDROCK_CODECS.contains(codec);
    }

    /**
     * MINEZ：客户端的协议是否比支持的网易中国版客户端都新，用于选择断开时的提示。
     */
    public static boolean isNewerThanNeteaseCodecs(int protocolVersion) {
        return NETEASE_BEDROCK_CODECS.stream().allMatch(codec -> protocolVersion > codec.getProtocolVersion());
    }

    /**
     * MINEZ：支持的网易中国版客户端所对应的基岩版版本，例如 1.21.130。
     */
    public static String getNeteaseBedrockVersions() {
        return NETEASE_BEDROCK_CODECS.stream()
            .map(BedrockCodec::getMinecraftVersion)
            .collect(Collectors.joining(", "));
    }

    /* Bedrock convenience methods to gatekeep features and easily remove the check on version removal */

    /**
     * MINEZ：区分 1.21.130（协议 898）与 26.40 及以上。上游在移除 26.40 以下的版本时删掉了这些分支，
     * 凡是用到 898 没有的数据包、字段或方块状态的地方，都以此为条件为 1.21.130 跳过或降级。
     */
    public static boolean is26_40orHigher(int protocolVersion) {
        return protocolVersion >= Bedrock_v2168.CODEC.getProtocolVersion();
    }

    public static boolean is26_50orHigher(int protocolVersion) {
        return protocolVersion >= Bedrock_v2193.CODEC.getProtocolVersion();
    }

    public static boolean is26_60orHigher(int protocolVersion) {
        return protocolVersion >= Bedrock_v2225.CODEC.getProtocolVersion();
    }

    /**
     * Gets the supported Minecraft: Java Edition version names.
     *
     * @return the supported Minecraft: Java Edition version names
     */
    public static List<String> getJavaVersions() {
        return List.of(DEFAULT_JAVA_CODEC.getMinecraftVersion());
    }

    /**
     * Gets the supported Minecraft: Java Edition protocol version.
     *
     * @return the supported Minecraft: Java Edition protocol version
     */
    public static int getJavaProtocolVersion() {
        return DEFAULT_JAVA_CODEC.getProtocolVersion();
    }

    /**
     * Gets the supported Minecraft: Java Edition version.
     *
     * @return the supported Minecraft: Java Edition version
     */
    public static String getJavaMinecraftVersion() {
        return DEFAULT_JAVA_CODEC.getMinecraftVersion();
    }

    /**
     * @return a string showing all supported Bedrock versions for this Geyser instance
     */
    public static String getAllSupportedBedrockVersions() {
        return SUPPORTED_BEDROCK_VERSIONS.stream()
            .map(MinecraftVersion::versionString)
            .collect(Collectors.joining(", "));
    }

    /**
     * @return a string showing all supported Java versions for this Geyser instance
     */
    public static String getAllSupportedJavaVersions() {
        return String.join(", ", getJavaVersions());
    }

    private GameProtocol() {
    }
}
