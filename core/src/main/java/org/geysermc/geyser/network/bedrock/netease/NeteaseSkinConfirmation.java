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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

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
    /**
     * 是否发送皮肤确认，默认不发，启动参数 {@code -DGeyser.NeteaseConfirmSkin=true} 时才发。b1280.56~58 一直发，正式服的玩家随即反映
     * 看到“透明人”（看不见的玩家与 NPC）、部分手持物品与盔甲看不见，而自己与别人的皮肤仍看不到；本机没有网易客户端可以核实，
     * 先停发，回到 b1280.55 的样子（看到默认皮肤），留着开关供测试服试验。
     * <p>
     * 原因（2026-11-19，对照布吉岛开源的网易版 Geyser，mc-dreamland/Geyser 的 netease/1.21.130 分支）：网易客户端在同一个玩家列表条目
     * 没有先移除、又被加入并确认一次时，会把这个实体隐藏起来。Geyser 常对同一名玩家再发加入（皮肤异步取到后、列表中的名字改变后），
     * 此前每次都跟一个确认，第二次起这名玩家就成了“透明人”。现在由 {@link org.geysermc.geyser.session.GeyserSession} 记下已加入并确认、
     * 尚未移除的条目，再次加入之前先补发一个移除。布吉岛只对协议 860 这样做，本服的网易客户端为 898，同样出现了透明人。
     * 包的格式与布吉岛的相同（皮肤图为长度加字节，不带宽高；网易文档中带宽高的 writeImage 一段是旧格式）。
     */
    public static final boolean ENABLED = Boolean.getBoolean("Geyser.NeteaseConfirmSkin");

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
        return packet(entries, uuid -> null);
    }

    /**
     * @param entries 刚发出的玩家列表中的各项
     * @param uids    条目的网易账号编号（字符串），没有时（Java 版玩家、NPC 等）返回 null，改用 UUID 的散列（见 {@link #uid}）
     * @return 确认其中要确认的各项的数据包；没有要确认的时为 null
     */
    public static NeteasePacket.@Nullable ConfirmSkin packet(List<PlayerListPacket.Entry> entries,
                                                             Function<UUID, @Nullable String> uids) {
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
        packet.payload(payload(confirmed, uids));
        return packet;
    }

    static byte[] payload(List<PlayerListPacket.Entry> entries) {
        return payload(entries, uuid -> null);
    }

    /**
     * 发出玩家列表之前调用：移除时把条目从 {@code confirmed} 中去掉；加入时，要确认的条目中已加入并确认、尚未移除的，从
     * {@code confirmed} 中去掉并返回移除它们的玩家列表，须在加入之前发出，否则网易客户端会把这些实体隐藏。
     *
     * @param list      将要发出的玩家列表
     * @param confirmed 已加入并确认、尚未移除的条目
     * @return 要先发出的移除，没有时为 null
     */
    public static @Nullable PlayerListPacket removalBeforeAdding(PlayerListPacket list, Set<UUID> confirmed) {
        if (list.getAction() == PlayerListPacket.Action.REMOVE) {
            for (PlayerListPacket.Entry entry : list.getEntries()) {
                confirmed.remove(entry.getUuid());
            }
            return null;
        }
        PlayerListPacket remove = null;
        for (PlayerListPacket.Entry entry : list.getEntries()) {
            if (needsConfirmation(entry) && confirmed.remove(entry.getUuid())) {
                if (remove == null) {
                    remove = new PlayerListPacket();
                    remove.setAction(PlayerListPacket.Action.REMOVE);
                }
                remove.getEntries().add(new PlayerListPacket.Entry(entry.getUuid()));
            }
        }
        return remove;
    }

    /**
     * 发出加入与确认之后调用：记下其中确认了的条目。
     */
    public static void recordConfirmed(PlayerListPacket list, Set<UUID> confirmed) {
        for (PlayerListPacket.Entry entry : list.getEntries()) {
            if (needsConfirmation(entry)) {
                confirmed.add(entry.getUuid());
            }
        }
    }

    static byte[] payload(List<PlayerListPacket.Entry> entries, Function<UUID, @Nullable String> uids) {
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
                String uid = uids.apply(entry.getUuid());
                writeString(buf, uid != null && !uid.isEmpty() ? uid : Long.toString(uid(entry.getUuid())));
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
     * 没有网易账号编号的条目的 uid：网易文档要求“通过 uuid 获取的 uid”，即网易账号的编号。本服玩家列表中的玩家多数没有网易编号可给
     * （Java 版玩家、NPC），与布吉岛相同，由 UUID 去掉连字符后的散列值取绝对值得到；同一个 UUID 总是同一个值。网易玩家用自己的编号。
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
