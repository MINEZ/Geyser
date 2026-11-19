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

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：向 noa 查询时的 struct uoa_param（noa 1.0.1 的 uapi/unoa.h，packed，56 字节）与每分钟的汇总。
 */
class NeteaseRealAddressTest {
    private static final InetSocketAddress RELAY = new InetSocketAddress("7.136.137.174", 11361);
    private static final InetSocketAddress SERVER = new InetSocketAddress("7.129.69.35", 28765);

    @Test
    void writesTheFiveTupleAsNoaExpects() {
        ByteBuffer param = ByteBuffer.allocate(NeteaseRealAddress.PARAM_LENGTH);
        NeteaseRealAddress.writeQuery(param, RELAY, SERVER);

        byte[] bytes = new byte[NeteaseRealAddress.PARAM_LENGTH];
        param.get(0, bytes);
        // saddr（中转）16 字节、daddr（本服）16 字节、sport、dport（网络字节序）、af = AF_INET，其余为 0
        assertEquals("078889ae000000000000000000000000"
            + "07814523000000000000000000000000"
            + "2c61" + "705d" + "02"
            + "00000000000000000000000000000000" + "0000" + "00", HexFormat.of().formatHex(bytes));
        assertEquals(0, param.position());
        assertEquals(NeteaseRealAddress.PARAM_LENGTH, param.remaining());
    }

    @Test
    void readsThePlayersAddress() {
        ByteBuffer param = ByteBuffer.allocate(NeteaseRealAddress.PARAM_LENGTH);
        NeteaseRealAddress.writeQuery(param, RELAY, SERVER);
        // noa 写回：real_saddr 116.7.1.88、real_sport 2961、real_af AF_INET
        param.put(37, new byte[] {116, 7, 1, 88});
        param.putShort(53, (short) 2961);
        param.put(55, (byte) 2);
        assertEquals(new InetSocketAddress("116.7.1.88", 2961), NeteaseRealAddress.readAnswer(param));
    }

    @Test
    void ignoresEmptyOrNonIpv4Answers() {
        ByteBuffer param = ByteBuffer.allocate(NeteaseRealAddress.PARAM_LENGTH);
        NeteaseRealAddress.writeQuery(param, RELAY, SERVER);
        assertNull(NeteaseRealAddress.readAnswer(param), "noa 没有写回时 real_af 为 0");
        param.put(55, (byte) 10);
        assertNull(NeteaseRealAddress.readAnswer(param), "IPv6 不处理");
        param.put(55, (byte) 2);
        assertNull(NeteaseRealAddress.readAnswer(param), "地址为 0.0.0.0");
    }

    @Test
    void looksUpWithTheConnectionsOwnAddressWhenKnown() {
        try (NeteaseRealAddress lookup = NeteaseRealAddress.open(List.of("7.136.137.0/24"))) {
            assertEquals(List.of(SERVER.getAddress()), lookup.candidates(SERVER));
            assertEquals(lookup.candidates(new InetSocketAddress("0.0.0.0", 28765)),
                lookup.candidates(new InetSocketAddress("0.0.0.0", 19132)), "绑定在 0.0.0.0 时用本机的各个地址");
        }
    }

    @Test
    void summarisesWithoutAddresses() {
        NeteaseRelayStats stats = new NeteaseRelayStats();
        assertNull(stats.drainSummary(), "没有连接时不写");
        stats.relayedConnection(true, null);
        stats.rememberedConnection();
        stats.relayedConnection(false, "getsockopt() failed: No such file or directory");
        stats.relayedConnection(false, "getsockopt() failed: Protocol not available");
        stats.directConnection();
        assertEquals("NetEase relay in the last minute: Bedrock connections: 1 with the player's address from noa, "
            + "1 with the address noa gave earlier for the same relay port, 2 via a relay without it (last: getsockopt() failed: Protocol not available), 1 not via a listed relay",
            stats.drainSummary());
        assertNull(stats.drainSummary(), "取出后清零");
    }

    @Test
    void unlistedRelaysAreNotLookedUp() {
        NeteaseRealAddress.STATS.drainSummary();
        try (NeteaseRealAddress lookup = NeteaseRealAddress.open(List.of("7.136.137.0/24"))) {
            assertNull(lookup.lookup(new InetSocketAddress("203.0.113.7", 11361), SERVER));
        }
        assertEquals("NetEase relay in the last minute: Bedrock connections: 0 with the player's address from noa, "
            + "0 with the address noa gave earlier for the same relay port, 0 via a relay without it, 1 not via a listed relay",
            NeteaseRealAddress.STATS.drainSummary());
    }

    @Test
    void placesFieldsAtTheirPackedOffsets() {
        // 与 noa 的 struct uoa_param 对照：saddr 0、daddr 16、sport 32、dport 34、af 36、real_saddr 37、real_sport 53、real_af 55
        ByteBuffer param = ByteBuffer.allocate(NeteaseRealAddress.PARAM_LENGTH);
        NeteaseRealAddress.writeQuery(param, RELAY, SERVER);
        byte[] relay = new byte[4];
        param.get(0, relay);
        assertArrayEquals(RELAY.getAddress().getAddress(), relay);
        assertEquals(RELAY.getPort(), Short.toUnsignedInt(param.getShort(32)));
        assertEquals(SERVER.getPort(), Short.toUnsignedInt(param.getShort(34)));
        assertEquals(2, param.get(36));
    }

    /**
     * noa 查不到时（这里在没有 noa 的环境中，每次都查不到），用同一中转地址:端口 30 分钟内记下的地址，用一次重新计时；
     * 别的中转端口、过期的都不用。2026-11-19 正式服：玩家两三分钟内重连，中转沿用原来的会话、不再发地址包。
     */
    @Test
    void reusesTheAddressNoaGaveForTheSameRelayPort() {
        NeteaseRealAddress.STATS.drainSummary();
        try (NeteaseRealAddress lookup = NeteaseRealAddress.open(List.of("7.136.137.0/24"))) {
            long[] now = {1_000_000L};
            lookup.clock = () -> now[0];
            InetSocketAddress player = new InetSocketAddress("36.98.152.47", 2961);
            assertNull(lookup.lookup(RELAY, SERVER), "还没有记下时照旧以中转的地址进入");

            lookup.remember(RELAY, player);
            now[0] += 25 * 60_000L;
            assertEquals(player, lookup.lookup(RELAY, SERVER), "同一中转端口，30 分钟内");
            now[0] += 25 * 60_000L;
            assertEquals(player, lookup.lookup(RELAY, SERVER), "用一次重新计时");
            assertNull(lookup.lookup(new InetSocketAddress("7.136.137.174", 11362), SERVER), "别的中转端口不用");

            now[0] += 31 * 60_000L;
            assertNull(lookup.lookup(RELAY, SERVER), "超过 30 分钟不再用");
            String summary = NeteaseRealAddress.STATS.drainSummary();
            assertTrue(summary.contains("2 with the address noa gave earlier for the same relay port, 3 via a relay without it"), summary);
        }
    }
}
