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
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.AnimateSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.common.util.VarInts;

/**
 * MINEZ：网易 898 的 AnimatePacket 在包尾多一个有符号 VarLong，即攻击者的实体唯一 ID。
 * Java 端的攻击另有交互包，这里写 0、读取时丢弃。
 */
public class NeteaseAnimateSerializer extends AnimateSerializer_v898 {
    public static final NeteaseAnimateSerializer INSTANCE = new NeteaseAnimateSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, AnimatePacket packet) {
        super.serialize(buffer, helper, packet);
        VarInts.writeLong(buffer, 0);
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, AnimatePacket packet) {
        super.deserialize(buffer, helper, packet);
        if (buffer.isReadable()) {
            VarInts.readLong(buffer);
        }
    }
}
