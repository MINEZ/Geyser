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
import org.cloudburstmc.protocol.bedrock.codec.v291.serializer.ContainerOpenSerializer_v291;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;

/**
 * MINEZ：网易 898 的 ContainerOpenPacket 在包尾追加：布尔 {@code isIgnoreBlock}（1.21.124 起）、
 * 字符串 {@code screenName}、字符串 {@code customDescription}、布尔 {@code isClosedDropItem}（898 起）。
 * 这些是网易 Mod 界面用的，写默认值；读取时只在还有剩余字节时读。
 */
public class NeteaseContainerOpenSerializer extends ContainerOpenSerializer_v291 {
    public static final NeteaseContainerOpenSerializer INSTANCE = new NeteaseContainerOpenSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ContainerOpenPacket packet) {
        super.serialize(buffer, helper, packet);
        buffer.writeBoolean(false); // isIgnoreBlock
        helper.writeString(buffer, ""); // screenName
        helper.writeString(buffer, ""); // customDescription
        buffer.writeBoolean(false); // isClosedDropItem
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ContainerOpenPacket packet) {
        super.deserialize(buffer, helper, packet);
        if (buffer.isReadable()) {
            buffer.readBoolean();
        }
        if (buffer.isReadable()) {
            helper.readString(buffer);
        }
        if (buffer.isReadable()) {
            helper.readString(buffer);
        }
        if (buffer.isReadable()) {
            buffer.readBoolean();
        }
    }
}
