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

package org.geysermc.geyser.network.bedrock.netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.CommandRequestSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

/**
 * MINEZ：网易 898 的 CommandRequestPacket 在 {@code internal} 之后、版本字符串之前多一个布尔值 {@code unlimit}。
 * 它不影响 Java 端的命令权限，读取后丢弃。
 */
public class NeteaseCommandRequestSerializer extends CommandRequestSerializer_v898 {
    public static final NeteaseCommandRequestSerializer INSTANCE = new NeteaseCommandRequestSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, CommandRequestPacket packet) {
        helper.writeString(buffer, packet.getCommand());
        helper.writeCommandOrigin(buffer, packet.getCommandOriginData());
        buffer.writeBoolean(packet.isInternal());
        buffer.writeBoolean(false); // unlimit
        helper.writeString(buffer, "latest");
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, CommandRequestPacket packet) {
        packet.setCommand(helper.readStringMaxLen(buffer, 1000));
        packet.setCommandOriginData(helper.readCommandOrigin(buffer));
        packet.setInternal(buffer.readBoolean());
        buffer.readBoolean(); // unlimit
        helper.readString(buffer);
        packet.setVersion(48); // 与原版 898 的序列化器相同
    }
}
