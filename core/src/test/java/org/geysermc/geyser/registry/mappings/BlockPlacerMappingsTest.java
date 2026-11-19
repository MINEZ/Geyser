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

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.scoreboard.network.util.GeyserMockContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * MINEZ：物品定义的 bedrock_options 中的 block_placer。以原版物品为底材的物品（例如 CE 的物品以下界砖块、纸为底材）不能经 API
 * 加 Geyser 的组件，2026-11-19 曾因此在读映射文件时抛出异常、Geyser 启动失败。
 */
class BlockPlacerMappingsTest {
    @TempDir
    Path dir;

    @Test
    void readsBlockPlacerOnVanillaBasedItems() throws Exception {
        // MINEZ 资源包的生成工具写出的物品映射（节选：哨戒炮以下界砖块为底材，另有一件不按方块绘制的物品作对照）。
        Path file = dir.resolve("minez_resources.json");
        Files.writeString(file, """
            {
              "format_version": 2,
              "items": {
                "minecraft:nether_brick": [
                  {
                    "type": "definition",
                    "model": "minez:sentry_gun",
                    "bedrock_identifier": "minez:sentry_gun.nether_brick",
                    "bedrock_options": {
                      "icon": "minez.sentry_gun",
                      "allow_offhand": true,
                      "block_placer": {
                        "block": "geyser_custom:minez_item_sentry_gun",
                        "use_block_icon": true
                      }
                    }
                  },
                  {
                    "type": "definition",
                    "model": "minez:electronic_part",
                    "bedrock_identifier": "minez:electronic_part.nether_brick",
                    "bedrock_options": {
                      "icon": "minez.electronic_part",
                      "allow_offhand": true
                    }
                  }
                ]
              }
            }
            """);
        List<CustomItemDefinition> read = new ArrayList<>();
        GeyserMockContext.mockContext(context -> {
            // 物品定义的构造器经 GeyserApi.api().provider() 取得，参数个数不定；模拟环境只放行了两个参数的调用，这里补上其余的
            GeyserImpl geyser = GeyserImpl.getInstance();
            doAnswer(InvocationOnMock::callRealMethod).when(geyser).provider(any(Class.class));
            doAnswer(InvocationOnMock::callRealMethod).when(geyser).provider(any(Class.class), any());
            doAnswer(InvocationOnMock::callRealMethod).when(geyser).provider(any(Class.class), any(), any(), any());
            // 组件常量（GeyserItemDataComponents）同样经 provider() 创建；为空时测不出“原版物品不能加 Geyser 组件”的检查
            assertNotNull(GeyserItemDataComponents.BLOCK_PLACER);
            MappingsConfigReader.readCustomMappings(MappingsType.ITEMS, file, (item, definition) -> read.add(definition));
        });

        assertEquals(2, read.size());
        CustomItemDefinition sentryGun = read.stream()
            .filter(definition -> definition.bedrockIdentifier().toString().equals("minez:sentry_gun.nether_brick"))
            .findFirst().orElseThrow();
        GeyserBlockPlacer placer = sentryGun.components().get(GeyserItemDataComponents.BLOCK_PLACER);
        assertNotNull(placer);
        assertEquals("geyser_custom:minez_item_sentry_gun", placer.block().toString());
        assertTrue(placer.useBlockIcon());

        CustomItemDefinition part = read.stream()
            .filter(definition -> definition.bedrockIdentifier().toString().equals("minez:electronic_part.nether_brick"))
            .findFirst().orElseThrow();
        assertNull(part.components().get(GeyserItemDataComponents.BLOCK_PLACER));
    }
}
