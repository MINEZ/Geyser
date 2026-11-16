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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.netty.util.nethernet.IpRangeSet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * MINEZ：网易中转的地址包。
 * <p>
 * 中国版客户端经网易的中转连到本服，Geyser 看到的是中转的地址。网易的中转在转发一条连接之前，先从同一个地址与端口另发一个
 * UDP 包，写明玩家的真实地址（不是 HAProxy 的 PROXY protocol，所以 {@code haproxy-protocol-whitelisted-ips} 读不了它）。
 * 2026-11-16 在正式服抓包得出的格式，共 60 字节：
 * <pre>
 * 0      7f 00
 * 2      其后的长度，00 38（56）
 * 4      中转的地址，16 字节（IPv4 写在前 4 字节，其余为 0）
 * 20     本服的地址，16 字节
 * 36     中转的端口、本服的端口，各 2 字节
 * 40     地址族，02 为 IPv4
 * 41     玩家的真实地址，16 字节
 * 57     玩家的真实端口，2 字节
 * 59     02
 * </pre>
 * 玩家进入时，中转先发一个这样的包，紧接着发 RakNet 的连接请求，之后又发两次。中转每隔约 2 秒还会发一个同样格式的包检查本服
 * 是否在线，其中的“真实地址”是网易的探测机，随后发服务器列表的 ping，不会建立连接，记下的地址到时自动作废。
 * <p>
 * 只接受 {@code relay-addresses} 中的地址发来的包（任何人都能伪造一个），按中转的地址与端口记下真实地址，建立会话时取用
 * （{@link #clientAddress}）。收不到这种包的连接（例如网易没有开启的服务器）照旧以中转的地址进入。
 */
@ChannelHandler.Sharable
public final class NeteaseRelayAddressHandler extends ChannelInboundHandlerAdapter {
    public static final String NAME = "minez-netease-relay-address";

    static final int PACKET_ID = 0x7f;
    static final int PACKET_LENGTH = 60;
    private static final int ADDRESS_LENGTH = 16;
    private static final int FAMILY_IPV4 = 2;

    private final IpRangeSet relays;
    /** 中转的地址与端口 → 玩家的真实地址。连接请求紧跟在地址包之后，30 秒足够；中转之后还会再发，届时重新记下。 */
    private final Cache<InetSocketAddress, InetSocketAddress> clients = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .maximumSize(65536)
        .build();

    public NeteaseRelayAddressHandler(Collection<String> relays) {
        this.relays = IpRangeSet.parse(relays);
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) {
        if (msg instanceof DatagramPacket packet && isAddressPacket(packet.content())
                && this.relays.contains(packet.sender().getAddress())) {
            try {
                InetSocketAddress client = parse(packet.content(), packet.sender());
                if (client != null) {
                    this.clients.put(packet.sender(), client);
                }
            } finally {
                packet.release();
            }
            return;
        }
        ctx.fireChannelRead(msg);
    }

    /**
     * @param relay 中转的地址与端口，即 RakNet 连接的对端
     * @return 这条连接的玩家的真实地址，没有收到地址包时为 null
     */
    public @Nullable InetSocketAddress clientAddress(InetSocketAddress relay) {
        return this.clients.getIfPresent(relay);
    }

    static boolean isAddressPacket(ByteBuf buf) {
        return buf.readableBytes() >= 1 && buf.getUnsignedByte(buf.readerIndex()) == PACKET_ID;
    }

    /**
     * @param sender 发来这个包的中转，须与包中写的中转地址相同
     * @return 包中写的玩家地址，格式不对时为 null
     */
    static @Nullable InetSocketAddress parse(ByteBuf buf, InetSocketAddress sender) {
        int start = buf.readerIndex();
        if (buf.readableBytes() != PACKET_LENGTH
                || buf.getUnsignedByte(start) != PACKET_ID || buf.getUnsignedByte(start + 1) != 0
                || buf.getUnsignedShort(start + 2) != PACKET_LENGTH - 4
                || buf.getUnsignedByte(start + 40) != FAMILY_IPV4) {
            return null;
        }
        InetAddress relay = ipv4(buf, start + 4);
        if (relay == null || !relay.equals(sender.getAddress()) || buf.getUnsignedShort(start + 36) != sender.getPort()) {
            return null;
        }
        InetAddress client = ipv4(buf, start + 41);
        int port = buf.getUnsignedShort(start + 57);
        if (client == null || client.isAnyLocalAddress()) {
            return null;
        }
        return new InetSocketAddress(client, port);
    }

    /** 16 字节的地址字段中的 IPv4 地址：前 4 字节为地址，其余须为 0。 */
    private static @Nullable InetAddress ipv4(ByteBuf buf, int index) {
        for (int i = 4; i < ADDRESS_LENGTH; i++) {
            if (buf.getByte(index + i) != 0) {
                return null;
            }
        }
        byte[] bytes = new byte[4];
        buf.getBytes(index, bytes);
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
