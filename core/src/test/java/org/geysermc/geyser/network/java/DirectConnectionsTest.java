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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.session.GeyserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MINEZ：只有 Geyser 的进程内直连带着 Geyser 会话，可以按代理的会话 ID 查到；其他连接都查不到。
 */
class DirectConnectionsTest {
    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());

    @AfterEach
    void shutdown() {
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }

    /**
     * 与 GeyserVelocityInjector 一样以 LocalServerChannelWrapper 监听，返回服务端一侧收到的通道。
     */
    private Channel connect(LocalAddress address, Channel client) throws Exception {
        CompletableFuture<Channel> accepted = new CompletableFuture<>();
        Channel server = new ServerBootstrap()
            .group(group, group)
            .channel(LocalServerChannelWrapper.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(@NonNull Channel ch) {
                    accepted.complete(ch);
                }
            })
            .localAddress(address)
            .bind().syncUninterruptibly().channel();
        group.next().register(client).syncUninterruptibly();
        client.connect(address).syncUninterruptibly();
        Channel channel = accepted.get(5, TimeUnit.SECONDS);
        channel.closeFuture().addListener(future -> server.close());
        return channel;
    }

    @Test
    void geyserDirectConnectionIsRegistered() throws Exception {
        GeyserSession session = mock(GeyserSession.class);
        LocalChannelWithRemoteAddress client = new LocalChannelWithRemoteAddress();
        client.geyserSession(session);
        Channel server = connect(new LocalAddress("geyser-direct-" + UUID.randomUUID()), client);

        assertSame(session, DirectConnections.sessionOf(server));
        UUID proxySessionId = UUID.randomUUID();
        assertTrue(DirectConnections.register(server, proxySessionId));
        assertSame(session, DirectConnections.get(proxySessionId));

        // 会话关闭后查不到
        when(session.isClosed()).thenReturn(true);
        assertNull(DirectConnections.get(proxySessionId));
        when(session.isClosed()).thenReturn(false);

        // 通道关闭后移除
        server.close().syncUninterruptibly();
        client.close().syncUninterruptibly();
        long deadline = System.currentTimeMillis() + 5000;
        while (DirectConnections.get(proxySessionId) != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNull(DirectConnections.get(proxySessionId));
    }

    @Test
    void otherLocalConnectionsAreNotRegistered() throws Exception {
        // 进程内的其他 LocalChannel（不是 LocalSession 建立的）不带 Geyser 会话
        Channel server = connect(new LocalAddress("geyser-direct-" + UUID.randomUUID()), new LocalChannel());
        assertNull(DirectConnections.sessionOf(server));
        UUID proxySessionId = UUID.randomUUID();
        assertFalse(DirectConnections.register(server, proxySessionId));
        assertNull(DirectConnections.get(proxySessionId));
        server.close().syncUninterruptibly();
    }

    @Test
    void geyserConnectionWithoutASessionIsNotRegistered() throws Exception {
        // 例如 Spigot 注入器测试连接时用的 LocalSession，没有 Geyser 会话
        Channel server = connect(new LocalAddress("geyser-direct-" + UUID.randomUUID()), new LocalChannelWithRemoteAddress());
        assertNull(DirectConnections.sessionOf(server));
        assertFalse(DirectConnections.register(server, UUID.randomUUID()));
        server.close().syncUninterruptibly();
    }

    @Test
    void networkConnectionsAreNotRegistered() {
        // 代表经网络连入的连接：不是 LocalChannel
        EmbeddedChannel network = new EmbeddedChannel();
        assertNull(DirectConnections.sessionOf(network));
        UUID proxySessionId = UUID.randomUUID();
        assertFalse(DirectConnections.register(network, proxySessionId));
        assertNull(DirectConnections.get(proxySessionId));
        assertNull(DirectConnections.get(UUID.randomUUID()));
    }
}
