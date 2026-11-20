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

package org.geysermc.geyser.session.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmbientBlockEffectsTest {

    @Test
    void packsBlockPositions() {
        int[][] positions = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-30_000_000, -2048, 12345}, {17, 2047, -33}};
        for (int[] p : positions) {
            long packed = AmbientBlockEffects.pack(p[0], p[1], p[2]);
            assertEquals(p[0], AmbientBlockEffects.unpackX(packed));
            assertEquals(p[1], AmbientBlockEffects.unpackY(packed));
            assertEquals(p[2], AmbientBlockEffects.unpackZ(packed));
        }
    }

    @Test
    void animateTickChanceMatchesJavaSampling() {
        // 玩家所在格：667 ×（1/16³ + 1/32³）
        assertEquals(667 * (1.0 / 4096 + 1.0 / 32768), AmbientBlockEffects.animateTickChance(0, 0, 0), 1e-12);
        // 半径 16 之外只剩半径 32 的部分
        assertEquals(667 * Math.pow(16.0 / 1024, 3), AmbientBlockEffects.animateTickChance(16, 16, 16), 1e-12);
        // 32 格以外不会被抽中
        assertEquals(0, AmbientBlockEffects.animateTickChance(32, 0, 0));
        // 对称
        assertEquals(AmbientBlockEffects.animateTickChance(-5, 3, -7), AmbientBlockEffects.animateTickChance(5, -3, 7), 1e-15);
    }
}
