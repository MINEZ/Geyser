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

package org.geysermc.geyser.registry.populator.conversion;

import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.item.type.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * MINEZ：把方块与物品降级到基岩版 1.21.130（协议 898，“坐骑浩劫”）。
 * <p>
 * 先按 26.40 档处理（{@link ICanHasStates}：26.40 以后新增的连接、拐角等状态去掉，白杨木、羊毛与混凝土的台阶与楼梯等换成替身），
 * 再按上游曾经的 26.0 档处理（{@link GoldenDandelionConverter}、{@link ChaosCubedConverter}：金蒲公英、硫磺与朱砂换成替身）。
 * 1.21.130 的方块调色板与物品表和 26.0 逐字节相同，所以 26.0 档的回退表可以直接沿用。
 */
public class MountsOfMayhemConverter implements BlockRemapper {
    public static final MountsOfMayhemConverter INSTANCE = new MountsOfMayhemConverter();

    @Override
    public NbtMap remapBlock(NbtMap tag) {
        return GoldenDandelionConverter.convertBlock(ICanHasStates.INSTANCE.remapBlock(tag));
    }

    /**
     * 26.40 档的替身里有朱砂楼梯等 26.20 才有的物品，要再按 26.0 档的回退表换一次。
     */
    public static Map<Item, Item> itemMappings() {
        Map<Item, Item> olderMappings = GoldenDandelionConverter.convertItem();
        Map<Item, Item> mappings = new HashMap<>(WildernessBoundConverter.itemMappings());
        mappings.replaceAll((item, fallback) -> olderMappings.getOrDefault(fallback, fallback));
        mappings.putAll(olderMappings);
        return mappings;
    }
}
