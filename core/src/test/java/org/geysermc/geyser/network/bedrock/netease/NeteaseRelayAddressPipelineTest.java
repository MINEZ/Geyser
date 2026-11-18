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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.channel.raknet.config.RakServerCookieMode;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：在真实的 RakNet 服务器管线中（与 RaknetServer 相同，处理器在绑定后加在最前）走一遍网易中转的流程：
 * 中转先从某个端口发地址包，再从同一端口发起 RakNet 连接，建立子通道时应取到玩家的真实地址。
 */
class NeteaseRelayAddressPipelineTest {
    private static final InetSocketAddress PLAYER = new InetSocketAddress("116.7.1.88", 2961);

    @Test
    void sessionGetsThePlayersAddress() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(2);
        try {
            NeteaseRelayAddressHandler handler = new NeteaseRelayAddressHandler(List.of("127.0.0.0/8"));
            CompletableFuture<InetSocketAddress> session = new CompletableFuture<>();
            CompletableFuture<InetSocketAddress> remote = new CompletableFuture<>();
            Channel server = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .group(group, group)
                .option(RakChannelOption.RAK_HANDLE_PING, true)
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, new int[] {NeteaseClients.RAKNET_PROTOCOL_VERSION, 11})
                .option(RakChannelOption.RAK_SERVER_COOKIE_MODE, RakServerCookieMode.ACTIVE)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        InetSocketAddress from = ((RakChildChannel) channel).remoteOrProxyAddress();
                        remote.complete(from);
                        session.complete(handler.sessionAddress(from));
                    }
                })
                .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
            server.pipeline().addFirst(NeteaseRelayAddressHandler.NAME, handler);
            InetSocketAddress serverAddress = (InetSocketAddress) server.localAddress();
            NeteaseRelayAddressHandler.STATS.drainSummary();

            InetSocketAddress relay;
            try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
                relay = (InetSocketAddress) socket.getLocalSocketAddress();
                byte[] packet = addressPacket(relay, serverAddress, PLAYER);
                socket.send(new DatagramPacket(packet, packet.length, serverAddress));
            }
            // 等服务器读到地址包，再发起连接；取不到时在失败信息中写出经过，便于在 Linux 上查明原因
            InetSocketAddress remembered = null;
            for (int i = 0; i < 100 && remembered == null; i++) {
                Thread.sleep(10);
                remembered = handler.clientAddress(relay);
            }
            String beforeConnecting = "relay " + relay + ", remembered before connecting: " + remembered
                + ", summary so far: " + NeteaseRelayAddressHandler.STATS.drainSummary();

            Channel client = new Bootstrap()
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .group(group)
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, 11)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                    }
                })
                .connect(serverAddress, relay).channel();

            InetSocketAddress address = session.get(10, TimeUnit.SECONDS);
            String afterConnecting = "connection from " + remote.get() + " (" + remote.get().getAddress().getClass().getSimpleName()
                + "), summary: " + NeteaseRelayAddressHandler.STATS.drainSummary();
            assertEquals(PLAYER, address, beforeConnecting + "; " + afterConnecting);
            client.close().sync();
            server.close().sync();
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    /** 与正式服抓到的包同样格式的地址包，见 {@link NeteaseRelayAddressHandler}。 */
    static byte[] addressPacket(InetSocketAddress relay, InetSocketAddress server, InetSocketAddress player) {
        byte[] packet = new byte[NeteaseRelayAddressHandler.PACKET_LENGTH];
        packet[0] = (byte) NeteaseRelayAddressHandler.PACKET_ID;
        packet[3] = (byte) (NeteaseRelayAddressHandler.PACKET_LENGTH - 4);
        ipv4(packet, 4, relay.getAddress());
        ipv4(packet, 20, server.getAddress());
        port(packet, 36, relay.getPort());
        port(packet, 38, server.getPort());
        packet[40] = 2;
        ipv4(packet, 41, player.getAddress());
        port(packet, 57, player.getPort());
        packet[59] = 2;
        return packet;
    }

    private static void ipv4(byte[] packet, int index, InetAddress address) {
        System.arraycopy(address.getAddress(), 0, packet, index, 4);
    }

    private static void port(byte[] packet, int index, int port) {
        packet[index] = (byte) (port >> 8);
        packet[index + 1] = (byte) port;
    }
}
