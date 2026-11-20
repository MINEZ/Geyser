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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void readsItemOnlyBlocks() throws Exception {
        // MINEZ：方块物品的显示用方块（item_only），不对应任何 Java 版方块状态，键为任意标识。
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "blocks": {
                "minez:item/sentry_gun": {
                  "name": "minez_item_sentry_gun",
                  "item_only": true,
                  "geometry": "minecraft:geometry.full_block",
                  "material_instances": {
                    "*": {
                      "texture": "minez_block_sentry_gun_ready",
                      "render_method": "opaque",
                      "ambient_occlusion": true,
                      "face_dimming": true
                    }
                  }
                }
              }
            }
            """);
        Map<String, CustomBlockMapping> read = new HashMap<>();
        BlockRegistries.COLLISIONS.set(new ArrayList<>());
        BlockRegistries.SHAPES.set(new ArrayList<>());
        try {
            org.geysermc.geyser.scoreboard.network.util.GeyserMockContext.mockContext(() ->
                MappingsConfigReader.readCustomMappings(MappingsType.BLOCKS, file, read::put));
        } finally {
            BlockRegistries.COLLISIONS.set(null);
            BlockRegistries.SHAPES.set(null);
        }

        CustomBlockMapping mapping = read.get("minez:item/sentry_gun");
        assertEquals("geyser_custom:minez_item_sentry_gun", mapping.data().identifier());
        assertTrue(mapping.states().isEmpty());
        assertTrue(mapping.data().permutations().isEmpty());
        assertEquals("minecraft:geometry.full_block", mapping.data().components().geometry().identifier());
        assertEquals("minez_block_sentry_gun_ready", mapping.data().components().materialInstances().get("*").texture());
        assertFalse(mapping.overrideItem());
    }

    @Test
    void readsVanillaBlocks() throws Exception {
        // MINEZ 资源包的生成工具写出的原版新方块（节选）：覆盖这个 Java 版方块的全部状态，同名的 Java 版物品换成它的方块物品；
        // 原木类按 axis 转动。
        BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.register("minecraft:sulfur", 9100);
        BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.register("minecraft:poplar_log[axis=x]", 9101);
        BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.register("minecraft:poplar_log[axis=y]", 9102);
        BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.register("minecraft:poplar_log[axis=z]", 9103);
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "blocks": {
                "minecraft:sulfur": {
                  "name": "minez_sulfur",
                  "only_override_states": false,
                  "geometry": "minecraft:geometry.full_block",
                  "material_instances": {
                    "*": {
                      "texture": "sulfur",
                      "render_method": "opaque",
                      "ambient_occlusion": true,
                      "face_dimming": true
                    }
                  },
                  "destructible_by_mining": 1.5
                },
                "minecraft:poplar_log": {
                  "name": "minez_poplar_log",
                  "only_override_states": false,
                  "geometry": "minecraft:geometry.full_block",
                  "material_instances": {
                    "down": {
                      "texture": "poplar_log_top",
                      "render_method": "opaque",
                      "ambient_occlusion": true,
                      "face_dimming": true
                    },
                    "up": {
                      "texture": "poplar_log_top",
                      "render_method": "opaque",
                      "ambient_occlusion": true,
                      "face_dimming": true
                    },
                    "*": {
                      "texture": "poplar_log_side",
                      "render_method": "opaque",
                      "ambient_occlusion": true,
                      "face_dimming": true
                    }
                  },
                  "destructible_by_mining": 2.0,
                  "state_overrides": {
                    "axis=x": {
                      "geometry": "minecraft:geometry.full_block",
                      "material_instances": {
                        "down": {
                          "texture": "poplar_log_top",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        },
                        "up": {
                          "texture": "poplar_log_top",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        },
                        "*": {
                          "texture": "poplar_log_side",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        }
                      },
                      "destructible_by_mining": 2.0,
                      "transformation": {
                        "rotation": [
                          0,
                          0,
                          90
                        ]
                      }
                    },
                    "axis=z": {
                      "geometry": "minecraft:geometry.full_block",
                      "material_instances": {
                        "down": {
                          "texture": "poplar_log_top",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        },
                        "up": {
                          "texture": "poplar_log_top",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        },
                        "*": {
                          "texture": "poplar_log_side",
                          "render_method": "opaque",
                          "ambient_occlusion": true,
                          "face_dimming": true
                        }
                      },
                      "destructible_by_mining": 2.0,
                      "transformation": {
                        "rotation": [
                          90,
                          0,
                          0
                        ]
                      }
                    }
                  }
                }
              }
            }
            """);
        Map<String, CustomBlockMapping> read = new HashMap<>();
        BlockRegistries.COLLISIONS.set(new ArrayList<>());
        BlockRegistries.SHAPES.set(new ArrayList<>());
        try {
            org.geysermc.geyser.scoreboard.network.util.GeyserMockContext.mockContext(() ->
                MappingsConfigReader.readCustomMappings(MappingsType.BLOCKS, file, read::put));
        } finally {
            BlockRegistries.COLLISIONS.set(null);
            BlockRegistries.SHAPES.set(null);
        }

        CustomBlockMapping sulfur = read.get("minecraft:sulfur");
        assertEquals("geyser_custom:minez_sulfur", sulfur.data().identifier());
        assertEquals(1, sulfur.states().size());
        assertTrue(sulfur.overrideItem());
        assertEquals("sulfur", sulfur.data().components().materialInstances().get("*").texture());

        CustomBlockMapping log = read.get("minecraft:poplar_log");
        assertEquals(3, log.states().size());
        assertTrue(log.overrideItem());
        int rotated = 0;
        for (var permutation : log.data().permutations()) {
            var transformation = permutation.components().transformation();
            if (permutation.condition().contains("'axis') == 'y'")) {
                assertEquals(null, transformation);
            } else {
                assertEquals(90, Math.abs(transformation.rx()) + Math.abs(transformation.rz()));
                rotated++;
            }
        }
        assertEquals(2, rotated);
    }
}
