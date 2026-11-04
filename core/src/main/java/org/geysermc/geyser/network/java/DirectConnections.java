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

package org.geysermc.geyser.network.java;

import io.netty.channel.Channel;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MINEZ：代理为 Geyser 的进程内直连分配的会话 ID 与 Geyser 会话的对应，供插件认出来自 Geyser 的连接。
 * <p>
 * 只有经 {@link LocalServerChannelWrapper} 接受、对端是 {@link LocalSession} 建立的 {@link LocalChannelWithRemoteAddress} 的通道
 * 才带着 Geyser 会话；这种通道只能在同一个 JVM 中以 {@code LocalAddress} 建立，网络连接不可能是这样的通道，所以登记不能被伪造。
 * 由各平台的注入器（目前只有 Geyser-Velocity）在代理为这个通道调用完初始化器之后登记，通道关闭时移除。
 */
public final class DirectConnections {
    private static final Map<UUID, GeyserSession> SESSIONS = new ConcurrentHashMap<>();

    private DirectConnections() {
    }

    /**
     * @param channel 服务端一侧的通道，即代理的初始化器收到的那个
     * @return 这个直连所属的 Geyser 会话；不是 Geyser 的进程内直连时为 null
     */
    public static @Nullable GeyserSession sessionOf(Channel channel) {
        Channel source = channel instanceof ChannelWrapper wrapper ? wrapper.source : channel;
        return source instanceof LocalChannelWrapper local ? local.geyserSession() : null;
    }

    /**
     * 登记代理为这个直连分配的会话 ID，通道关闭时自动移除。
     *
     * @param channel        服务端一侧的通道
     * @param proxySessionId 代理的会话 ID
     * @return 是否登记了；不是 Geyser 的进程内直连时为 false
     */
    public static boolean register(Channel channel, UUID proxySessionId) {
        GeyserSession session = sessionOf(channel);
        if (session == null) {
            return false;
        }
        if (SESSIONS.putIfAbsent(proxySessionId, session) != null) {
            // 会话 ID 由代理随机生成，不应重复；重复时宁可都查不到
            SESSIONS.remove(proxySessionId);
            session.getGeyser().getLogger().warning("The proxy gave two direct connections the same session ID " + proxySessionId);
            return false;
        }
        channel.closeFuture().addListener(future -> SESSIONS.remove(proxySessionId, session));
        return true;
    }

    /**
     * @param proxySessionId 代理的会话 ID
     * @return 登记了这个会话 ID、且仍未关闭的 Geyser 会话，否则为 null
     */
    public static @Nullable GeyserSession get(UUID proxySessionId) {
        GeyserSession session = SESSIONS.get(proxySessionId);
        return session == null || session.isClosed() ? null : session;
    }
}
