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

package org.geysermc.geyser.session.dialog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：对话框的文字按基岩版表单的背景替换颜色，格式代码不变。
 */
class FormColorsTest {

    @Test
    void lightBackgroundsGetDarkColors() {
        assertEquals("§0白§0灰§n黄§n金§2绿§3青§5紫§c红§l粗", FormColors.onLight("§f白§7灰§e黄§6金§a绿§b青§d紫§c红§l粗"));
    }

    @Test
    void darkBackgroundsGetLightGreys() {
        assertEquals("§7黑§7深灰§f白", FormColors.onDark("§0黑§8深灰§f白"));
    }

    @Test
    void textWithoutCodesIsUnchanged() {
        assertEquals("关闭", FormColors.onLight("关闭"));
    }
}
