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

package org.geysermc.geyser.api.event.bedrock;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.connection.ConnectionEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MINEZ：网易中国版客户端经 {@code PyRpc} 通知服务器，玩家在网易商城（客户端自带的网络服商城）中购买成功，或点击了“催促发货”。
 * <p>
 * 只有中国版会话会发出这个事件，国际版客户端没有网易商城。事件由 Geyser 的网络线程同步发出，监听者不能阻塞，耗时的工作应交给自己的线程。
 * <p>
 * 这只是客户端的通知：消息由客户端发出，无法核实，可能重复，也可能被伪造。收到后应当去网易的订单接口查询这名玩家的未发货订单，
 * 由查询结果决定发什么，不能据此直接发货；同一名玩家短时间内的多次通知宜合并为一次查询。
 * 客户端还在登录（包括登录闸门中）时也可能发来，此时会话可能还没有连接 Java 端。
 */
public final class NeteaseShopEvent extends ConnectionEvent {
    private final Kind kind;
    private final String eventName;
    private final @Nullable String namespace;
    private final @Nullable String systemName;
    private final Map<String, Object> data;

    /**
     * @param connection 发来通知的会话。
     * @param kind       事件的种类。
     * @param eventName  消息中的事件名，见 {@link #eventName()}。
     * @param namespace  消息中的命名空间，没有时为 null。
     * @param systemName 消息中的系统名，没有时为 null。
     * @param data       消息中的事件数据，见 {@link #data()}。
     */
    public NeteaseShopEvent(@NonNull GeyserConnection connection, @NonNull Kind kind, @NonNull String eventName,
                            @Nullable String namespace, @Nullable String systemName, @NonNull Map<String, Object> data) {
        super(connection);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.eventName = Objects.requireNonNull(eventName, "eventName");
        this.namespace = namespace;
        this.systemName = systemName;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(data, "data")));
    }

    /**
     * @return 事件的种类。
     */
    public @NonNull Kind kind() {
        return kind;
    }

    /**
     * 客户端消息中的事件名，例如 {@code StoreBuySuccServerEvent}、{@code UrgeShipEvent}，用于日志与排查。
     * 由网易专有的“购买成功”数据包（ID 202）得到的通知为 {@code StoreBuySuccess}。
     *
     * @return 事件名。
     */
    public @NonNull String eventName() {
        return eventName;
    }

    /**
     * 消息是 Mod SDK 的客户端事件（{@code ModEventC2S}）时，其中的命名空间。
     *
     * @return 命名空间，消息中没有时为 null。
     */
    public @Nullable String namespace() {
        return namespace;
    }

    /**
     * 消息是 Mod SDK 的客户端事件（{@code ModEventC2S}）时，其中的系统名。
     *
     * @return 系统名，消息中没有时为 null。
     */
    public @Nullable String systemName() {
        return systemName;
    }

    /**
     * 消息中的事件数据，按网易文档中 Python 与 Java 的类型对应转换：值为 null、{@link Boolean}、{@link Integer}、{@link Long}、
     * {@link java.math.BigInteger}、{@link Double}、{@link String}、{@link java.util.List} 或 {@link Map}，
     * 字符串与二进制串都转为 {@link String}。Geyser 给出的映射连同其中的列表与映射都是只读的。
     * <p>
     * 官方文档写明这两个事件的数据只有玩家的实体 ID（{@code playerId}），本地玩家为 {@code -2}，不能用来识别玩家，
     * 玩家应以 {@link #connection()} 识别。没有数据时为空映射。
     *
     * @return 只读的事件数据。
     */
    public @NonNull Map<String, Object> data() {
        return data;
    }

    /**
     * 网易商城事件的种类。
     */
    public enum Kind {
        /**
         * 玩家购买成功，对应 Mod SDK 的 {@code StoreBuySuccServerEvent}（SpigotMaster 的 {@code PLAYER_BUY_ITEM_SUCCESS}）。
         */
        BUY_SUCCESS,
        /**
         * 玩家点击了商城中的“催促发货”，对应 Mod SDK 的 {@code UrgeShipEvent}（SpigotMaster 的 {@code PLAYER_URGE_SHIP}）。
         */
        URGE_SHIP
    }
}
