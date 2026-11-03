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

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * MINEZ：网易中国版客户端的识别与共用常量。
 * <p>
 * 网易客户端在 RakNet 握手时报协议版本 8（国际版为 11），实际却按 11 的方式工作：批内为 VarInt 包头，
 * RequestNetworkSettings 之前不压缩。RakNet 层因此只在握手时接受 8，随后按 11 搭建管线，并在通道上记下原本的版本，
 * 供登录阶段选用网易的编解码器与解压方式。是否真是网易客户端，最终以登录链是否由受信任的网易根公钥签发为准，
 * 见 {@link org.geysermc.geyser.util.NeteaseLoginChain}。
 */
public final class NeteaseClients {
    /**
     * 网易客户端握手时报的 RakNet 协议版本。
     */
    public static final int RAKNET_PROTOCOL_VERSION = 8;

    /**
     * 国际版客户端与网易客户端实际使用的 RakNet 批处理方式所对应的版本。
     */
    public static final int VANILLA_RAKNET_PROTOCOL_VERSION = 11;

    /**
     * 识别出不是网易客户端、且只接受网易客户端时，断开连接所用的提示。
     */
    public static final String NOT_NETEASE_MESSAGE = "客户端版本错误，请使用中国版客户端加入游戏。";

    private static final AttributeKey<Boolean> RAKNET_8 = AttributeKey.valueOf("minez-netease-raknet-8");
    private static final AttributeKey<Boolean> REFUSED = AttributeKey.valueOf("minez-netease-refused");

    private NeteaseClients() {
    }

    /**
     * 记下这条连接握手时报的是 RakNet 8。
     */
    public static void markRaknet8(Channel channel) {
        channel.attr(RAKNET_8).set(Boolean.TRUE);
    }

    /**
     * @return 这条连接握手时是否报的是 RakNet 8，即很可能是网易客户端
     */
    public static boolean isRaknet8(@Nullable Channel channel) {
        return channel != null && Boolean.TRUE.equals(channel.attr(RAKNET_8).get());
    }

    /**
     * 记下这条连接因为不是网易客户端而被拒绝。断开时不强制关闭连接，客户端才收得到提示。
     */
    public static void markRefused(Channel channel) {
        channel.attr(REFUSED).set(Boolean.TRUE);
    }

    /**
     * @return 这条连接是否因为不是网易客户端而被拒绝
     */
    public static boolean isRefused(@Nullable Channel channel) {
        return channel != null && Boolean.TRUE.equals(channel.attr(REFUSED).get());
    }
}
