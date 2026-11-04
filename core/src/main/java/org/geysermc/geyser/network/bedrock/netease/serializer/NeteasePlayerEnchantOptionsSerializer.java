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

/*
 * MINEZ：本文件改写自 CloudburstMC Protocol（https://github.com/CloudburstMC/Protocol）中的 PlayerEnchantOptionsSerializer_v407
 * 等序列化器，按网易中国版客户端的数据包格式修改了部分字段。CloudburstMC Protocol 以 Apache License 2.0 授权，
 * Copyright 2020 NukkitX，许可证全文见本仓库的
 * core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt，构建产物中也附带一份。
 */

package org.geysermc.geyser.network.bedrock.netease.serializer;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v407.serializer.PlayerEnchantOptionsSerializer_v407;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.List;

/**
 * MINEZ：网易的 PlayerEnchantOptionsPacket 中，每个选项在原版的三个附魔列表之后多一个自定义附魔列表，
 * 每条附魔在类型与等级之后多一个字符串（Mod 附魔的标识）。Geyser 只有原版附魔，写空列表与空字符串。
 */
public class NeteasePlayerEnchantOptionsSerializer extends PlayerEnchantOptionsSerializer_v407 {
    public static final NeteasePlayerEnchantOptionsSerializer INSTANCE = new NeteasePlayerEnchantOptionsSerializer();

    @Override
    protected void writeOption(ByteBuf buffer, BedrockCodecHelper helper, EnchantOptionData option) {
        VarInts.writeUnsignedInt(buffer, option.getCost());
        buffer.writeIntLE(option.getPrimarySlot());
        writeEnchants(buffer, helper, option.getEnchants0());
        writeEnchants(buffer, helper, option.getEnchants1());
        writeEnchants(buffer, helper, option.getEnchants2());
        writeEnchants(buffer, helper, List.of()); // 自定义附魔。
        helper.writeString(buffer, option.getEnchantName());
        VarInts.writeUnsignedInt(buffer, option.getEnchantNetId());
    }

    @Override
    protected EnchantOptionData readOption(ByteBuf buffer, BedrockCodecHelper helper) {
        int cost = VarInts.readUnsignedInt(buffer);
        int primarySlot = buffer.readIntLE();
        List<EnchantData> enchants0 = readEnchants(buffer, helper);
        List<EnchantData> enchants1 = readEnchants(buffer, helper);
        List<EnchantData> enchants2 = readEnchants(buffer, helper);
        readEnchants(buffer, helper); // 自定义附魔。
        String enchantName = helper.readString(buffer);
        int enchantNetId = VarInts.readUnsignedInt(buffer);
        return new EnchantOptionData(cost, primarySlot, enchants0, enchants1, enchants2, enchantName, enchantNetId);
    }

    private void writeEnchants(ByteBuf buffer, BedrockCodecHelper helper, List<EnchantData> enchants) {
        helper.writeArray(buffer, enchants, (buf, enchant) -> {
            serializeEnchant(buf, enchant);
            helper.writeString(buf, ""); // Mod 附魔的标识。
        });
    }

    private List<EnchantData> readEnchants(ByteBuf buffer, BedrockCodecHelper helper) {
        List<EnchantData> enchants = new ObjectArrayList<>();
        helper.readArray(buffer, enchants, buf -> {
            EnchantData enchant = deserializeEnchant(buf);
            helper.readString(buf);
            return enchant;
        });
        return enchants;
    }
}
