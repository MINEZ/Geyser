/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.network.TransportType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.network.java.DirectConnections;
import org.geysermc.geyser.network.java.GeyserInjector;
import org.geysermc.geyser.network.java.IoHandlerWrapper;
import org.geysermc.geyser.network.java.LocalServerChannelWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

public class GeyserVelocityInjector extends GeyserInjector {
    private final ProxyServer proxy;
    /**
     * MINEZ：Velocity 的 {@code MinecraftConnection#getSessionId()}（Velocity 4.3.0 起）。编译时用的 Velocity 还没有这个方法，
     * 所以以反射取得；为 null 时表示还没有查找过。
     */
    private volatile @Nullable Method sessionIdGetter;
    private volatile boolean warnedNoSessionId;

    public GeyserVelocityInjector(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void initializeLocalChannel0(GeyserBootstrap bootstrap) throws Exception {
        try {
            Class.forName("io.netty.channel.MultiThreadIoEventLoopGroup");
        } catch (ClassNotFoundException e) {
            bootstrap.getGeyserLogger().error("use-direct-connection disabled as Velocity is not up-to-date.");
            return;
        }

        Field cm = proxy.getClass().getDeclaredField("cm");
        cm.setAccessible(true);
        Object connectionManager = cm.get(proxy);
        Class<?> connectionManagerClass = connectionManager.getClass();

        Supplier<ChannelInitializer<Channel>> serverChannelInitializerHolder = (Supplier<ChannelInitializer<Channel>>) connectionManagerClass
                .getMethod("getServerChannelInitializer")
                .invoke(connectionManager);
        ChannelInitializer<Channel> channelInitializer = serverChannelInitializerHolder.get();

        // Is set on Velocity's end for listening to Java connections - required on ours or else the initial world load process won't finish sometimes
        Field serverWriteMarkField = connectionManagerClass.getDeclaredField("SERVER_WRITE_MARK");
        serverWriteMarkField.setAccessible(true);
        WriteBufferWaterMark serverWriteMark = (WriteBufferWaterMark) serverWriteMarkField.get(null);

        Field workerGroupField = connectionManagerClass.getDeclaredField("workerGroup");
        workerGroupField.setAccessible(true);
        IoEventLoopGroup workerGroup = (IoEventLoopGroup) workerGroupField.get(connectionManager);

        var factory = LocalIoHandler.newFactory();
        var nativeFactory = getNativeHandlerFactory();
        var wrapperFactory = new IoHandlerFactory() {
            @Override
            public IoHandler newHandler(ThreadAwareExecutor ioExecutor) {
                return new IoHandlerWrapper(factory.newHandler(ioExecutor), nativeFactory.newHandler(ioExecutor));
            }
        };

        EventLoopGroup wrapperGroup = new MultiThreadIoEventLoopGroup(factory) {
            @Override
            protected ThreadFactory newDefaultThreadFactory() {
                return new DefaultThreadFactory("Geyser Backend Worker Group", Thread.MAX_PRIORITY);
            }

            @Override
            protected IoEventLoop newChild(Executor executor, IoHandlerFactory ioHandlerFactory, Object... args) {
                return new SingleThreadIoEventLoop(workerGroup, executor, wrapperFactory);
            }
        };

        // This method is what initializes the connection in Java Edition, after Netty is all set.
        Method initChannel = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
        initChannel.setAccessible(true);

        ChannelFuture channelFuture = (new ServerBootstrap()
            .channel(LocalServerChannelWrapper.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(@NonNull Channel ch) throws Exception {
                    initChannel.invoke(channelInitializer, ch);
                    // MINEZ：登记 Velocity 为这个进程内直连分配的会话 ID，插件可由此认出来自 Geyser 的连接。
                    registerProxySession(bootstrap, ch);

                    if (bootstrap.config().advanced().java().disableCompression() && GeyserVelocityCompressionDisabler.ENABLED) {
                        ch.pipeline().addAfter("minecraft-encoder", "geyser-compression-disabler",
                            new GeyserVelocityCompressionDisabler());
                    }
                }
            })
            .group(new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory()), wrapperGroup) // Cannot be DefaultEventLoopGroup
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, serverWriteMark) // Required or else rare network freezes can occur
            .localAddress(LocalAddress.ANY))
            .bind()
            .syncUninterruptibly();

        this.localChannel = channelFuture;
        this.serverSocketAddress = channelFuture.channel().localAddress();
    }

    /**
     * MINEZ：取得 Velocity 刚为这个通道建立的 {@code MinecraftConnection} 的会话 ID，登记到 {@link DirectConnections}。
     * 这个通道是 Geyser 自己的 {@code LocalServerChannelWrapper} 接受的，只能来自同一个 JVM。
     */
    private void registerProxySession(GeyserBootstrap bootstrap, Channel channel) {
        Object connection = channel.pipeline().get(Connections.HANDLER);
        if (connection == null) {
            return;
        }
        try {
            Method getter = sessionIdGetter;
            if (getter == null || getter.getDeclaringClass() != connection.getClass()) {
                getter = connection.getClass().getMethod("getSessionId");
                sessionIdGetter = getter;
            }
            if (getter.invoke(connection) instanceof UUID sessionId) {
                if (!DirectConnections.register(channel, sessionId)) {
                    bootstrap.getGeyserLogger().debug("A direct connection without a Geyser session got the Velocity session ID " + sessionId);
                }
            }
        } catch (NoSuchMethodException e) {
            if (!warnedNoSessionId) {
                warnedNoSessionId = true;
                bootstrap.getGeyserLogger().warning("This Velocity does not give connections a session ID (added in Velocity 4.3.0), "
                    + "so plugins cannot look up Geyser players by it (connectionByProxySessionId)");
            }
        } catch (ReflectiveOperationException e) {
            bootstrap.getGeyserLogger().error("Could not read the Velocity session ID of a direct connection", e);
        }
    }

    private static IoHandlerFactory getNativeHandlerFactory() {
        return switch (TransportType.bestType()) {
            case NIO -> NioIoHandler.newFactory();
            case EPOLL -> EpollIoHandler.newFactory();
            case KQUEUE -> KQueueIoHandler.newFactory();
            case IO_URING -> IoUringIoHandler.newFactory();
        };
    }
}
