/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
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

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MINEZ：发给 26.40 之前的客户端（网易 1.21.130）的实体列表。
 * <p>
 * 列表中每个实体的 bid 是它的基础实体（行为包的 runtime_identifier），客户端按它决定实体的类。26.x 新增的实体 1.21.130 没有对应的类，
 * bid 为空时客户端只把它当成通用实体：不画手持物品等。这里给这些实体填上 1.21.130 已有的、最接近的原版实体，客户端实体（样子）
 * 仍按实体自己的名字取资源包中的定义。
 * <ul>
 *     <li>硫方怪 → 史莱姆：基岩版的硫方怪与史莱姆同为方块生物，吸收的方块是主手物品，由客户端画在模型的 rightItem 骨骼上；
 *     通用实体不画手持物品，体内的方块不显示。</li>
 * </ul>
 */
public final class LegacyEntityIdentifiers {
    static final Map<String, String> BASES = Map.of(
        "minecraft:sulfur_cube", "minecraft:slime"
    );

    private static NbtMap source;
    private static NbtMap legacy;

    private LegacyEntityIdentifiers() {
    }

    /**
     * @return 给这个协议版本的客户端发的实体列表
     */
    public static synchronized NbtMap forProtocol(int protocolVersion, NbtMap identifiers) {
        if (GameProtocol.is26_40orHigher(protocolVersion)) {
            return identifiers;
        }
        if (identifiers != source) {
            legacy = withBases(identifiers);
            source = identifiers;
        }
        return legacy;
    }

    static NbtMap withBases(NbtMap identifiers) {
        List<NbtMap> idlist = new ArrayList<>();
        for (NbtMap entry : identifiers.getList("idlist", NbtType.COMPOUND)) {
            String base = BASES.get(entry.getString("id"));
            if (base != null && entry.getString("bid").isEmpty()) {
                entry = entry.toBuilder().putString("bid", base).build();
            }
            idlist.add(entry);
        }
        return identifiers.toBuilder().putList("idlist", NbtType.COMPOUND, idlist).build();
    }
}
