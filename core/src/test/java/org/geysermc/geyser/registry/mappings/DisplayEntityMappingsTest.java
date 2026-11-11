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
import org.geysermc.geyser.entity.DisplayModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：映射文件中的 display_entities（物品展示实体在基岩版中显示成的自定义实体）。
 */
class DisplayEntityMappingsTest {
    @TempDir
    Path dir;

    @Test
    void readsDisplayEntities() throws Exception {
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "display_entities": {
                "minez:airdrop_parachute|none": "minez:display_airdrop_parachute_none",
                "minecraft:chest|none": "minez:display_minecraft_chest_none",
                "minez:dark_star_wings|head": "minez:display_dark_star_wings_head",
                "minez:bad_context|sideways": "minez:display_bad",
                "minez:no_context": "minez:display_bad",
                "minez:vanilla_entity|none": "minecraft:pig",
                "minez:not_a_string|none": 1
              }
            }
            """);
        Map<String, String> read = new HashMap<>();
        // 格式不对的项记一条错误后跳过；记错误要用到 GeyserImpl。
        org.geysermc.geyser.scoreboard.network.util.GeyserMockContext.mockContext(() ->
            MappingsConfigReader.readCustomMappings(MappingsType.DISPLAY_ENTITIES, file, read::put));

        assertEquals(Map.of(
            DisplayModels.key(Key.key("minez:airdrop_parachute"), "none"), "minez:display_airdrop_parachute_none",
            DisplayModels.key(Key.key("minecraft:chest"), "none"), "minez:display_minecraft_chest_none",
            DisplayModels.key(Key.key("minez:dark_star_wings"), "head"), "minez:display_dark_star_wings_head"), read);
    }

    @Test
    void contextsFollowJavaOrder() {
        // 物品展示实体的 display 以 Java 版 ItemDisplayContext 的序号同步：none 为 0，head 为 5，fixed 为 8。
        assertEquals("none", DisplayModels.CONTEXTS.get(0));
        assertEquals("head", DisplayModels.CONTEXTS.get(5));
        assertEquals("fixed", DisplayModels.CONTEXTS.get(8));
    }
}
