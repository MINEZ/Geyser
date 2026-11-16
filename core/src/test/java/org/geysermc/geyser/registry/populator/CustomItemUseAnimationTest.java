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


package org.geysermc.geyser.registry.populator;

import org.geysermc.mcprotocollib.protocol.data.game.item.component.Consumable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：自定义物品的使用动画。套了皮肤的三叉戟以 TRIDENT 动画注册，须有基岩版的 ID（6），否则客户端蓄力时不举手。
 */
class CustomItemUseAnimationTest {
    @Test
    void tridentRaisesTheArm() {
        assertEquals(6, CustomItemRegistryPopulator.BEDROCK_ANIMATIONS.get(Consumable.ItemUseAnimation.TRIDENT));
        assertEquals(6, CustomItemRegistryPopulator.BEDROCK_ANIMATIONS.get(Consumable.ItemUseAnimation.SPEAR));
    }

    @Test
    void bowAndCrossbowKeepTheirAnimations() {
        assertEquals(4, CustomItemRegistryPopulator.BEDROCK_ANIMATIONS.get(Consumable.ItemUseAnimation.BOW));
        assertEquals(9, CustomItemRegistryPopulator.BEDROCK_ANIMATIONS.get(Consumable.ItemUseAnimation.CROSSBOW));
        assertEquals(3, CustomItemRegistryPopulator.BEDROCK_ANIMATIONS.get(Consumable.ItemUseAnimation.BLOCK));
    }
}
