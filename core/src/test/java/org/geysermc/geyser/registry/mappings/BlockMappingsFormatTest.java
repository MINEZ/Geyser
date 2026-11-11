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

import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.mappings.util.CustomBlockMapping;
import org.geysermc.geyser.registry.mappings.versions.block.BlockMappingsReader_v1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * MINEZ：格式 2 的映射文件中的方块按格式 1 的写法读（MINEZ 资源包的 minez_resources.json 把物品与方块写在同一个文件中）。
 */
class BlockMappingsFormatTest {
    @Test
    void readsBlocksInBothFormats() {
        assertInstanceOf(BlockMappingsReader_v1.class, MappingsType.BLOCKS.readers().get(1));
        assertInstanceOf(BlockMappingsReader_v1.class, MappingsType.BLOCKS.readers().get(2));
    }

    @TempDir
    Path dir;

    @Test
    void readsGeneratedBlocks() throws Exception {
        // MINEZ 资源包的生成工具写出的 blocks（节选：基地核心与哨戒炮各借用的一个棕色蘑菇方块状态）。
        BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.register("minecraft:brown_mushroom_block[down=true,east=true,north=true,south=false,up=false,west=true]", 9000);
        BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.register("minecraft:brown_mushroom_block[down=true,east=true,north=true,south=false,up=true,west=false]", 9001);
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "blocks": {
                "minecraft:brown_mushroom_block": {
                  "name": "minez_brown_mushroom_block",
                  "only_override_states": true,
                  "state_overrides": {
                    "down=true,east=true,north=true,south=false,up=false,west=true": {
                      "geometry": "minecraft:geometry.full_block",
                      "material_instances": {
                        "*": {
                          "texture": "minez_block_base_core_intact_side_on",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        },
                        "up": {
                          "texture": "minez_block_base_core_intact_top_on",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        },
                        "down": {
                          "texture": "minez_block_base_core_intact_bottom_on",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        }
                      },
                      "light_emission": 11,
                      "destructible_by_mining": 5.0
                    },
                    "down=true,east=true,north=true,south=false,up=true,west=false": {
                      "geometry": "minecraft:geometry.full_block",
                      "material_instances": {
                        "*": {
                          "texture": "minez_block_sentry_gun_targeting",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        }
                      },
                      "light_emission": 11,
                      "destructible_by_mining": 5.0
                    }
                  }
                }
              }
            }
            """);
        Map<String, CustomBlockMapping> read = new HashMap<>();
        // 碰撞箱与选择框按 Java 版的方块状态取，测试中没有载入，一律当作没有（读出空的）。
        BlockRegistries.COLLISIONS.set(new ArrayList<>());
        BlockRegistries.SHAPES.set(new ArrayList<>());
        try {
            // 读错的项只记一条错误后跳过；记错误要用到 GeyserImpl。
            org.geysermc.geyser.scoreboard.network.util.GeyserMockContext.mockContext(() ->
                MappingsConfigReader.readCustomMappings(MappingsType.BLOCKS, file, read::put));
        } finally {
            BlockRegistries.COLLISIONS.set(null);
            BlockRegistries.SHAPES.set(null);
        }

        CustomBlockMapping mapping = read.get("minecraft:brown_mushroom_block");
        assertEquals("geyser_custom:minez_brown_mushroom_block", mapping.data().identifier());
        assertEquals(2, mapping.states().size());
        assertEquals(2, mapping.data().permutations().size());
        assertFalse(mapping.overrideItem());
    }
}
