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

package org.geysermc.geyser.registry.populator;

import com.google.gson.reflect.TypeToken;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.geyser.registry.type.PaletteItem;
import org.geysermc.geyser.util.JsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：由国际版 1.21.130 生成的网易调色板与物品表。
 * <p>
 * 期望的编号（例如工作台 10499、micro_block 3211）与资料中网易调色板的编号一致，用来交叉核对生成的结果。
 */
class NeteasePaletteTest {
    private static List<NbtMap> vanilla;
    private static List<NbtMap> netease;
    private static Map<NbtMap, Integer> vanillaIds;
    private static Map<NbtMap, Integer> neteaseIds;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream stream = NeteasePaletteTest.class.getClassLoader().getResourceAsStream("bedrock/block_palette.1_21_130.nbt");
             NBTInputStream nbt = new NBTInputStream(new DataInputStream(new GZIPInputStream(stream)), true, true)) {
            vanilla = new ArrayList<>();
            // 与 BlockRegistryPopulator 一样，只留下 name 与 states。
            for (NbtMap state : ((NbtMap) nbt.readTag()).getList("blocks", NbtType.COMPOUND)) {
                vanilla.add(NbtMap.builder()
                    .putString("name", state.getString("name"))
                    .putCompound("states", state.getCompound("states"))
                    .build());
            }
        }
        netease = NeteasePalette.blockStates(vanilla);
        vanillaIds = ids(vanilla);
        neteaseIds = ids(netease);
    }

    private static Map<NbtMap, Integer> ids(List<NbtMap> palette) {
        Map<NbtMap, Integer> ids = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) {
            assertNull(ids.put(palette.get(i), i), "duplicate state " + palette.get(i));
        }
        return ids;
    }

    private static NbtMap block(String name, Object... states) {
        NbtMapBuilder builder = NbtMap.builder();
        for (int i = 0; i < states.length; i += 2) {
            builder.put((String) states[i], states[i + 1]);
        }
        return NbtMap.builder().putString("name", name).putCompound("states", builder.build()).build();
    }

    /**
     * Java 版方块转换到网易调色板后的编号。
     */
    private static Integer neteaseId(NbtMap javaSideTag) {
        return neteaseIds.get(NeteasePalette.remapper(tag -> tag).remapBlock(javaSideTag));
    }

    @Test
    void chalkboardIsRemovedAndMicroBlockAdded() {
        assertEquals(15845, vanilla.size());
        assertEquals(15845 - 16 + 1, netease.size());
        assertTrue(netease.stream().noneMatch(state -> state.getString("name").equals("minecraft:chalkboard")));
        assertEquals(16, vanilla.stream().filter(state -> state.getString("name").equals("minecraft:chalkboard")).count());
        assertEquals(3211, neteaseIds.get(block("minecraft:micro_block")));
        assertNull(vanillaIds.get(block("minecraft:micro_block")));
    }

    @Test
    void ordinaryBlocksKeepTheirOrder() {
        // 在 micro_block 之前的不变，在它与黑板之间的后移 1 位，在黑板之后的前移 15 位。
        assertEquals(2532, vanillaIds.get(block("minecraft:stone")));
        assertEquals(2532, neteaseId(block("minecraft:stone")));
        assertEquals(12530, vanillaIds.get(block("minecraft:air")));
        assertEquals(12515, neteaseId(block("minecraft:air")));
        assertEquals(9852, vanillaIds.get(block("minecraft:dirt")));
        assertEquals(9837, neteaseId(block("minecraft:dirt")));
        assertEquals(9268, vanillaIds.get(block("minecraft:water", "liquid_depth", 0)));
        assertEquals(9253, neteaseId(block("minecraft:water", "liquid_depth", 0)));

        // 逐个核对：去掉 micro_block、黑板与 custom_appearance 之后，两份调色板逐项相同。
        List<NbtMap> expected = vanilla.stream().filter(state -> !state.getString("name").equals("minecraft:chalkboard")).toList();
        List<NbtMap> actual = netease.stream()
            .filter(state -> !state.getString("name").equals("minecraft:micro_block"))
            .map(NeteasePaletteTest::withoutCustomAppearance)
            .toList();
        assertEquals(expected, actual);
        int microBlock = neteaseIds.get(block("minecraft:micro_block"));
        int firstChalkboard = 0;
        while (!vanilla.get(firstChalkboard).getString("name").equals("minecraft:chalkboard")) {
            firstChalkboard++;
        }
        assertEquals(5196, firstChalkboard);
        for (int i = 0; i < vanilla.size(); i++) {
            NbtMap state = vanilla.get(i);
            if (state.getString("name").equals("minecraft:chalkboard")) {
                continue;
            }
            int shift = i < microBlock ? 0 : i < firstChalkboard ? 1 : -15;
            assertEquals(i + shift, neteaseId(state), state.toString());
        }
    }

    private static NbtMap withoutCustomAppearance(NbtMap state) {
        NbtMap states = state.getCompound("states");
        if (!states.containsKey("custom_appearance")) {
            return state;
        }
        NbtMapBuilder builder = states.toBuilder();
        builder.remove("custom_appearance");
        return state.toBuilder().putCompound("states", builder.build()).build();
    }

    @Test
    void craftingTableAndFurnacesGetCustomAppearance() {
        assertEquals(10514, vanillaIds.get(block("minecraft:crafting_table")));
        assertEquals(10499, neteaseId(block("minecraft:crafting_table")));
        assertEquals(10499, neteaseIds.get(block("minecraft:crafting_table", "custom_appearance", (byte) 0)));
        // 网易调色板里只有带 custom_appearance 的工作台，转换时总会补上。
        assertNull(neteaseIds.get(block("minecraft:crafting_table")));

        assertEquals(14786, vanillaIds.get(block("minecraft:furnace", "minecraft:cardinal_direction", "north")));
        assertEquals(14771, neteaseId(block("minecraft:furnace", "minecraft:cardinal_direction", "north")));
        assertEquals(13860, vanillaIds.get(block("minecraft:lit_furnace", "minecraft:cardinal_direction", "north")));
        assertEquals(13845, neteaseId(block("minecraft:lit_furnace", "minecraft:cardinal_direction", "north")));

        long withCustomAppearance = netease.stream().filter(state -> state.getCompound("states").containsKey("custom_appearance")).count();
        assertEquals(9, withCustomAppearance);
        // 其他方块不受影响，例如高炉。
        NbtMap blastFurnace = block("minecraft:blast_furnace", "minecraft:cardinal_direction", "north");
        assertSame(blastFurnace, NeteasePalette.remapBlock(blastFurnace));
        assertNotNull(neteaseIds.get(blastFurnace));
    }

    @Test
    void unsortedPaletteIsRejected() {
        List<NbtMap> reversed = new ArrayList<>(vanilla.subList(0, 100));
        Collections.reverse(reversed);
        assertThrows(IllegalStateException.class, () -> NeteasePalette.blockStates(reversed));
        assertThrows(IllegalStateException.class, () -> NeteasePalette.blockStates(netease));
    }

    @Test
    void boardAndChalkboardItemsAreRemoved() throws Exception {
        List<PaletteItem> items;
        try (InputStream stream = NeteasePaletteTest.class.getClassLoader().getResourceAsStream("bedrock/runtime_item_states.1_21_130.json")) {
            items = JsonUtils.fromJson(stream, new TypeToken<List<PaletteItem>>() { }.getType());
        }
        List<PaletteItem> neteaseItems = NeteasePalette.items(items);
        assertEquals(items.size() - 2, neteaseItems.size());
        assertFalse(neteaseItems.stream().anyMatch(item -> item.getName().equals("minecraft:board") || item.getName().equals("minecraft:chalkboard")));
        Map<String, Integer> neteaseItemIds = new HashMap<>();
        neteaseItems.forEach(item -> neteaseItemIds.put(item.getName(), item.getId()));
        for (PaletteItem item : items) {
            if (!NeteasePalette.REMOVED_ITEMS.contains(item.getName())) {
                assertEquals(item.getId(), neteaseItemIds.get(item.getName()), item.getName());
            }
        }
    }

    @Test
    void skipsIdsOfNeteaseItems() {
        // 网易客户端自己的物品占用的编号跳过，其余照常顺延。
        assertEquals(1275, NeteasePalette.nextFreeItemId(1275));
        assertEquals(1277, NeteasePalette.nextFreeItemId(1276));
        assertEquals(1282, NeteasePalette.nextFreeItemId(1280));
        assertEquals(1282, NeteasePalette.nextFreeItemId(1281));
    }
}
