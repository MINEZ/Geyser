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

package org.geysermc.geyser.item;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.registry.BlockRegistries;

/**
 * MINEZ：按方块绘制的自定义物品。
 * <p>
 * 物品定义带有以方块作为图标的方块放置组件（映射文件中 bedrock_options 的 {@code block_placer}，{@code use_block_icon} 为 true）时，
 * 转换给基岩版的不是这个自定义物品，而是它放置的那个自定义方块的方块物品（与自定义头颅相同）。只以方块作为图标时，基岩版只有物品栏中的
 * 图标是立体的，拿在手上仍是平面的贴图（2026-11-19 实测）；换成方块物品后，手上、物品栏与掉落物都由客户端按原版方块的画法画出，
 * 与拿着原版方块完全相同。
 */
public final class BlockIconItems {
    private BlockIconItems() {
    }

    /**
     * 物品定义要按哪个自定义方块绘制；不按方块绘制，或者方块没有注册时为 null。
     */
    public static @Nullable CustomBlockData block(CustomItemDefinition definition) {
        GeyserBlockPlacer placer = definition.components().get(GeyserItemDataComponents.BLOCK_PLACER);
        if (placer == null || !placer.useBlockIcon()) {
            return null;
        }
        String identifier = placer.block().toString();
        for (CustomBlockData block : BlockRegistries.CUSTOM_BLOCKS.get()) {
            if (block.identifier().equals(identifier)) {
                return block;
            }
        }
        return null;
    }
}
