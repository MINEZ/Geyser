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

package org.geysermc.geyser.util;

import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.scoreboard.network.util.GeyserMockContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * MINEZ：CraftEngine 换成 replaced.&lt;原名&gt; 的原版方块声音，在基岩版中按原名播放。
 */
class CraftEngineReplacedSoundTest {
    @Test
    void replacedSoundsUseTheVanillaMapping() {
        GeyserMockContext.mockContext(context -> {
            // 声音映射（mappings/sounds.json）经启动器从类路径读取
            GeyserBootstrap bootstrap = context.mock(GeyserBootstrap.class);
            when(bootstrap.getResourceOrThrow(anyString())).thenAnswer(invocation ->
                getClass().getClassLoader().getResourceAsStream(invocation.getArgument(0, String.class)));
            when(GeyserImpl.getInstance().getBootstrap()).thenReturn(bootstrap);
            Registries.SOUNDS.load();
            // 前提：原版的木头声音有映射，CE 换出的名字没有
            assertNotNull(Registries.SOUNDS.get("block.wood.place"));
            assertNull(Registries.SOUNDS.get("replaced.block.wood.place"));

            assertEquals("block.wood.place", SoundUtils.withoutCraftEngineReplacement("replaced.block.wood.place"));
            assertEquals("block.wood.break", SoundUtils.withoutCraftEngineReplacement("replaced.block.wood.break"));
            assertEquals("block.wood.hit", SoundUtils.withoutCraftEngineReplacement("replaced.block.wood.hit"));
            // 其他名字原样不动
            assertEquals("block.stone.place", SoundUtils.withoutCraftEngineReplacement("block.stone.place"));
            assertEquals("minez.sentry_gun.fire", SoundUtils.withoutCraftEngineReplacement("minez.sentry_gun.fire"));
        });
    }
}
