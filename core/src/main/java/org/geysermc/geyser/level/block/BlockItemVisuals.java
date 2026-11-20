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

package org.geysermc.geyser.level.block;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MINEZ：自定义方块的方块物品另用的外观（基岩版方块的 {@code minecraft:item_visual} 组件：物品用的几何体与材质）。
 * <p>
 * 方块物品默认按方块的默认状态画出，有的方块与 Java 版的物品模型不同：按钮的默认状态挂在墙上，Java 版的物品是平放的小方块；
 * 栅栏、墙的默认状态只有柱子，Java 版的物品另有两边的栏杆。映射文件 {@code blocks} 中的一项可以写
 * {@code "item_visual": {"geometry": ..., "material_instances": ...}}（写法与 {@code state_overrides} 中的相同），读入后记在这里，
 * 生成方块数据时加在方块的基础组件上。
 */
public final class BlockItemVisuals {
    private static final Map<String, CustomBlockComponents> VISUALS = new ConcurrentHashMap<>();

    private BlockItemVisuals() {
    }

    public static void register(String blockIdentifier, CustomBlockComponents visual) {
        VISUALS.put(blockIdentifier, visual);
    }

    public static @Nullable CustomBlockComponents get(String blockIdentifier) {
        return VISUALS.get(blockIdentifier);
    }
}
