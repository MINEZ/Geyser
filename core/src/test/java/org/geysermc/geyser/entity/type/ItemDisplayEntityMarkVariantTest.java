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

package org.geysermc.geyser.entity.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：物品展示实体的标记变种（朝向 + 360 × (俯仰 + 90)，见 {@link ItemDisplayEntity}）。
 */
public class ItemDisplayEntityMarkVariantTest {

    @Test
    void packsYawAndPitch() {
        // 平视：俯仰 0 → 90 × 360
        assertEquals(90 * 360, ItemDisplayEntity.markVariant(0, 0));
        assertEquals(90 * 360 + 270, ItemDisplayEntity.markVariant(-90, 0));
        assertEquals(90 * 360 + 359, ItemDisplayEntity.markVariant(359.4f, 0));
        // 俯仰取整并限制在 -90~90
        assertEquals(135 * 360 + 10, ItemDisplayEntity.markVariant(10, 45.2f));
        assertEquals(10, ItemDisplayEntity.markVariant(10, -120));
        assertEquals(180 * 360 + 10, ItemDisplayEntity.markVariant(10, 95));
    }
}
