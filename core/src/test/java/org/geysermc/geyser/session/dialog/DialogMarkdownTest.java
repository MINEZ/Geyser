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
 * MINEZ：对话框正文中的 Markdown 按 Bridge 的规则转成基岩版能显示的文字，颜色代码照常生效。
 */
class DialogMarkdownTest {

    @Test
    void headingIsBoldWithoutHashes() {
        assertEquals("§f§l§e§l服务器菜单§r§e\n§f正文", DialogMarkdown.render("§f### §e服务器菜单\n§f正文"));
    }

    @Test
    void hashWithoutSpaceIsNotAHeading() {
        assertEquals("#标签", DialogMarkdown.render("#标签"));
    }

    @Test
    void bulletsUseDotsAndNestedListsAreIndented() {
        assertEquals("§6• §f第一项\n    §7• §f子项", DialogMarkdown.render("§6- §f第一项\n  §7- §f子项"));
    }

    @Test
    void codesBetweenTokenAndSpaceAreMoved() {
        assertEquals("§7• §f内容\n§e1. §f第一", DialogMarkdown.render("§7-§f 内容\n§e1.§f 第一"));
    }

    @Test
    void orderedListsKeepTheirNumbers() {
        assertEquals("1. 甲\n2. 乙", DialogMarkdown.render("1. 甲\n2) 乙"));
    }

    @Test
    void twoColumnTablesBecomeListsOfPairs() {
        assertEquals("§e• §e核心§7：§r§e§f8 铁锭\n§f• §f工具§7：§r§f§f2 铁锭",
            DialogMarkdown.render("| §f物品 | §f配方 |\n| --- | :-: |\n| §e核心 | §f8 铁锭 |\n| §f工具 | §f2 铁锭 |"));
    }

    @Test
    void widerTablesLabelEachValueWithItsHeader() {
        assertEquals("§f• §f/back 冷却§7：无月卡 §r§f§f7 天§7，黄金月卡 §r§f§e1 小时",
            DialogMarkdown.render("| §f权益 | §f无月卡 | §e黄金月卡 |\n| --- | --- | --- |\n| §f`/back` 冷却 | §f7 天 | §e1 小时 |"));
    }

    @Test
    void tableBulletsDoNotKeepTheHeadingColor() {
        // 表头的颜色与数据行相同时，转换后的数据行不再写颜色代码。
        assertEquals("§6§l通用§r§6\n\n§r§f• /menu§7：§r§f打开",
            DialogMarkdown.render("§6### 通用\n\n| §f命令 | 说明 |\n| --- | --- |\n| `/menu` | 打开 |"));
    }

    @Test
    void bulletsTakeTheFirstColorOfTheirLine() {
        assertEquals("§6§l标题§r§6\n§f• §f第一项", DialogMarkdown.render("§6## 标题\n- §f第一项"));
    }

    @Test
    void escapedPipesStayInTheirCell() {
        assertEquals("a|b §7|§r c", DialogMarkdown.render("| a\\|b | c |"));
    }

    @Test
    void quotesGetABarAndGreyText() {
        assertEquals("§7▎ §7无颜色§r", DialogMarkdown.render("> 无颜色"));
        assertEquals("§7▎ §e有颜色§r§e", DialogMarkdown.render("§e> 有颜色"));
    }

    @Test
    void boldRestoresTheStyleAndSurvivesColorCodes() {
        assertEquals("§f§l甲§e§l乙§r§e丙", DialogMarkdown.render("§f**甲§e乙**丙"));
    }

    @Test
    void strikethroughNeedsTwoTildesAndSingleTildeIsARange() {
        assertEquals("§m删§r 10 月 24 日~11 月 7 日", DialogMarkdown.render("~~删~~ 10 月 24 日~11 月 7 日"));
    }

    @Test
    void underscoresAreNotEmphasis() {
        assertEquals("player_name_1", DialogMarkdown.render("player_name_1"));
    }

    @Test
    void escapesAndCodeSpansAreShownAsIs() {
        assertEquals("a*b_c /ah sell <价格> **", DialogMarkdown.render("a\\*b\\_c `/ah sell <价格>` \\*\\*"));
    }

    @Test
    void fencedCodeIsNotParsed() {
        assertEquals("# 不是标题", DialogMarkdown.render("```\n# 不是标题\n```"));
    }

    @Test
    void linksShowTheirAddress() {
        assertEquals("文字§7（https://minez.cc）§r https://a.b/c_d", DialogMarkdown.render("[文字](https://minez.cc) <https://a.b/c_d>"));
    }

    @Test
    void blankLinesAndPlainTextAreKept() {
        assertEquals("§f第一行\n\n第二行", DialogMarkdown.render("§f第一行\n\n第二行"));
    }
}
