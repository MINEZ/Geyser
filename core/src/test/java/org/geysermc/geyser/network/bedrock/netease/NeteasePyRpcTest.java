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

import org.geysermc.geyser.api.event.bedrock.NeteaseShopEvent;
import org.geysermc.geyser.network.bedrock.netease.MsgPackWriter.Raw;
import org.geysermc.geyser.network.bedrock.netease.MsgPackWriter.Str;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.geysermc.geyser.network.bedrock.netease.MsgPackWriter.encode;
import static org.geysermc.geyser.network.bedrock.netease.MsgPackWriter.packet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：解析 PyRpc，认出网易商城的“购买成功”与“催促发货”。数据包按 PyRpc 的格式（以 VarInt 为长度前缀的 MessagePack、4 个字节的消息 ID）
 * 构造，事件名与数据（{@code playerId}）取自 Mod SDK 的事件文档。
 */
class NeteasePyRpcTest {
    private static Object[] array(Object... values) {
        return values;
    }

    private static Map<Object, Object> map(Object... entries) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(entries[i], entries[i + 1]);
        }
        return map;
    }

    private static NeteasePyRpc.Message decode(Object message) throws NeteaseMsgPack.FormatException {
        return NeteasePyRpc.decode(packet(message));
    }

    @Test
    void buySuccessAsAnEngineEvent() throws Exception {
        // ["StoreBuySuccServerEvent", [{"playerId": "-2"}]]，字符串为二进制串。
        NeteasePyRpc.Message message = decode(array("StoreBuySuccServerEvent", array(map("playerId", "-2"))));
        assertEquals(NeteaseShopEvent.Kind.BUY_SUCCESS, message.shopKind());
        assertEquals("StoreBuySuccServerEvent", message.eventName());
        assertNull(message.namespace());
        assertNull(message.systemName());
        assertEquals(Map.of("playerId", "-2"), message.data());
        assertEquals(9753608L, message.msgId());

        // 只有方法名、没有参数的。
        NeteasePyRpc.Message bare = decode(array("StoreBuySuccServerEvent"));
        assertEquals(NeteaseShopEvent.Kind.BUY_SUCCESS, bare.shopKind());
        assertEquals(Map.of(), bare.data());
    }

    @Test
    void urgeShipAsAModEvent() throws Exception {
        // {"value": ["ModEventC2S", {"value": [命名空间, 系统名, "UrgeShipEvent", {"playerId": "-2"}]}, nil]}，字符串为字符串类型。
        Object message = map(new Str("value"), array(new Str("ModEventC2S"),
            map(new Str("value"), array(new Str("Minecraft"), new Str("neteaseShop"), new Str("UrgeShipEvent"),
                map(new Str("playerId"), new Str("-2")))), null));
        NeteasePyRpc.Message decoded = decode(message);
        assertEquals(NeteaseShopEvent.Kind.URGE_SHIP, decoded.shopKind());
        assertEquals("UrgeShipEvent", decoded.eventName());
        assertEquals("Minecraft", decoded.namespace());
        assertEquals("neteaseShop", decoded.systemName());
        assertEquals(Map.of("playerId", "-2"), decoded.data());
    }

    @Test
    void theNamesOfTheMasterPluginsAreAccepted() throws Exception {
        assertEquals(NeteaseShopEvent.Kind.BUY_SUCCESS,
            decode(array("ModEventC2S", array("SpigotMaster", "Shop", "player_buy_item_success", map()), null)).shopKind());
        assertEquals(NeteaseShopEvent.Kind.URGE_SHIP, decode(array("player_urge_ship", map("playerId", "-2"))).shopKind());
        // 大小写不同的不认。
        assertNull(decode(array("urgeshipevent")).shopKind());
    }

    @Test
    void otherEventsAreNotShopEvents() throws Exception {
        NeteasePyRpc.Message click = decode(array("ModEventC2S",
            array("MyMod", "MySystemCS", "OnButtonClick", map("buttonId", "secret-value")), null));
        assertNull(click.shopKind());
        assertEquals("OnButtonClick", click.eventName());
        // debug 日志的摘要不含数据的值。
        assertTrue(click.summary().contains("MyMod/MySystemCS/OnButtonClick"), click.summary());
        assertTrue(click.summary().contains("buttonId"), click.summary());
        assertFalse(click.summary().contains("secret-value"), click.summary());

        assertNull(decode(array("ClientLoadAddonsFinishedFromGac", array())).shopKind());
        // 事件数据不是映射时放在 value 中。
        assertEquals(Map.of("value", 3), decode(array("ModEventC2S", array("A", "B", "C", 3), null)).data());
    }

    @Test
    void valuesFollowTheDocumentedTypes() throws Exception {
        Map<String, Object> data = decode(array("UrgeShipEvent", map(
            "nil", null, "bool", true, "small", 5, "negative", -3, "int", Integer.MIN_VALUE, "long", 1L << 40,
            "unsigned", new Raw(new byte[]{(byte) 0xcf, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}),
            "uint32", new Raw(new byte[]{(byte) 0xce, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}),
            "float", 1.5f, "double", 2.25, "text", new Str("中文"), "bytes", "字节串",
            "list", array(1, "a"), "map", map("k", array()), 7, "integer key"))).data();
        assertNull(data.get("nil"));
        assertTrue(data.containsKey("nil"));
        assertEquals(true, data.get("bool"));
        assertEquals(5, data.get("small"));
        assertEquals(-3, data.get("negative"));
        assertEquals(Integer.MIN_VALUE, data.get("int"));
        assertEquals(1L << 40, data.get("long"));
        assertEquals(new BigInteger("18446744073709551615"), data.get("unsigned"));
        assertEquals(4294967295L, data.get("uint32"));
        assertEquals(1.5, data.get("float"));
        assertEquals(2.25, data.get("double"));
        assertEquals("中文", data.get("text"));
        assertEquals("字节串", data.get("bytes"));
        assertEquals(List.of(1, "a"), data.get("list"));
        assertEquals(Map.of("k", List.of()), data.get("map"));
        assertEquals("integer key", data.get("7"));

        // 读出的映射与列表都是只读的。
        assertThrows(UnsupportedOperationException.class, () -> data.put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) data.get("list")).add(2));
        assertThrows(UnsupportedOperationException.class, () -> ((Map<String, Object>) data.get("map")).clear());
    }

    @Test
    void malformedPacketsAreRejected() {
        byte[] urge = encode(array("UrgeShipEvent"));
        List<byte[]> packets = List.of(
            new byte[0],
            // 长度前缀不完整、超过 5 个字节、大于剩余的字节。
            new byte[]{(byte) 0x80},
            new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x01},
            new byte[]{0x10, (byte) 0x91, (byte) 0xa1, 0x41},
            // 负载之后不是 4 个字节的消息 ID。
            Arrays.copyOf(packet(urge, 1), packet(urge, 1).length + 2),
            Arrays.copyOf(packet(urge, 1), packet(urge, 1).length - 1));
        for (byte[] packet : packets) {
            assertThrows(NeteaseMsgPack.FormatException.class, () -> NeteasePyRpc.decode(packet), Arrays.toString(packet));
        }

        List<Object> messages = List.of(
            // 不是 [方法名, 参数] 数组。
            5, array(), array(5, array()), map("method", "UrgeShipEvent"),
            // ModEventC2S 缺少名字或名字不是字符串。
            array("ModEventC2S"), array("ModEventC2S", array("A", "B")), array("ModEventC2S", array("A", 1, "UrgeShipEvent", map()), null),
            // 无效的类型字节、扩展类型。
            array("UrgeShipEvent", new Raw(new byte[]{(byte) 0xc1})),
            array("UrgeShipEvent", new Raw(new byte[]{(byte) 0xd4, 0x01, 0x00})),
            // 字符串被截断、不是 UTF-8。
            array("UrgeShipEvent", new Raw(new byte[]{(byte) 0xa5, 'a'})),
            array(new Raw(new byte[]{(byte) 0xa2, (byte) 0xc3, (byte) 0x28})),
            // 映射的键不是标量。
            array("UrgeShipEvent", new Raw(new byte[]{(byte) 0x81, (byte) 0x90, 0x01})));
        for (Object message : messages) {
            assertThrows(NeteaseMsgPack.FormatException.class, () -> decode(message));
        }

        // 一个值之后还有多余的字节。
        byte[] trailing = Arrays.copyOf(urge, urge.length + 1);
        assertThrows(NeteaseMsgPack.FormatException.class, () -> NeteasePyRpc.decode(packet(trailing, 1)));
    }

    @Test
    void oversizedPayloadsAreNotDecoded() throws Exception {
        String filler = "x".repeat(NeteasePyRpc.MAX_PAYLOAD);
        NeteaseMsgPack.FormatException large = assertThrows(NeteaseMsgPack.FormatException.class,
            () -> decode(array("UrgeShipEvent", map("filler", filler))));
        assertTrue(large.getMessage().contains("larger than"), large.getMessage());

        // 长度前缀声称很大、实际很小的，不按长度分配。
        assertThrows(NeteaseMsgPack.FormatException.class, () -> NeteasePyRpc.decode(new byte[]{(byte) 0xff, (byte) 0xff, 0x03, 0x00}));
        // 数组、映射、字符串的长度字段大于剩余的字节。
        for (byte[] huge : List.of(new byte[]{(byte) 0xdd, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff},
            new byte[]{(byte) 0xdf, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff},
            new byte[]{(byte) 0xdb, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff})) {
            assertThrows(NeteaseMsgPack.FormatException.class, () -> NeteasePyRpc.decode(packet(huge, 1)));
        }
        // 元素太多。
        Object[] many = new Object[NeteaseMsgPack.MAX_ELEMENTS + 1];
        assertThrows(NeteaseMsgPack.FormatException.class, () -> decode(array("UrgeShipEvent", many)));

        // 上限以内的照常解析。
        String fits = "x".repeat(NeteasePyRpc.MAX_PAYLOAD - 100);
        assertEquals(NeteaseShopEvent.Kind.URGE_SHIP, decode(array("UrgeShipEvent", map("filler", fits))).shopKind());
    }

    @Test
    void deepNestingIsRejected() throws Exception {
        // 根为第 1 层：参数中再嵌套到第 MAX_DEPTH 层可以，再深一层不行。
        Object nested = map("deepest", 1);
        for (int depth = 3; depth < NeteaseMsgPack.MAX_DEPTH; depth++) {
            nested = array(nested);
        }
        assertEquals(NeteaseShopEvent.Kind.URGE_SHIP, decode(array("UrgeShipEvent", map("nested", nested))).shopKind());
        Object tooDeep = map("nested", array(nested));
        assertThrows(NeteaseMsgPack.FormatException.class, () -> decode(array("UrgeShipEvent", tooDeep)));

        byte[] bomb = new byte[4000];
        Arrays.fill(bomb, (byte) 0x91);
        assertThrows(NeteaseMsgPack.FormatException.class, () -> NeteasePyRpc.decode(packet(bomb, 1)));
    }
}
