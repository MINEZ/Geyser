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
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.ResourcePackStackSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;

/**
 * MINEZ：网易 898 的 ResourcePackStackPacket 在 {@code hasEditorPacks} 之后追加两个列表：VIP 资源包与 VIP 行为包，
 * 条目格式与普通包栈相同。Geyser 没有这类包，写两个空列表；读取时每个列表前都先看是否还有剩余字节。
 */
public class NeteaseResourcePackStackSerializer extends ResourcePackStackSerializer_v898 {
    public static final NeteaseResourcePackStackSerializer INSTANCE = new NeteaseResourcePackStackSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ResourcePackStackPacket packet) {
        super.serialize(buffer, helper, packet);
        VarInts.writeUnsignedInt(buffer, 0); // VIP 资源包
        VarInts.writeUnsignedInt(buffer, 0); // VIP 行为包
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ResourcePackStackPacket packet) {
        super.deserialize(buffer, helper, packet);
        for (int i = 0; i < 2 && buffer.isReadable(); i++) {
            helper.readArray(buffer, new ArrayList<>(), this::readEntry);
        }
    }
}
