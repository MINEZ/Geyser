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

package org.geysermc.geyser.network.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v766.serializer.PlayerAuthInputSerializer_v766;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryLayout;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabLeft;
import org.cloudburstmc.protocol.bedrock.data.inventory.InventoryTabRight;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：网易 898 编解码器的关键字段。多数用例与同一数据包在国际版 898 下的编码逐字节比较，看多出的字段是否在应在的位置。
 */
class NeteaseCodecTest {
    private static final BedrockCodec NETEASE = GameProtocol.getNeteaseCodec(898);
    private static final BedrockCodec VANILLA = GameProtocol.getBedrockCodec(898);

    private static byte[] encode(BedrockCodec codec, BedrockPacket packet) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        codec.tryEncode(codec.createHelper(), buffer, packet);
        return ByteBufUtil.getBytes(buffer);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BedrockPacket> T decode(BedrockCodec codec, byte[] bytes, Class<T> type) throws Exception {
        return (T) codec.tryDecode(codec.createHelper(), Unpooled.wrappedBuffer(bytes), codec.getPacketDefinition(type).getId());
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuf buffer = Unpooled.buffer();
        for (byte[] part : parts) {
            buffer.writeBytes(part);
        }
        return ByteBufUtil.getBytes(buffer);
    }

    private static byte[] string(String value) {
        ByteBuf buffer = Unpooled.buffer();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        VarInts.writeUnsignedInt(buffer, bytes.length);
        buffer.writeBytes(bytes);
        return ByteBufUtil.getBytes(buffer);
    }

    @Test
    void codecsAreSeparate() {
        assertNotNull(NETEASE);
        assertEquals(898, NETEASE.getProtocolVersion());
        assertEquals(PlayerAuthInputSerializer_v766.class, VANILLA.getPacketDefinition(PlayerAuthInputPacket.class).getSerializer().getClass());
        assertNull(GameProtocol.getNeteaseCodec(GameProtocol.DEFAULT_BEDROCK_PROTOCOL));
    }

    private static PlayerAuthInputPacket input() {
        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.setRotation(Vector3f.from(1, 2, 3));
        packet.setPosition(Vector3f.from(4, 5, 6));
        packet.setMotion(Vector2f.from(7, 8));
        packet.getInputData().addAll(EnumSet.of(PlayerAuthInputData.JUMP_DOWN, PlayerAuthInputData.STOP_FLYING,
            PlayerAuthInputData.RECEIVED_SERVER_DATA, PlayerAuthInputData.START_SPIN_ATTACK));
        packet.setInputMode(InputMode.TOUCH);
        packet.setPlayMode(ClientPlayMode.NORMAL);
        packet.setInputInteractionModel(InputInteractionModel.CLASSIC);
        packet.setInteractRotation(Vector2f.from(9, 10));
        packet.setTick(11);
        packet.setDelta(Vector3f.from(12, 13, 14));
        packet.setAnalogMoveVector(Vector2f.from(15, 16));
        packet.setCameraOrientation(Vector3f.from(17, 18, 19));
        packet.setRawMoveVector(Vector2f.from(20, 21));
        return packet;
    }

