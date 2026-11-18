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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakPing;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerOfflineHandler;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：网易中转发来的 9 字节服务器列表查询。样本是 2026-11-19 在正式服（7.129.69.35:28765）抓到的包。
 */
class NeteaseShortPingHandlerTest {
    /** 中转 7.136.137.169:37028 发来的查询：01 加 8 字节时间。 */
    private static final String NETEASE_PING = "01411bd3eeef5b7cc1";
    private static final InetSocketAddress RELAY = new InetSocketAddress("7.136.137.169", 37028);
    private static final InetSocketAddress SERVER = new InetSocketAddress("7.129.69.35", 28765);

    @Test
    void turnsTheShortPingIntoARakPing() {
        EmbeddedChannel channel = new EmbeddedChannel(new NeteaseShortPingHandler(List.of("7.136.137.0/24")));
        channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(NETEASE_PING)), SERVER, RELAY));
        RakPing ping = assertInstanceOf(RakPing.class, channel.readInbound());
        assertEquals(0x411bd3eeef5b7cc1L, ping.getPingTime());
        assertEquals(RELAY, ping.getSender());
        assertEquals(SERVER, ping.getRecipient());
        channel.finishAndReleaseAll();
    }

    @Test
    void leavesOtherPacketsToRakNet() {
        EmbeddedChannel channel = new EmbeddedChannel(new NeteaseShortPingHandler(List.of("7.136.137.0/24")));
        // 不在名单中的地址发来的同样的包：回复会被用来放大流量，交给 RakNet 丢弃
        DatagramPacket stranger = new DatagramPacket(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(NETEASE_PING)),
            SERVER, new InetSocketAddress("203.0.113.7", 37028));
        channel.writeInbound(stranger);
        assertSame(stranger, channel.readInbound());
        // 标准的 33 字节 ping 照常交给 RakNet
        DatagramPacket standard = new DatagramPacket(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(
            "01" + "0000000000000001" + "00ffff00fefefefefdfdfdfd12345678" + "0000000000000002")), SERVER, RELAY);
        channel.writeInbound(standard);
        assertSame(standard, channel.readInbound());
        // 网易的地址包（0x7f）不受影响
        DatagramPacket address = new DatagramPacket(Unpooled.wrappedBuffer(new byte[] {0x7f, 0, 0, 0x38}), SERVER, RELAY);
        channel.writeInbound(address);
        assertSame(address, channel.readInbound());
        stranger.release();
        standard.release();
        address.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void recognisesOnlyNineByteUnconnectedPings() {
        assertTrue(NeteaseShortPingHandler.isShortPing(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(NETEASE_PING))));
        assertEquals(false, NeteaseShortPingHandler.isShortPing(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(NETEASE_PING + "00"))));
        assertEquals(false, NeteaseShortPingHandler.isShortPing(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump("02411bd3eeef5b7cc1"))));
    }

    /** 在真实的 RakNet 服务器管线中，网易的简短查询得到标准的 unconnected pong。 */
    @Test
    void realServerAnswersTheShortPing() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(2);
        try {
            Channel server = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .group(group, group)
                .option(RakChannelOption.RAK_HANDLE_PING, true)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                    }
                })
                .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
            server.pipeline().addAfter(RakServerOfflineHandler.NAME, "test-ping", new SimpleChannelInboundHandler<RakPing>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, RakPing ping) {
                    ctx.writeAndFlush(ping.reply(42, Unpooled.copiedBuffer("MCPE;MINEZ;0;0;3;100", StandardCharsets.UTF_8)));
                }
            });
            server.pipeline().addFirst(NeteaseShortPingHandler.NAME, new NeteaseShortPingHandler(List.of("127.0.0.0/8")));
            InetSocketAddress address = (InetSocketAddress) server.localAddress();

            try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
                socket.setSoTimeout(5000);
                byte[] ping = ByteBufUtil.decodeHexDump(NETEASE_PING);
                socket.send(new java.net.DatagramPacket(ping, ping.length, address));
                byte[] buffer = new byte[1500];
                java.net.DatagramPacket answer = new java.net.DatagramPacket(buffer, buffer.length);
                socket.receive(answer);
                ByteBuf pong = Unpooled.wrappedBuffer(buffer, 0, answer.getLength());
                // unconnected pong：1c、原样的时间、服务器 GUID、16 字节 magic、长度与内容
                assertEquals(0x1c, pong.readUnsignedByte());
                assertEquals(0x411bd3eeef5b7cc1L, pong.readLong());
                pong.skipBytes(8 + 16);
                int length = pong.readUnsignedShort();
                assertEquals("MCPE;MINEZ;0;0;3;100", pong.readCharSequence(length, StandardCharsets.UTF_8).toString());
            }
            server.close().sync();
        } finally {
            group.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).sync();
        }
    }
}
