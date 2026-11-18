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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.netty.channel.raknet.RakPing;
import org.cloudburstmc.netty.util.nethernet.IpRangeSet;

import java.util.Collection;

/**
 * MINEZ：回复网易中转发来的简短服务器列表查询，使网易的服务器列表显示基岩版的在线人数。
 * <p>
 * 网易的探测机经中转每隔约 2 秒查询一次基岩版端口，发来的是 9 字节的未连接 ping：编号 {@code 01} 加 8 字节时间，
 * 没有标准 ping 的 16 字节 magic 与 8 字节客户端 GUID（2026-11-19 在正式服抓包确认，内容固定为 {@code 01 41 1b d3 ee ef 5b 7c c1}）。
 * RakNet 的 RakServerOfflineHandler 先核对 magic，长度不够就当作无效包丢弃，所以本服从未回复过，网易拿不到基岩版的人数，
 * 一直显示为 0；Java 版端口的状态查询照常回复，所以 Java 版的人数正常。
 * <p>
 * 这里在 RakNet 之前认出这种包，换成标准的 {@link RakPing} 往后传，由 {@code RakPingHandler} 照常回复（人数与 MOTD
 * 与普通的服务器列表相同，回复本身是标准的 unconnected pong）。只接受 {@code relay-addresses} 中的地址发来的：
 * 回复比查询大得多，对任意来源都回复的话，可以被伪造来源的查询利用来放大流量。
 */
@ChannelHandler.Sharable
public final class NeteaseShortPingHandler extends ChannelInboundHandlerAdapter {
    public static final String NAME = "minez-netease-short-ping";

    static final int ID_UNCONNECTED_PING = 0x01;
    static final int SHORT_PING_LENGTH = 9;

    private final IpRangeSet relays;

    public NeteaseShortPingHandler(Collection<String> relays) {
        this.relays = IpRangeSet.parse(relays);
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) {
        if (msg instanceof DatagramPacket packet && isShortPing(packet.content())
                && this.relays.contains(packet.sender().getAddress())) {
            long pingTime;
            try {
                ByteBuf buf = packet.content();
                pingTime = buf.getLong(buf.readerIndex() + 1);
            } finally {
                packet.release();
            }
            ctx.fireChannelRead(new RakPing(pingTime, packet.sender(), packet.recipient()));
            return;
        }
        ctx.fireChannelRead(msg);
    }

    /** 9 字节、首字节为 {@code 01} 的包。标准 ping 有 33 字节，不在此列，照常交给 RakNet。 */
    static boolean isShortPing(ByteBuf buf) {
        return buf.readableBytes() == SHORT_PING_LENGTH && buf.getUnsignedByte(buf.readerIndex()) == ID_UNCONNECTED_PING;
    }
}
