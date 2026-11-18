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
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeteaseSkinConfirmationTest {
    private static final byte[] IMAGE = new byte[64 * 64 * 4];

    static {
        for (int i = 0; i < IMAGE.length; i++) {
            IMAGE[i] = (byte) i;
        }
    }

    private static PlayerListPacket.Entry entry(UUID uuid, String skinId, boolean persona, byte[] image, String geometry) {
        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(uuid);
        entry.setSkin(SerializedSkin.builder()
            .skinId(skinId)
            .skinData(ImageData.of(64, 64, image))
            .persona(persona)
            .geometryData(geometry)
            .build());
        return entry;
    }

    @Test
    void personaAndNonsyncSkinsAreNotConfirmed() {
        UUID uuid = UUID.randomUUID();
        assertTrue(NeteaseSkinConfirmation.needsConfirmation(entry(uuid, "abc", false, IMAGE, "")));
        assertFalse(NeteaseSkinConfirmation.needsConfirmation(entry(uuid, "abc", true, IMAGE, "")));
        assertFalse(NeteaseSkinConfirmation.needsConfirmation(entry(uuid, "abc.NonsyncCustom", false, IMAGE, "")));
        assertFalse(NeteaseSkinConfirmation.needsConfirmation(entry(uuid, "abc.NonsyncCustomSlim", false, IMAGE, "")));
        assertFalse(NeteaseSkinConfirmation.needsConfirmation(new PlayerListPacket.Entry(uuid)));
    }

    @Test
    void nothingToConfirmGivesNoPacket() {
        assertNull(NeteaseSkinConfirmation.packet(List.of(entry(UUID.randomUUID(), "x", true, IMAGE, ""))));
        assertNull(NeteaseSkinConfirmation.packet(List.of()));
    }

    @Test
    void uidIsStableAndNonNegative() {
        UUID uuid = UUID.fromString("06df98ac-6142-38b7-998a-19c3b02e1312");
        long uid = NeteaseSkinConfirmation.uid(uuid);
        assertTrue(uid >= 0);
        assertEquals(uid, NeteaseSkinConfirmation.uid(uuid));
        assertEquals(Math.abs((long) "06df98ac614238b7998a19c3b02e1312".hashCode()), uid);
    }

    @Test
    void layoutFollowsTheNeteaseDocument() {
        UUID a = new UUID(0x0102030405060708L, 0x1112131415161718L);
        UUID b = UUID.randomUUID();
        NeteasePacket.ConfirmSkin packet = NeteaseSkinConfirmation.packet(List.of(
            entry(a, "a", false, IMAGE, "geoA"),
            entry(UUID.randomUUID(), "p", true, IMAGE, "skipped"),
            entry(b, "b", false, IMAGE, "")));
        assertNotNull(packet);
        assertEquals(NeteasePacket.ConfirmSkin.ID, packet.packetId());

        ByteBuf buf = Unpooled.wrappedBuffer(packet.payload());
        assertEquals(2, VarInts.readUnsignedInt(buf));
        for (UUID expected : List.of(a, b)) {
            assertTrue(buf.readBoolean());
            assertEquals(expected, new UUID(buf.readLongLE(), buf.readLongLE()));
            byte[] image = new byte[VarInts.readUnsignedInt(buf)];
            buf.readBytes(image);
            assertArrayEquals(IMAGE, image);
        }
        assertEquals(Long.toString(NeteaseSkinConfirmation.uid(a)), readString(buf));
        assertEquals(Long.toString(NeteaseSkinConfirmation.uid(b)), readString(buf));
        assertEquals("geoA", readString(buf));
        assertEquals("", readString(buf));
        assertEquals(0, buf.readableBytes());
    }

    private static String readString(ByteBuf buf) {
        byte[] bytes = new byte[VarInts.readUnsignedInt(buf)];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void offUnlessTurnedOnAtStartup() {
        // b1280.56~58 发了确认后，正式服出现“透明人”与看不见的物品，默认不发
        assertFalse(NeteaseSkinConfirmation.ENABLED);
    }
}
