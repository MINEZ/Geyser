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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.event.bedrock.NeteaseShopEvent;

import java.util.List;
import java.util.Map;

/**
 * MINEZ：解析客户端发来的 {@code PyRpc}（ID 200），从中认出网易商城的“购买成功”与“催促发货”，以及 MINEZ 客户端模组中服务器菜单按钮的事件。
 * <p>
 * 数据包的格式：先是以 VarInt 为长度前缀的字节串，即 MessagePack 编码的消息，其后是 4 个字节的小端序消息 ID。
 * 消息是一个数组 {@code [方法名, 参数, ...]}，整个数组可能再包在 {@code {"value": ...}} 中，参数同样可能这样包一层。
 * 已知的方法有：
 * <ul>
 * <li>{@code ModEventC2S}：Mod SDK 的客户端事件（客户端的 {@code NotifyToServer}），参数为
 * {@code [命名空间, 系统名, 事件名, 事件数据]}；</li>
 * <li>以引擎事件名为方法名的消息，例如 {@code StoreBuySuccServerEvent}。</li>
 * </ul>
 * 事件名为下列之一时认作商城事件，两种消息都认，不限命名空间与系统名：
 * <ul>
 * <li>购买成功：{@code StoreBuySuccServerEvent}（Mod SDK 的服务端引擎事件名），{@code player_buy_item_success}
 * （SpigotMaster、NukkitMaster 的事件枚举值）；</li>
 * <li>催促发货：{@code UrgeShipEvent}（Mod SDK 的服务端引擎事件名），{@code player_urge_ship}（同上）。</li>
 * </ul>
 * 网易的文档只给出了事件名与数据（{@code playerId}），没有写明客户端实际发出的消息，所以名字取宽：认错的代价只是多查一次订单。
 * 依据与不确定之处见 G0012 的说明。
 */
public final class NeteasePyRpc {
    /**
     * 解析的负载（MessagePack 部分）的最大字节数，更大的不解析。
     */
    public static final int MAX_PAYLOAD = 64 * 1024;
    /**
     * Mod SDK 的客户端事件的方法名。
     */
    public static final String MOD_EVENT_C2S = "ModEventC2S";

    /**
     * MINEZ 客户端模组中服务器菜单按钮的事件：命名空间、系统名与事件名，与模组 {@code minezClientScripts/menuClientSystem.py} 一致。
     */
    public static final String MENU_NAMESPACE = "MinezClient";
    public static final String MENU_SYSTEM = "MenuSystem";
    public static final String MENU_EVENT = "OpenServerMenu";

    private static final Map<String, NeteaseShopEvent.Kind> SHOP_EVENTS = Map.of(
        "StoreBuySuccServerEvent", NeteaseShopEvent.Kind.BUY_SUCCESS,
        "player_buy_item_success", NeteaseShopEvent.Kind.BUY_SUCCESS,
        "UrgeShipEvent", NeteaseShopEvent.Kind.URGE_SHIP,
        "player_urge_ship", NeteaseShopEvent.Kind.URGE_SHIP);

    private NeteasePyRpc() {
    }

    /**
     * 一条解出的消息。
     *
     * @param method     方法名。
     * @param namespace  {@code ModEventC2S} 的命名空间，其他方法为 null。
     * @param systemName {@code ModEventC2S} 的系统名，其他方法为 null。
     * @param eventName  {@code ModEventC2S} 的事件名，其他方法为方法名。
     * @param data       事件数据，只读；没有时为空映射。
     * @param msgId      消息 ID，数据包中没有时为 -1。
     */
    public record Message(String method, @Nullable String namespace, @Nullable String systemName, String eventName,
                          Map<String, Object> data, long msgId) {
        /**
         * @return 是商城事件时为它的种类，否则为 null。
         */
        public NeteaseShopEvent.@Nullable Kind shopKind() {
            return SHOP_EVENTS.get(eventName);
        }

        /**
         * @return 是否为 MINEZ 客户端模组中服务器菜单按钮的事件。只认 {@code ModEventC2S}，命名空间、系统名与事件名都须相符。
         */
        public boolean isOpenMenu() {
            return MOD_EVENT_C2S.equals(method) && MENU_NAMESPACE.equals(namespace) && MENU_SYSTEM.equals(systemName)
                && MENU_EVENT.equals(eventName);
        }

        /**
         * @return 用于 debug 日志的摘要，只有方法与事件的名字和数据的键，不含数据的值。
         */
        public String summary() {
            String name = namespace == null ? eventName : namespace + "/" + systemName + "/" + eventName;
            return method.equals(eventName) ? method + " " + data.keySet() : method + " " + name + " " + data.keySet();
        }
    }

