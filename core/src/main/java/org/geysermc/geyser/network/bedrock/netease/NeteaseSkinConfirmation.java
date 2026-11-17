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
import io.netty.buffer.Unpooled;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MINEZ：网易的皮肤确认（{@link NeteasePacket.ConfirmSkin}，228）。
 * <p>
 * 网易客户端收到玩家列表中某名玩家的皮肤后，要等服务器再发一个皮肤确认，才显示这个皮肤，否则一直显示默认皮肤。网易的
 * 《皮肤反作弊方案》中，服务器先向网易查询皮肤是否合法，合法的才确认；Persona 皮肤与皮肤 ID 以 {@code .NonsyncCustom}、
 * {@code .NonsyncCustomSlim} 结尾的不用确认。查询的接口只认网易商城的皮肤（以皮肤图的 MD5 判断），本服的玩家没有经过
 * 这一步的需要：基岩版玩家的皮肤由登录链证明来自网易客户端，Java 版玩家的皮肤来自网易启动器。本服对玩家列表中有皮肤图的
 * 每一项都直接确认，与布吉岛（mc-dreamland/Geyser）的做法相同。
 * <p>
 * 格式（网易文档，2025-04-14 版）：条目数（VarInt）；每个条目依次为是否合法（1 字节）、UUID（两个小端序 long）、
 * 皮肤图（VarInt 长度 + 字节）；然后每个条目一个 uid（字符串，VarInt 长度 + UTF-8）；最后每个条目一个几何数据（字符串）。
 */
public final class NeteaseSkinConfirmation {
    private static final String NONSYNC_CUSTOM = ".NonsyncCustom";
    private static final String NONSYNC_CUSTOM_SLIM = ".NonsyncCustomSlim";

    private NeteaseSkinConfirmation() {
    }

    /**
     * @return 玩家列表的这一项是否要确认：有皮肤图、不是 Persona 皮肤、皮肤 ID 不以 {@code .NonsyncCustom(Slim)} 结尾。
     */
    public static boolean needsConfirmation(PlayerListPacket.Entry entry) {
        SerializedSkin skin = entry.getSkin();
        if (entry.getUuid() == null || skin == null || skin.isPersona()) {
            return false;
        }
        String skinId = skin.getSkinId();
        if (skinId != null && (skinId.endsWith(NONSYNC_CUSTOM) || skinId.endsWith(NONSYNC_CUSTOM_SLIM))) {
            return false;
        }
        return skin.getSkinData() != null && skin.getSkinData().getImage() != null && skin.getSkinData().getImage().length > 0;
    }

    /**
     * @param entries 刚发出的玩家列表中的各项
     * @return 确认其中要确认的各项（见 {@link #needsConfirmation}）的数据包；没有要确认的时为 null
     */
    public static NeteasePacket.@Nullable ConfirmSkin packet(List<PlayerListPacket.Entry> entries) {
        List<PlayerListPacket.Entry> confirmed = new ArrayList<>();
        for (PlayerListPacket.Entry entry : entries) {
            if (needsConfirmation(entry)) {
                confirmed.add(entry);
            }
        }
        if (confirmed.isEmpty()) {
            return null;
        }
        NeteasePacket.ConfirmSkin packet = new NeteasePacket.ConfirmSkin();
        packet.payload(payload(confirmed));
        return packet;
    }

    static byte[] payload(List<PlayerListPacket.Entry> entries) {
        ByteBuf buf = Unpooled.buffer();
        try {
            VarInts.writeUnsignedInt(buf, entries.size());
            for (PlayerListPacket.Entry entry : entries) {
                buf.writeBoolean(true);
                UUID uuid = entry.getUuid();
                buf.writeLongLE(uuid.getMostSignificantBits());
                buf.writeLongLE(uuid.getLeastSignificantBits());
                byte[] image = entry.getSkin().getSkinData().getImage();
                VarInts.writeUnsignedInt(buf, image.length);
                buf.writeBytes(image);
            }
            for (PlayerListPacket.Entry entry : entries) {
                writeString(buf, Long.toString(uid(entry.getUuid())));
            }
            for (PlayerListPacket.Entry entry : entries) {
                String geometry = entry.getSkin().getGeometryData();
                writeString(buf, geometry == null ? "" : geometry);
            }
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    /**
     * 条目的 uid：网易文档要求“通过 uuid 获取的 uid”，即网易账号的编号。本服玩家列表中的玩家多数没有网易编号可给（Java 版
     * 玩家、NPC），与布吉岛相同，由 UUID 去掉连字符后的散列值取绝对值得到；同一个 UUID 总是同一个值。
     */
    static long uid(UUID uuid) {
        long uid = uuid.toString().replace("-", "").hashCode();
        return uid < 0 ? -uid : uid;
    }

    private static void writeString(ByteBuf buf, String string) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        VarInts.writeUnsignedInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }
}
