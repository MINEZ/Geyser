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

package org.geysermc.geyser.registry.mappings;

import net.kyori.adventure.key.Key;
import org.geysermc.geyser.text.SpriteGlyphs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：映射文件中的 sprites（文字图标对应的字符），与物品映射写在同一个文件中。
 */
class SpriteGlyphMappingsTest {
    @TempDir
    Path dir;

    @Test
    void readsSpritesNextToItems() throws Exception {
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "items": {},
              "sprites": {
                "minecraft:items": {"minez:item/flare": "\\uF100", "item/diamond": "\\uF101"},
                "gui": {"minez:icon/airdrop": "\\uF102"}
              }
            }
            """);
        Map<String, String> read = new HashMap<>();
        MappingsConfigReader.readCustomMappings(MappingsType.SPRITES, file, read::put);

        // 图集与精灵都按 Key 规范化，没有命名空间的补上 minecraft。
        assertEquals(Map.of(
            SpriteGlyphs.key(Key.key("minecraft:items"), Key.key("minez:item/flare")), "",
            SpriteGlyphs.key(Key.key("minecraft:items"), Key.key("minecraft:item/diamond")), "",
            SpriteGlyphs.key(Key.key("minecraft:gui"), Key.key("minez:icon/airdrop")), ""), read);
    }

    @Test
    void filesWithoutSpritesAreSkipped() throws Exception {
        Path file = dir.resolve("other.json");
        Files.writeString(file, "{\"format_version\": 2, \"items\": {}}");
        Map<String, String> read = new HashMap<>();
        MappingsConfigReader.readCustomMappings(MappingsType.SPRITES, file, read::put);
        assertEquals(Map.of(), read);
    }
}
