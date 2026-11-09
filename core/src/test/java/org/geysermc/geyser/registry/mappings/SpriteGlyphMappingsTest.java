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
                "minecraft:items": {"minez:item/flare": "\\uF100", "item/diamond": "\\uF101", "minez:item/title/a_1": ""},
                "gui": {"minez:icon/airdrop": "\\uF102"}
              }
            }
            """);
        Map<String, String> read = new HashMap<>();
        MappingsConfigReader.readCustomMappings(MappingsType.SPRITES, file, read::put);

        // 图集与精灵都按 Key 规范化，没有命名空间的补上 minecraft。
        assertEquals(Map.of(
            SpriteGlyphs.key(Key.key("minecraft:items"), Key.key("minez:item/flare")), "\uF100",
            SpriteGlyphs.key(Key.key("minecraft:items"), Key.key("minecraft:item/diamond")), "\uF101",
            SpriteGlyphs.key(Key.key("minecraft:gui"), Key.key("minez:icon/airdrop")), "\uF102",
            // 空字符串：多块拼成的图标中第一块以外的各块，由第一块的字一并画出。
            SpriteGlyphs.key(Key.key("minecraft:items"), Key.key("minez:item/title/a_1")), ""), read);
    }

    @Test
    void filesWithoutSpritesAreSkipped() throws Exception {
        Path file = dir.resolve("other.json");
        Files.writeString(file, "{\"format_version\": 2, \"items\": {}}");
        Map<String, String> read = new HashMap<>();
        MappingsConfigReader.readCustomMappings(MappingsType.SPRITES, file, read::put);
        assertEquals(Map.of(), read);
    }

    @Test
    void readsAnimatedSprites() throws Exception {
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "animated_sprites": {
                "\uF1F1\uF1F2": {"texture": "textures/minez/anim/item.title.builder", "frame_width": 32, "frame_height": 16,
                                   "frames": 8, "fps": 10.0, "tiles": 2},
                "\uF1F3": {"texture": "textures/x", "frames": 8},
                "\uF1F4": {"texture": 1, "frame_width": 16, "frame_height": 16, "frames": 8, "fps": 6.5, "tiles": 1}
              }
            }
            """);
        Map<String, Map<String, Object>> read = new HashMap<>();
        // 不完整或类型不对的项（\uF1F3、\uF1F4）记一条错误后跳过；记错误要用到 GeyserImpl。
        org.geysermc.geyser.scoreboard.network.util.GeyserMockContext.mockContext(() ->
            MappingsConfigReader.readCustomMappings(MappingsType.ANIMATED_SPRITES, file, read::put));

        // 整数写成 long，其余数字保持浮点，原样发给模组。
        assertEquals(Map.of("\uF1F1\uF1F2", Map.of("texture", "textures/minez/anim/item.title.builder", "frame_width", 32L,
            "frame_height", 16L, "frames", 8L, "fps", 10L, "tiles", 2L)), read);
    }

    @Test
    void readsModelIcons() throws Exception {
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "model_icons": {
                "minez:dark_star_wings": {"entity": "minez:icon_dark_star_wings", "scale": 1.0, "rot_y": 180},
                "minez:other": {"entity": "minez:icon_other"},
                "minez:bad": {"scale": 1.0},
                "minez:bad_number": {"entity": "minez:icon_x", "scale": "big"}
              }
            }
            """);
        Map<String, Map<String, Object>> read = new HashMap<>();
        // 缺少 entity 或参数不是数字的项记一条错误后跳过；记错误要用到 GeyserImpl。
        org.geysermc.geyser.scoreboard.network.util.GeyserMockContext.mockContext(() ->
            MappingsConfigReader.readCustomMappings(MappingsType.MODEL_ICONS, file, read::put));

        // 参数一律写成浮点数，省略的不补。
        assertEquals(Map.of(
            "minez:dark_star_wings", Map.of("entity", "minez:icon_dark_star_wings", "scale", 1.0, "rot_y", 180.0),
            "minez:other", Map.of("entity", "minez:icon_other")), read);
    }
}
