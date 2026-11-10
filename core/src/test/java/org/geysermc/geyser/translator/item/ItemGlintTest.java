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

package org.geysermc.geyser.translator.item;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：光效的空 ench 列表在基岩版中显示为名称下的一个空行，见 {@link ItemTranslator#addGlint}。
 */
public class ItemGlintTest {

    @Test
    public void itemsWithoutLoreGetNoBlankLine() {
        BedrockItemBuilder builder = new BedrockItemBuilder();
        ItemTranslator.addGlint(builder);
        assertNull(builder.build());
    }

    @Test
    public void theListReplacesALeadingBlankLine() {
        BedrockItemBuilder builder = new BedrockItemBuilder();
        builder.getOrCreateLore().addAll(List.of("§r§f", "§r§7稀有 · 万圣节系列"));
        ItemTranslator.addGlint(builder);

        NbtMap nbt = builder.build();
        assertTrue(nbt.containsKey("ench"));
        assertEquals(List.of("§r§7稀有 · 万圣节系列"), nbt.getCompound("display").getList("Lore", NbtType.STRING));
    }

    @Test
    public void loreWithoutABlankLineIsKept() {
        BedrockItemBuilder builder = new BedrockItemBuilder();
        builder.getOrCreateLore().add("§r§7稀有 · 万圣节系列");
        ItemTranslator.addGlint(builder);

        NbtMap nbt = builder.build();
        assertTrue(nbt.containsKey("ench"));
        assertEquals(1, nbt.getCompound("display").getList("Lore", NbtType.STRING).size());
    }

    @Test
    public void enchantedItemsAreLeftAlone() {
        BedrockItemBuilder builder = new BedrockItemBuilder();
        builder.getOrCreateLore().addAll(List.of("", "§7描述"));
        builder.putList("ench", NbtType.COMPOUND, List.of(NbtMap.builder().putShort("id", (short) 9).build()));
        ItemTranslator.addGlint(builder);

        NbtMap nbt = builder.build();
        assertEquals(2, nbt.getCompound("display").getList("Lore", NbtType.STRING).size());
        assertFalse(nbt.getList("ench", NbtType.COMPOUND).isEmpty());
    }
}
