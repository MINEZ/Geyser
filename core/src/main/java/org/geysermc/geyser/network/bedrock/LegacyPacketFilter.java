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
import io.netty.buffer.ByteBufAllocator;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;
import org.geysermc.geyser.GeyserImpl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * MINEZ：1.21.130（协议 898）的音效、关卡事件、粒子与实体事件枚举比当前映射少，例如硫方怪、白杨树叶、金蒲公英用到的 26.x 枚举。
 * 编码到客户端没有的枚举值或数据包时编解码器会抛异常，Geyser 随即踢出玩家。这里在发送前丢掉这类数据包（只是少一个音效或粒子），
 * 只对低于 26.40 的版本生效，26.40 及以上的行为与上游一致。
 * <p>
 * 不支持的枚举值在第一次用到某个协议时，逐个试编码一次得出，不必随协议库维护列表。
 */
public final class LegacyPacketFilter {
    private static final Map<Integer, Set<Object>> UNSUPPORTED = new ConcurrentHashMap<>();

    private LegacyPacketFilter() {
    }

    /**
     * @return 数据包能否按此编解码器编码；不能时调用方应丢弃它。
     */
    public static boolean canEncode(BedrockCodec codec, BedrockPacket packet) {
        int protocolVersion = codec.getProtocolVersion();
        // 登录前用的兼容编解码器（协议号为 0 等）不在此列。
        if (protocolVersion < Bedrock_v898.CODEC.getProtocolVersion() || GameProtocol.is26_40orHigher(protocolVersion)) {
            return true;
        }

        if (!(packet instanceof UnknownPacket) && codec.getPacketDefinition(packet.getClass()) == null) {
            // 26.x 新增的数据包。已知的发送处都按版本跳过了，这里只是兜底。
            GeyserImpl.getInstance().getLogger().debug("Dropped " + packet.getClass().getSimpleName()
                + ", which protocol " + protocolVersion + " does not have");
            return false;
        }

        Object value = switch (packet) {
            case LevelSoundEventPacket soundPacket -> soundPacket.getSound();
            case LevelEventPacket eventPacket -> eventPacket.getType();
            case LevelEventGenericPacket eventPacket -> eventPacket.getType();
            case EntityEventPacket eventPacket -> eventPacket.getType();
            default -> null;
        };
        if (value == null) {
            return true;
        }

        Set<Object> unsupported = UNSUPPORTED.computeIfAbsent(protocolVersion, protocol -> probe(codec));
        if (unsupported.contains(value)) {
            GeyserImpl.getInstance().getLogger().debug("Dropped " + packet.getClass().getSimpleName() + " with " + value
                + ", which protocol " + protocolVersion + " does not have");
            return false;
        }
        return true;
    }

    private static Set<Object> probe(BedrockCodec codec) {
        BedrockCodecHelper helper = codec.createHelper();
        Set<Object> unsupported = new HashSet<>();

        probe(codec, helper, unsupported, SoundEvent.values(), sound -> {
            LevelSoundEventPacket packet = new LevelSoundEventPacket();
            packet.setSound(sound);
            packet.setPosition(Vector3f.ZERO);
            packet.setIdentifier("");
            packet.setExtraData(-1);
            return packet;
        });
        Function<LevelEventType, BedrockPacket> levelEvent = type -> {
            LevelEventPacket packet = new LevelEventPacket();
            packet.setType(type);
            packet.setPosition(Vector3f.ZERO);
            return packet;
        };
        probe(codec, helper, unsupported, LevelEvent.values(), levelEvent);
        probe(codec, helper, unsupported, ParticleType.values(), levelEvent);
        probe(codec, helper, unsupported, EntityEventType.values(), type -> {
            EntityEventPacket packet = new EntityEventPacket();
            packet.setType(type);
            return packet;
        });

        GeyserImpl.getInstance().getLogger().debug("Protocol " + codec.getProtocolVersion() + " lacks " + unsupported.size() + " enum values: " + unsupported);
        return Set.copyOf(unsupported);
    }

    private static <T> void probe(BedrockCodec codec, BedrockCodecHelper helper, Set<Object> unsupported, T[] values, Function<? super T, BedrockPacket> factory) {
        for (T value : values) {
            ByteBuf buffer = ByteBufAllocator.DEFAULT.heapBuffer();
            try {
                codec.tryEncode(helper, buffer, factory.apply(value));
            } catch (Exception e) {
                unsupported.add(value);
            } finally {
                buffer.release();
            }
        }
    }
}
