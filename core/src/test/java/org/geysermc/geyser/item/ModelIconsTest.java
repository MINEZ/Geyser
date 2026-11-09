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

package org.geysermc.geyser.item;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * MINEZ：3D 物品图标的实体加进基岩版的实体列表，重载时不重复添加。
 */
class ModelIconsTest {

    @Test
    void registersIconEntitiesOnce() {
        NbtMap original = NbtMap.builder().putList("idlist", NbtType.COMPOUND, List.of(
            NbtMap.builder().putString("id", "minecraft:cow").putInt("rid", 1).build())).build();
        Map<String, Map<String, Object>> table = Map.of("minez:dark_star_wings", Map.of("entity", "minez:icon_dark_star_wings"));
        NbtMap once = ModelIcons.withEntities(original, table);
        // 重载时再读一次，已在列表中的不重复添加。
        assertSame(once, ModelIcons.withEntities(once, table));
        List<NbtMap> list = once.getList("idlist", NbtType.COMPOUND);
        assertEquals(List.of("minecraft:cow", "minez:icon_dark_star_wings"), list.stream().map(e -> e.getString("id")).toList());
        // 只用于显示：不可召唤，没有刷怪蛋。
        assertFalse(list.get(1).getBoolean("summonable"));
        assertFalse(list.get(1).getBoolean("hasSpawnEgg"));
    }
}
