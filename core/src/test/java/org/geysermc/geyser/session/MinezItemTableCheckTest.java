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

package org.geysermc.geyser.session;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：网易客户端的物品表与下发的物品表逐个编号比对。
 */
class MinezItemTableCheckTest {

    @Test
    void listsEveryDifferentId() {
        Map<Integer, String> server = Map.of(1, "minecraft:stone", 843, "minez:a.paper", 1276, "minez:basin.nether_brick",
            1277, "minez:wallet.nether_brick");
        Int2ObjectMap<String> client = new Int2ObjectOpenHashMap<>();
        client.put(1, "minecraft:stone");
        client.put(843, "minez:a.paper");
        // 撞号：客户端认作网易自己的物品；客户端缺了下发的物品；客户端有而下发的表中没有。
        client.put(1276, "minecraft:debug_stick");
        client.put(2000, "minecraft:mod");
        assertEquals(List.of(
            "1276: minecraft:debug_stick / minez:basin.nether_brick",
            "1277: - / minez:wallet.nether_brick",
            "2000: minecraft:mod / -"), MinezItemTableCheck.differences(client, server::get));
    }
}
