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
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketType;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.Set;

/**
 * MINEZ：网易客户端专有的数据包。
 * <p>
 * 解码时只识别出是哪一种，保留原始负载，都不转发给 Java 端：客户端发来的 PyRpc 与 StoreBuySuccess 中网易商城的通知
 * 由 LoggingPacketHandler 解析后发出 {@link org.geysermc.geyser.api.event.bedrock.NeteaseShopEvent}（PyRpc 的格式见 {@link NeteasePyRpc}），
 * 其余记一条 debug 日志就丢弃。各包的用途与格式见 G0006 的说明，例如 PyRpc 是网易 Mod SDK 的通信包，负载为 MessagePack。
 */
public abstract class NeteasePacket implements BedrockPacket {
    /**
     * 只读出原始负载、照原样写回的序列化器。
     */
    public static final BedrockPacketSerializer<NeteasePacket> RAW_SERIALIZER = new BedrockPacketSerializer<>() {
        @Override
        public void serialize(ByteBuf buffer, BedrockCodecHelper helper, NeteasePacket packet) {
            buffer.writeBytes(packet.payload);
        }

        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, NeteasePacket packet) {
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.readBytes(payload);
            packet.payload = payload;
        }
    };

    /**
     * 网易专有数据包的 ID。
     */
    private static final Set<Integer> IDS = Set.of(PyRpc.ID, StoreBuySuccess.ID, NeteaseJson.ID,
        SetDimensionLocalTime.ID, ConfirmSkin.ID, SyncSkin.ID);

    private byte[] payload = new byte[0];

    /**
     * @return 是否为网易专有数据包的 ID。没有用网易的编解码器时，它们解成 {@code UnknownPacket}。
     */
    public static boolean isNeteaseId(int packetId) {
        return IDS.contains(packetId);
    }

    /**
     * @return 网易为这个包分配的 ID。
     */
    public abstract int packetId();

    /**
     * @return 未解析的负载。
     */
    public byte[] payload() {
        return payload;
    }

    public void payload(byte[] payload) {
        this.payload = payload;
    }

    @Override
    public final PacketSignal handle(BedrockPacketHandler handler) {
        return PacketSignal.UNHANDLED;
    }

    @Override
    public final BedrockPacketType getPacketType() {
        return BedrockPacketType.UNKNOWN;
    }

    @Override
    public NeteasePacket clone() {
        try {
            NeteasePacket clone = (NeteasePacket) super.clone();
            clone.payload = payload.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + packetId() + ", " + payload.length + " bytes)";
    }

    /**
     * 200：网易 Mod SDK（Python 脚本）的客户端与服务端通信，负载为 MessagePack，双向。
     */
    public static final class PyRpc extends NeteasePacket {
        public static final int ID = 200;

        @Override
        public int packetId() {
            return ID;
        }
    }

    /**
     * 202：商城购买成功的通知，空包，双向。客户端发来时发出 {@link org.geysermc.geyser.api.event.bedrock.NeteaseShopEvent}。
     */
    public static final class StoreBuySuccess extends NeteasePacket {
        public static final int ID = 202;

        @Override
        public int packetId() {
            return ID;
        }
    }

    /**
     * 203：一个 JSON 字符串，双向。
     */
    public static final class NeteaseJson extends NeteasePacket {
        public static final int ID = 203;

        @Override
        public int packetId() {
            return ID;
        }
    }

    /**
     * 208：维度的本地时间，服务端发往客户端。
     */
    public static final class SetDimensionLocalTime extends NeteasePacket {
        public static final int ID = 208;

        @Override
        public int packetId() {
            return ID;
        }
    }

    /**
     * 228：确认其他玩家的皮肤，服务端在 PlayerList 之后发往客户端。
     */
    public static final class ConfirmSkin extends NeteasePacket {
        public static final int ID = 228;

        @Override
        public int packetId() {
            return ID;
        }
    }

    /**
     * 236：游戏内换肤，客户端发往服务端。
     */
    public static final class SyncSkin extends NeteasePacket {
        public static final int ID = 236;

        @Override
        public int packetId() {
            return ID;
        }
    }
}
