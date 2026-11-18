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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * MINEZ：网易中转的地址包。样本是 2026-11-16 在正式服（7.129.69.35:28765）抓到的包。
 */
class NeteaseRelayAddressHandlerTest {
    /** 玩家进入时，中转 7.136.137.174:11361 在 RakNet 连接请求之前发来的包，写明玩家的真实地址 116.7.1.88:2961。 */
    private static final String PLAYER = "7f000038078889ae00000000000000000000000007814523000000000000000000000000"
        + "2c61705d02740701580000000000000000000000000b9102";
    /** 中转每隔约 2 秒发来的检查包：7.136.137.178:58466，“真实地址”为网易的探测机 42.186.88.241。 */
    private static final String HEALTH_CHECK = "7f000038078889b200000000000000000000000007814523000000000000000000000000"
        + "e462705d022aba58f1000000000000000000000000c71d02";
    private static final InetSocketAddress RELAY = new InetSocketAddress("7.136.137.174", 11361);
    private static final InetSocketAddress SERVER = new InetSocketAddress("7.129.69.35", 28765);

    @Test
    void readsThePlayersAddress() {
        assertEquals(new InetSocketAddress("116.7.1.88", 2961), NeteaseRelayAddressHandler.parse(packet(PLAYER), RELAY));
        assertEquals(new InetSocketAddress("42.186.88.241", 50973),
            NeteaseRelayAddressHandler.parse(packet(HEALTH_CHECK), new InetSocketAddress("7.136.137.178", 58466)));
    }

    @Test
    void refusesPacketsNotFromTheRelayInside() {
        // 包中写的中转与发来的地址或端口不同
        assertNull(NeteaseRelayAddressHandler.parse(packet(PLAYER), new InetSocketAddress("7.136.137.175", 11361)));
        assertNull(NeteaseRelayAddressHandler.parse(packet(PLAYER), new InetSocketAddress("7.136.137.174", 11362)));
    }

    @Test
    void refusesMalformedPackets() {
        assertNull(NeteaseRelayAddressHandler.parse(packet(PLAYER.substring(0, PLAYER.length() - 2)), RELAY));
        assertNull(NeteaseRelayAddressHandler.parse(packet(PLAYER + "00"), RELAY));
        // 地址族不是 IPv4
        assertNull(NeteaseRelayAddressHandler.parse(packet(replaceByte(PLAYER, 40, "0a")), RELAY));
        // 长度字段不对
        assertNull(NeteaseRelayAddressHandler.parse(packet(replaceByte(PLAYER, 3, "39")), RELAY));
    }

    @Test
    void remembersAddressesFromListedRelaysOnly() {
        NeteaseRelayAddressHandler handler = new NeteaseRelayAddressHandler(List.of("7.136.137.0/24"));
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(new DatagramPacket(packet(PLAYER), SERVER, RELAY));
        assertNull(channel.readInbound(), "地址包不再往后传");
        assertEquals(new InetSocketAddress("116.7.1.88", 2961), handler.clientAddress(RELAY));

        InetSocketAddress stranger = new InetSocketAddress("203.0.113.7", 11361);
        String forged = PLAYER.replace("078889ae", "cb007107");
        DatagramPacket fromStranger = new DatagramPacket(packet(forged), SERVER, stranger);
        channel.writeInbound(fromStranger);
        assertSame(fromStranger, channel.readInbound(), "不在名单中的地址发来的包原样往后传，交给 RakNet 丢弃");
        assertNull(handler.clientAddress(stranger));
        fromStranger.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void passesRakNetPacketsThrough() {
        NeteaseRelayAddressHandler handler = new NeteaseRelayAddressHandler(List.of("7.136.137.0/24"));
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        DatagramPacket request = new DatagramPacket(packet("0500ffff00fefefefefdfdfdfd1234567808"), SERVER, RELAY);
        channel.writeInbound(request);
        assertSame(request, channel.readInbound());
        assertNull(handler.clientAddress(RELAY), "没有地址包时照旧以中转的地址进入");
        request.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void explainsUnreadablePackets() {
        String[] problem = new String[1];
        assertNull(NeteaseRelayAddressHandler.parse(packet(PLAYER), new InetSocketAddress("7.136.137.174", 11362), problem));
        assertEquals("the relay port in the packet is not the sender's", problem[0]);
        assertNull(NeteaseRelayAddressHandler.parse(packet(PLAYER + "00"), RELAY, problem));
        assertEquals("61 bytes instead of 60", problem[0]);
        assertNull(NeteaseRelayAddressHandler.parse(packet(replaceByte(PLAYER, 40, "0a")), RELAY, problem));
        assertEquals("address family 10", problem[0]);
    }

    @Test
    void summarisesEachMinute() {
        NeteaseRelayAddressHandler handler = new NeteaseRelayAddressHandler(List.of("7.136.137.0/24"));
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        NeteaseRelayAddressHandler.STATS.drainSummary();

        channel.writeInbound(new DatagramPacket(packet(PLAYER), SERVER, RELAY));
        channel.writeInbound(new DatagramPacket(packet(PLAYER + "00"), SERVER, RELAY));
        InetSocketAddress stranger = new InetSocketAddress("203.0.113.7", 11361);
        channel.writeInbound(new DatagramPacket(packet(PLAYER), SERVER, stranger));
        DatagramPacket passed = channel.readInbound();
        passed.release();

        assertEquals(new InetSocketAddress("116.7.1.88", 2961), handler.sessionAddress(RELAY));
        assertNull(handler.sessionAddress(new InetSocketAddress("7.136.137.174", 11362)), "同一中转的另一端口没有地址包");
        assertNull(handler.sessionAddress(new InetSocketAddress("7.136.137.175", 11361)));
        assertNull(handler.sessionAddress(stranger));
        assertEquals("NetEase relay in the last minute: 1 address packets read, 1 unreadable (last: 61 bytes instead of 60), "
            + "1 from unlisted addresses; Bedrock connections: 1 with the player's address, 2 via a relay without it "
            + "(1 of them with an address packet from the same relay on another port), 1 not via a listed relay",
            NeteaseRelayAddressHandler.STATS.drainSummary());
        assertNull(NeteaseRelayAddressHandler.STATS.drainSummary(), "取出后清零，没有新的内容时不写");
        channel.finishAndReleaseAll();
    }

    private static ByteBuf packet(String hex) {
        return Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hex));
    }

    private static String replaceByte(String hex, int index, String value) {
        return hex.substring(0, index * 2) + value + hex.substring(index * 2 + 2);
    }
}
