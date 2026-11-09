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
    void reportsOnlyRealProblems() {
        Int2ObjectMap<String> server = new Int2ObjectOpenHashMap<>(Map.of(1, "minecraft:stone", 793, "minecraft:wool",
            100, "minecraft:white_wool", 1277, "minez:a.paper", 1278, "minez:b.paper", 1300, "minez:c.paper"));
        Int2ObjectMap<String> netease = new Int2ObjectOpenHashMap<>(Map.of(1276, "minecraft:debug_stick", 1290, "minecraft:mod"));
        Int2ObjectMap<String> client = new Int2ObjectOpenHashMap<>();
        client.put(1, "minecraft:stone");
        client.put(100, "minecraft:white_wool");
        // 旧物品的别名：读出的名字也在下发的表中，不算问题。
        client.put(793, "minecraft:white_wool");
        // 记下的网易物品，不算问题。
        client.put(1276, "minecraft:debug_stick");
        client.put(1277, "minez:a.paper");
        // 撞号：下发的物品被网易的物品占了编号。
        client.put(1278, "minecraft:mod_ex");
        // 没有记下的网易物品。
        client.put(2000, "minecraft:mod_new");
        assertEquals(List.of(
            "1278: minecraft:mod_ex / minez:b.paper (conflict)",
            "1290: - / - (China Edition item not found)",
            "1300: - / minez:c.paper (missing on client)",
            "2000: minecraft:mod_new / - (unlisted China Edition item)"), MinezItemTableCheck.differences(client, server, netease));
    }
}
