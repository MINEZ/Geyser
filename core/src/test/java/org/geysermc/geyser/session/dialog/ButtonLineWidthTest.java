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
 */

package org.geysermc.geyser.session.dialog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonLineWidthTest {

    @Test
    void colorCodesDoNotCount() {
        assertEquals(4, DialogHolder.displayWidth("§a礼包§r"));
        assertEquals(5, DialogHolder.displayWidth("§l§cHello"));
    }

    @Test
    void fullWidthCharactersCountTwo() {
        assertEquals(6, DialogHolder.displayWidth("周目，"));
        assertEquals(3, DialogHolder.displayWidth("abc"));
        assertEquals(4, DialogHolder.displayWidth("한글"));
    }

    @Test
    void shortTooltipsStayOnTheButton() {
        assertTrue(DialogHolder.fitsButtonLine("出售仅限当前周目、当前分区使用的物品。"));
    }

    @Test
    void longTooltipsMoveToTheBody() {
        // 2026-11-18 基岩版实测被截断的一行
        assertFalse(DialogHolder.fitsButtonLine("出售超值礼包，礼包中的物品与冷却重置次数仅限当前周目、当前分区使用。"));
    }
}
