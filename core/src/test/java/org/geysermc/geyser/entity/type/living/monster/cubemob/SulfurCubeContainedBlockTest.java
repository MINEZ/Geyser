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

package org.geysermc.geyser.entity.type.living.monster.cubemob;

import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Axis;
import org.geysermc.geyser.scoreboard.network.util.GeyserMockContext;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.BlockStateProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MINEZ: 硫方怪体内显示的方块照 Java 版 SulfurCubeRenderer 取。
 */
public class SulfurCubeContainedBlockTest {

    @Test
    void containedBlocks() {
        // 物品表初始化时要用到 GeyserImpl
        GeyserMockContext.mockContext(context -> {
            blockItemsShowTheirBlock();
            blockStateComponentIsApplied();
            skullsAndNonBlocksShowNothing();
        });
    }

    private void blockItemsShowTheirBlock() {
        BlockState state = SulfurCubeEntity.containedBlockState(Items.OAK_LOG, null);
        assertNotNull(state);
        assertEquals(Blocks.OAK_LOG.defaultBlockState(), state);
    }

    private void blockStateComponentIsApplied() {
        BlockState state = SulfurCubeEntity.containedBlockState(Items.OAK_LOG,
            new BlockStateProperties(Map.of("axis", "x", "unknown", "1")));
        assertNotNull(state);
        assertEquals(Axis.X, state.getValue(Properties.AXIS));
    }

    private void skullsAndNonBlocksShowNothing() {
        assertNull(SulfurCubeEntity.containedBlockState(Items.ZOMBIE_HEAD, null));
        assertNull(SulfurCubeEntity.containedBlockState(Items.DIAMOND, null));
    }
}