    /**
     * 照网易的格式逐个字段写出。
     */
    private static byte[] neteaseInput(boolean cameraDeparted, boolean tail) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeFloatLE(1).writeFloatLE(2);
        buffer.writeFloatLE(4).writeFloatLE(5).writeFloatLE(6);
        buffer.writeFloatLE(7).writeFloatLE(8);
        buffer.writeFloatLE(3);
        // 第 43 位 STOP_FLYING 不变，之后的原版标志位后移两位
        BigInteger flags = BigInteger.ZERO
            .setBit(PlayerAuthInputData.JUMP_DOWN.ordinal())
            .setBit(43)
            .setBit(PlayerAuthInputData.RECEIVED_SERVER_DATA.ordinal() + 2)
            .setBit(PlayerAuthInputData.START_SPIN_ATTACK.ordinal() + 2);
        VarInts.writeUnsignedBigVarInt(buffer, flags);
        VarInts.writeUnsignedInt(buffer, InputMode.TOUCH.ordinal());
        VarInts.writeUnsignedInt(buffer, ClientPlayMode.NORMAL.ordinal());
        VarInts.writeUnsignedInt(buffer, InputInteractionModel.CLASSIC.ordinal());
        buffer.writeFloatLE(9).writeFloatLE(10);
        VarInts.writeUnsignedLong(buffer, 11);
        buffer.writeFloatLE(12).writeFloatLE(13).writeFloatLE(14);
        buffer.writeBoolean(cameraDeparted);
        buffer.writeFloatLE(15).writeFloatLE(16);
        buffer.writeFloatLE(17).writeFloatLE(18).writeFloatLE(19);
        buffer.writeFloatLE(20).writeFloatLE(21);
        if (tail) {
            buffer.writeBoolean(true).writeFloatLE(0.5f).writeFloatLE(0.25f).writeBoolean(true).writeBoolean(true).writeByte(1);
        }
        return ByteBufUtil.getBytes(buffer);
    }

    @Test
    void playerAuthInputShiftsFlagsAndAddsFields() throws Exception {
        assertEquals(43, PlayerAuthInputData.STOP_FLYING.ordinal());
        byte[] encoded = encode(NETEASE, input());
        byte[] expected = neteaseInput(false, false);
        // 写出时包尾的网易字段都填默认值
        assertArrayEquals(concat(expected, new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}), encoded);

        for (byte[] wire : List.of(neteaseInput(true, true), neteaseInput(false, false))) {
            PlayerAuthInputPacket decoded = decode(NETEASE, wire, PlayerAuthInputPacket.class);
            assertEquals(input().getInputData(), decoded.getInputData());
            assertEquals(Vector3f.from(12, 13, 14), decoded.getDelta());
            assertEquals(Vector2f.from(20, 21), decoded.getRawMoveVector());
            assertEquals(11, decoded.getTick());
        }
    }

    private static TextPacket chat() {
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.CHAT);
        packet.setSourceName("张三");
        packet.setMessage("hello");
        packet.setXuid("123");
        packet.setPlatformChatId("");
        packet.setFilteredMessage("");
        return packet;
    }

    @Test
    void chatCarriesParametersBeforeTheXuid() throws Exception {
        TextPacket packet = chat();
        packet.setParameters(List.of("a", "b"));
        byte[] vanilla = encode(VANILLA, packet);
        byte[] netease = encode(NETEASE, packet);
        byte[] suffix = concat(string("123"), string(""), new byte[] {0});
        byte[] parameters = concat(new byte[] {2}, string("a"), string("b"));
        byte[] prefix = Arrays.copyOf(vanilla, vanilla.length - suffix.length);
        assertArrayEquals(concat(prefix, parameters, suffix), netease);

        TextPacket decoded = decode(NETEASE, netease, TextPacket.class);
        assertEquals(TextPacket.Type.CHAT, decoded.getType());
        assertEquals("张三", decoded.getSourceName());
        assertEquals("hello", decoded.getMessage());
        assertEquals(List.of("a", "b"), decoded.getParameters());
        assertEquals("123", decoded.getXuid());

        // 系统消息与翻译消息的格式与原版相同
        TextPacket system = chat();
        system.setType(TextPacket.Type.SYSTEM);
        assertArrayEquals(encode(VANILLA, system), encode(NETEASE, system));
        assertEquals("hello", decode(NETEASE, encode(NETEASE, system), TextPacket.class).getMessage());
    }

    @Test
    void longMessagesAreShortened() throws Exception {
        TextPacket packet = chat();
        packet.setMessage("字".repeat(600));
        TextPacket decoded = decode(NETEASE, encode(NETEASE, packet), TextPacket.class);
        assertEquals(511 - "张三".length(), decoded.getMessage().length());

        packet.setType(TextPacket.Type.RAW);
        decoded = decode(NETEASE, encode(NETEASE, packet), TextPacket.class);
        assertEquals(511, decoded.getMessage().length());

        String emoji = "😀".repeat(300);
        assertTrue(Character.isLowSurrogate(emoji.charAt(511)));
        packet.setMessage(emoji);
        decoded = decode(NETEASE, encode(NETEASE, packet), TextPacket.class);
        assertEquals(510, decoded.getMessage().length());
    }

    @Test
    void commandRequestHasUnlimitBeforeTheVersion() throws Exception {
        CommandRequestPacket packet = new CommandRequestPacket();
        packet.setCommand("/list");
        packet.setCommandOriginData(new CommandOriginData(CommandOriginType.PLAYER, new UUID(1, 2), "", 0));
        packet.setInternal(false);
        byte[] vanilla = encode(VANILLA, packet);
        byte[] version = string("latest");
        byte[] expected = concat(Arrays.copyOf(vanilla, vanilla.length - version.length), new byte[] {1}, version);

        CommandRequestPacket decoded = decode(NETEASE, expected, CommandRequestPacket.class);
        assertEquals("/list", decoded.getCommand());
        assertFalse(decoded.isInternal());
        byte[] encoded = encode(NETEASE, packet);
        assertEquals(0, encoded[vanilla.length - version.length]);
        assertEquals(vanilla.length + 1, encoded.length);
    }

    @Test
    void animateEndsWithTheAttacker() throws Exception {
        AnimatePacket packet = new AnimatePacket();
        packet.setAction(AnimatePacket.Action.SWING_ARM);
        packet.setRuntimeEntityId(5);
        packet.setSwingSource(AnimatePacket.SwingSource.NONE);
        byte[] vanilla = encode(VANILLA, packet);
        assertArrayEquals(concat(vanilla, new byte[] {0}), encode(NETEASE, packet));

        ByteBuf attacker = Unpooled.buffer();
        VarInts.writeLong(attacker, -123456789L);
        AnimatePacket decoded = decode(NETEASE, concat(vanilla, ByteBufUtil.getBytes(attacker)), AnimatePacket.class);
        assertEquals(5, decoded.getRuntimeEntityId());
        assertEquals(AnimatePacket.Action.SWING_ARM, decoded.getAction());
    }

    @Test
    void resourcePackStackEndsWithTwoVipLists() throws Exception {
        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.setGameVersion("*");
        packet.getResourcePacks().add(new ResourcePackStackPacket.Entry("e5f5c938-a701-11eb-b2a3-047d7bb283ba", "1.0.0", ""));
        byte[] vanilla = encode(VANILLA, packet);
        byte[] netease = encode(NETEASE, packet);
        assertArrayEquals(concat(vanilla, new byte[] {0, 0}), netease);
        assertEquals(1, decode(NETEASE, netease, ResourcePackStackPacket.class).getResourcePacks().size());
        // 没有这两个列表时也能读
        assertEquals(1, decode(NETEASE, vanilla, ResourcePackStackPacket.class).getResourcePacks().size());
    }

    @Test
    void containerOpenEndsWithTheNeteaseFields() throws Exception {
        ContainerOpenPacket packet = new ContainerOpenPacket();
        packet.setId((byte) 1);
        packet.setType(ContainerType.CONTAINER);
        packet.setBlockPosition(Vector3i.from(1, 2, 3));
        packet.setUniqueEntityId(-1);
        byte[] vanilla = encode(VANILLA, packet);
        byte[] netease = encode(NETEASE, packet);
        assertArrayEquals(concat(vanilla, new byte[] {0}, string(""), string(""), new byte[] {0}), netease);
        assertEquals(ContainerType.CONTAINER, decode(NETEASE, netease, ContainerOpenPacket.class).getType());
        assertEquals(Vector3i.from(1, 2, 3), decode(NETEASE, vanilla, ContainerOpenPacket.class).getBlockPosition());
    }

    @Test
    void inventoryOptionsTolerateTheTrailingVarInt() throws Exception {
        SetPlayerInventoryOptionsPacket packet = new SetPlayerInventoryOptionsPacket();
        packet.setLeftTab(InventoryTabLeft.NONE);
        packet.setRightTab(InventoryTabRight.NONE);
        packet.setLayout(InventoryLayout.NONE);
        packet.setCraftingLayout(InventoryLayout.NONE);
        packet.setFiltering(true);
        byte[] vanilla = encode(VANILLA, packet);
        assertArrayEquals(vanilla, encode(NETEASE, packet));
        assertTrue(decode(NETEASE, concat(vanilla, new byte[] {7}), SetPlayerInventoryOptionsPacket.class).isFiltering());
        assertTrue(decode(NETEASE, vanilla, SetPlayerInventoryOptionsPacket.class).isFiltering());
    }

    @Test
    void biomesCarryTwoMoreFields() throws Exception {
        BiomeDefinitionListPacket packet = new BiomeDefinitionListPacket();
        packet.setBiomes(new BiomeDefinitions(Map.of(
            "minecraft:plains", new BiomeDefinitionData(null, 0.8f, 0.4f, 0f, 0.1f, 0.2f, new Color(0x44AFF5), true, List.of("plains", "overworld"), null),
            "minecraft:desert", new BiomeDefinitionData(null, 2f, 0f, 0f, 0.1f, 0.2f, new Color(0x32A598), false, List.of("desert"), null))));
        byte[] vanilla = encode(VANILLA, packet);
        byte[] netease = encode(NETEASE, packet);
        // 每个群系多一个 int32 与一个空字符串
        assertEquals(vanilla.length + 2 * 5, netease.length);
        BiomeDefinitionData plains = decode(NETEASE, netease, BiomeDefinitionListPacket.class).getBiomes().getDefinitions().get("minecraft:plains");
        assertEquals(0.8f, plains.getTemperature());
        assertTrue(plains.isRain());
        assertEquals(List.of("plains", "overworld"), plains.getTags());
    }

    @Test
    void enchantOptionsCarryModFields() throws Exception {
        PlayerEnchantOptionsPacket packet = new PlayerEnchantOptionsPacket();
        packet.getOptions().add(new EnchantOptionData(3, 1, List.of(new EnchantData(9, 1)), List.of(), List.of(new EnchantData(17, 2), new EnchantData(22, 1)), "abc", 7));
        byte[] vanilla = encode(VANILLA, packet);
        byte[] netease = encode(NETEASE, packet);
        // 每条附魔多一个空字符串，每个选项多一个空的自定义附魔列表
        assertEquals(vanilla.length + 3 + 1, netease.length);
        EnchantOptionData option = decode(NETEASE, netease, PlayerEnchantOptionsPacket.class).getOptions().get(0);
        assertEquals(packet.getOptions().get(0), option);
    }

    @Test
    void particlesAfterSneezeAreShifted() throws Exception {
        int sneeze = NeteaseCodec.PARTICLES.getId(ParticleType.SNEEZE);
        assertEquals(62, sneeze);
        for (ParticleType particle : List.of(ParticleType.SNEEZE, ParticleType.BUBBLE, ParticleType.CONDUIT, ParticleType.SHULKER_BULLET)) {
            LevelEventPacket packet = new LevelEventPacket();
            packet.setType(particle);
            packet.setPosition(Vector3f.ZERO);
            byte[] vanilla = encode(VANILLA, packet);
            byte[] netease = encode(NETEASE, packet);
            int vanillaId = VarInts.readInt(Unpooled.wrappedBuffer(vanilla));
            int neteaseId = VarInts.readInt(Unpooled.wrappedBuffer(netease));
            assertEquals(vanillaId - NeteaseCodec.LEVEL_EVENT_PARTICLE_BASE > sneeze ? vanillaId + 1 : vanillaId, neteaseId, particle.name());
            assertEquals(particle, decode(NETEASE, netease, LevelEventPacket.class).getType());
        }
    }

    @Test
    void containerSlotsFromRecipeItemsAreShifted() {
        BedrockCodecHelper vanilla = VANILLA.createHelper();
        BedrockCodecHelper netease = NETEASE.createHelper();
        for (ContainerSlotType slot : ContainerSlotType.values()) {
            ByteBuf vanillaBuffer = Unpooled.buffer();
            try {
                vanilla.writeContainerSlotType(vanillaBuffer, slot);
            } catch (RuntimeException e) {
                continue; // 898 没有的值
            }
            int vanillaId = vanillaBuffer.readUnsignedByte();
            ByteBuf buffer = Unpooled.buffer();
            netease.writeContainerSlotType(buffer, slot);
            assertEquals(vanillaId >= NeteaseCodec.FIRST_SHIFTED_CONTAINER_SLOT ? vanillaId + 1 : vanillaId, buffer.readUnsignedByte(), slot.name());
            buffer.readerIndex(0);
            assertEquals(slot, netease.readContainerSlotType(buffer));
        }
        ByteBuf buffer = Unpooled.buffer();
        netease.writeContainerSlotType(buffer, ContainerSlotType.RECIPE_ITEMS);
        netease.writeContainerSlotType(buffer, ContainerSlotType.DYNAMIC_CONTAINER);
        netease.writeContainerSlotType(buffer, ContainerSlotType.RECIPE_NATURE);
        assertArrayEquals(new byte[] {18, 64, 16}, ByteBufUtil.getBytes(buffer));
        // 第 17 位是网易的 RECIPE_CUSTOM，协议库没有对应的值
        assertThrows(RuntimeException.class, () -> netease.readContainerSlotType(Unpooled.wrappedBuffer(new byte[] {17})));
    }

    @Test
    void neteasePacketsAreRecognised() throws Exception {
        byte[] payload = {3, 1, 2, 3, 0x10, 0x20, 0x30, 0x40};
        BedrockPacket packet = NETEASE.tryDecode(NETEASE.createHelper(), Unpooled.wrappedBuffer(payload), NeteasePacket.PyRpc.ID);
        NeteasePacket.PyRpc pyRpc = assertInstanceOf(NeteasePacket.PyRpc.class, packet);
        assertArrayEquals(payload, pyRpc.payload());
        assertArrayEquals(payload, encode(NETEASE, pyRpc));
        for (int id : new int[] {NeteasePacket.StoreBuySuccess.ID, NeteasePacket.NeteaseJson.ID, NeteasePacket.SetDimensionLocalTime.ID,
            NeteasePacket.ConfirmSkin.ID, NeteasePacket.SyncSkin.ID}) {
            NeteasePacket decoded = assertInstanceOf(NeteasePacket.class, NETEASE.tryDecode(NETEASE.createHelper(), Unpooled.wrappedBuffer(payload), id));
            assertEquals(id, decoded.packetId());
        }
        // 国际版的编解码器不认识它们
        assertInstanceOf(UnknownPacket.class, VANILLA.tryDecode(VANILLA.createHelper(), Unpooled.wrappedBuffer(payload), NeteasePacket.PyRpc.ID));
        assertSame(NETEASE, GameProtocol.getNeteaseCodec(898));
    }
}
