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

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.codec.v291.serializer.LevelEventSerializer_v291;
import org.cloudburstmc.protocol.bedrock.codec.v361.serializer.LevelEventGenericSerializer_v361;
import org.cloudburstmc.protocol.bedrock.codec.v898.BedrockCodecHelper_v898;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.transformer.TypeMapTransformer;
import org.cloudburstmc.protocol.common.util.TypeMap;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteaseAnimateSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteaseBiomeDefinitionListSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteaseCommandRequestSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteaseContainerOpenSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteasePlayerAuthInputSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteasePlayerEnchantOptionsSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteaseResourcePackStackSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteaseSetPlayerInventoryOptionsSerializer;
import org.geysermc.geyser.network.bedrock.netease.serializer.NeteaseTextSerializer;

/**
 * MINEZ：网易中国版 1.21.130（协议 898）的编解码器。
 * <p>
 * 以经过 {@link CodecProcessor} 处理的国际版 898 编解码器为基础，换上网易改动过的序列化器与枚举映射，并识别网易专有的数据包。
 * 只有网易客户端的会话使用它，国际版的编解码器不受影响。继承 {@link Bedrock_v898} 只是为了取用它的映射表。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class NeteaseCodec extends Bedrock_v898 {
    /**
     * 0x4000 加上粒子 ID 即为该粒子的关卡事件 ID。
     */
    static final int LEVEL_EVENT_PARTICLE_BASE = 0x4000;
    /**
     * 网易在原版 {@code RECIPE_ITEMS} 的位置插入了自己的 {@code RECIPE_CUSTOM}。
     */
    static final int FIRST_SHIFTED_CONTAINER_SLOT = 17;

    private NeteaseCodec() {
    }

    /**
     * 网易在 {@code SNEEZE} 之后插入了一种粒子，ID 大于 {@code SNEEZE} 的原版粒子在线上都要加 1。
     */
    static final TypeMap<ParticleType> PARTICLES = shiftParticles(PARTICLE_TYPES);

    /**
     * 原版 ID 不小于 17 的容器槽位类型在线上都要加 1，第 17 位是 {@code RECIPE_CUSTOM}，协议库没有对应的值，留空。
     * 网易在末尾另有的两项同样没有对应的值。
     */
    static final TypeMap<ContainerSlotType> CONTAINER_SLOTS = CONTAINER_SLOT_TYPES.toBuilder()
        .shift(FIRST_SHIFTED_CONTAINER_SLOT, 1)
        .build();

    static final TypeMap<LevelEventType> LEVEL_EVENT_TYPES = shiftParticleEvents(LEVEL_EVENTS);

    static final EntityDataTypeMap ENTITY_DATA_TYPES = ENTITY_DATA.toBuilder()
        .update(EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE, new TypeMapTransformer<>(PARTICLES))
        .build();

    /**
     * @param base 经过 {@link CodecProcessor} 处理的国际版 898 编解码器。
     */
    static BedrockCodec build(BedrockCodec base) {
        return base.toBuilder()
            .helper(() -> new BedrockCodecHelper_v898(ENTITY_DATA_TYPES, GAME_RULE_TYPES, ITEM_STACK_REQUEST_TYPES, CONTAINER_SLOTS,
                PLAYER_ABILITIES, TEXT_PROCESSING_ORIGINS))
            .updateSerializer(PlayerAuthInputPacket.class, NeteasePlayerAuthInputSerializer.INSTANCE)
            .updateSerializer(TextPacket.class, NeteaseTextSerializer.INSTANCE)
            .updateSerializer(CommandRequestPacket.class, NeteaseCommandRequestSerializer.INSTANCE)
            .updateSerializer(AnimatePacket.class, NeteaseAnimateSerializer.INSTANCE)
            .updateSerializer(ResourcePackStackPacket.class, NeteaseResourcePackStackSerializer.INSTANCE)
            .updateSerializer(ContainerOpenPacket.class, NeteaseContainerOpenSerializer.INSTANCE)
            .updateSerializer(SetPlayerInventoryOptionsPacket.class, NeteaseSetPlayerInventoryOptionsSerializer.INSTANCE)
            .updateSerializer(BiomeDefinitionListPacket.class, NeteaseBiomeDefinitionListSerializer.INSTANCE)
            .updateSerializer(PlayerEnchantOptionsPacket.class, NeteasePlayerEnchantOptionsSerializer.INSTANCE)
            .updateSerializer(LevelEventPacket.class, new LevelEventSerializer_v291(LEVEL_EVENT_TYPES))
            .updateSerializer(LevelEventGenericPacket.class, new LevelEventGenericSerializer_v361(LEVEL_EVENT_TYPES))
            // 网易客户端会用区块缓存，上游把这个包设为非法、收到就断开。Geyser 不启用区块缓存，忽略它即可。
            .updateSerializer(ClientCacheBlobStatusPacket.class, CodecProcessor.IGNORED_SERIALIZER)
            // 网易专有的数据包。方向按资料应为单向的几个也接受双向，收到后一律丢弃，见 LoggingPacketHandler。
            .registerPacket(NeteasePacket.PyRpc::new, raw(), NeteasePacket.PyRpc.ID, PacketRecipient.BOTH)
            .registerPacket(NeteasePacket.StoreBuySuccess::new, raw(), NeteasePacket.StoreBuySuccess.ID, PacketRecipient.BOTH)
            .registerPacket(NeteasePacket.NeteaseJson::new, raw(), NeteasePacket.NeteaseJson.ID, PacketRecipient.BOTH)
            .registerPacket(NeteasePacket.SetDimensionLocalTime::new, raw(), NeteasePacket.SetDimensionLocalTime.ID, PacketRecipient.BOTH)
            .registerPacket(NeteasePacket.ConfirmSkin::new, raw(), NeteasePacket.ConfirmSkin.ID, PacketRecipient.BOTH)
            .registerPacket(NeteasePacket.SyncSkin::new, raw(), NeteasePacket.SyncSkin.ID, PacketRecipient.BOTH)
            .build();
    }

    private static <T extends NeteasePacket> BedrockPacketSerializer<T> raw() {
        return (BedrockPacketSerializer<T>) (BedrockPacketSerializer) NeteasePacket.RAW_SERIALIZER;
    }

    private static TypeMap<ParticleType> shiftParticles(TypeMap<ParticleType> vanilla) {
        int sneeze = vanilla.getId(ParticleType.SNEEZE);
        TypeMap.Builder<ParticleType> builder = TypeMap.builder(ParticleType.class);
        vanilla.forEach((id, particle) -> builder.insert(id > sneeze ? id + 1 : id, particle));
        return builder.build();
    }

    private static TypeMap<LevelEventType> shiftParticleEvents(TypeMap<LevelEventType> vanilla) {
        int sneeze = LEVEL_EVENT_PARTICLE_BASE + PARTICLE_TYPES.getId(ParticleType.SNEEZE);
        TypeMap.Builder<LevelEventType> builder = TypeMap.builder(LevelEventType.class);
        vanilla.forEach((id, event) -> builder.insert(event instanceof ParticleType && id > sneeze ? id + 1 : id, event));
        return builder.build();
    }
}
