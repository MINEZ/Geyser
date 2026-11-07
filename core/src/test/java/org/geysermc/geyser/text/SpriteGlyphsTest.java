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

package org.geysermc.geyser.text;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：玩家列表的名字中，多块拼成的动态图标（称号）换到第二行。
 */
class SpriteGlyphsTest {
    private static final String TITLE = "";
    private static final String ICON = "";

    @BeforeEach
    void setUp() {
        SpriteGlyphs.setAnimated(Map.of(
            TITLE, Map.of("texture", "textures/minez/anim/item/title/builder", "tiles", 4L),
            // 只有一块的动态图标不换行。
            ICON, Map.of("texture", "textures/minez/anim/icon", "tiles", 1L)));
    }

    @AfterEach
    void tearDown() {
        SpriteGlyphs.setAnimated(Map.of());
    }

    @Test
    void titleMovesToSecondLine() {
        // 前面的空格去掉，颜色代码移到换行之后。
        assertEquals("§b测试\n§f" + TITLE, SpriteGlyphs.groupOnNewLine("§b测试 §f" + TITLE));
        assertEquals("§b测试\n§r§f" + TITLE + " §7后缀", SpriteGlyphs.groupOnNewLine("§b测试§r §f" + TITLE + " §7后缀"));
    }

    @Test
    void namesWithoutTitlesAreUnchanged() {
        assertEquals("§b测试", SpriteGlyphs.groupOnNewLine("§b测试"));
        assertEquals("§b测试 " + ICON, SpriteGlyphs.groupOnNewLine("§b测试 " + ICON));
        // 称号前面没有别的文字时不换行。
        assertEquals(" §f" + TITLE, SpriteGlyphs.groupOnNewLine(" §f" + TITLE));
    }
}