    /**
     * 解析 {@code PyRpc} 数据包的原始负载（{@link NeteasePacket#payload()}）。
     *
     * @param packet 数据包的原始负载。
     * @return 解出的消息。
     * @throws NeteaseMsgPack.FormatException 格式不符、超出大小或嵌套等限制。
     */
    public static Message decode(byte[] packet) throws NeteaseMsgPack.FormatException {
        int[] position = {0};
        long length = readVarUnsignedInt(packet, position);
        int start = position[0];
        if (length > MAX_PAYLOAD) {
            throw new NeteaseMsgPack.FormatException("payload of " + length + " bytes is larger than " + MAX_PAYLOAD);
        }
        if (length > packet.length - start) {
            throw new NeteaseMsgPack.FormatException("payload of " + length + " bytes exceeds the packet");
        }
        int end = start + (int) length;
        long msgId;
        int rest = packet.length - end;
        if (rest == 4) {
            msgId = Byte.toUnsignedLong(packet[end]) | Byte.toUnsignedLong(packet[end + 1]) << 8
                | Byte.toUnsignedLong(packet[end + 2]) << 16 | Byte.toUnsignedLong(packet[end + 3]) << 24;
        } else if (rest == 0) {
            msgId = -1;
        } else {
            throw new NeteaseMsgPack.FormatException(rest + " bytes after the payload");
        }
        byte[] payload = new byte[(int) length];
        System.arraycopy(packet, start, payload, 0, payload.length);
        return message(NeteaseMsgPack.read(payload), msgId);
    }

    private static Message message(Object root, long msgId) throws NeteaseMsgPack.FormatException {
        List<?> values = unwrapList(root);
        if (values == null || values.isEmpty() || !(values.get(0) instanceof String method)) {
            throw new NeteaseMsgPack.FormatException("not a [method, arguments] array");
        }
        Object arguments = values.size() > 1 ? values.get(1) : null;
        if (MOD_EVENT_C2S.equals(method)) {
            List<?> args = unwrapList(arguments);
            if (args == null || args.size() < 3 || !(args.get(0) instanceof String namespace)
                || !(args.get(1) instanceof String systemName) || !(args.get(2) instanceof String eventName)) {
                throw new NeteaseMsgPack.FormatException(MOD_EVENT_C2S + " without namespace, system and event names");
            }
            return new Message(method, namespace, systemName, eventName, data(args.size() > 3 ? args.get(3) : null), msgId);
        }
        // 以引擎事件名为方法名的消息：参数为映射时就是数据，为只含一个映射的数组时取这个映射。
        Object data = arguments;
        List<?> args = unwrapList(arguments);
        if (args != null) {
            data = args.isEmpty() ? null : args.size() == 1 && args.get(0) instanceof Map<?, ?> map ? map : args;
        }
        return new Message(method, null, null, method, data(data), msgId);
    }

    /**
     * @return 是数组，或 {@code {"value": 数组}} 时为这个数组，否则为 null。
     */
    private static @Nullable List<?> unwrapList(@Nullable Object value) {
        if (value instanceof Map<?, ?> map && map.get("value") instanceof List<?> list) {
            return list;
        }
        return value instanceof List<?> list ? list : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(@Nullable Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            // NeteaseMsgPack 读出的映射的键都是字符串，且已是只读的。
            return (Map<String, Object>) map;
        }
        return Map.of("value", value);
    }

    private static long readVarUnsignedInt(byte[] data, int[] position) throws NeteaseMsgPack.FormatException {
        long value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (position[0] >= data.length) {
                throw new NeteaseMsgPack.FormatException("truncated length prefix");
            }
            int b = data[position[0]++];
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new NeteaseMsgPack.FormatException("length prefix is longer than 5 bytes");
    }
}
