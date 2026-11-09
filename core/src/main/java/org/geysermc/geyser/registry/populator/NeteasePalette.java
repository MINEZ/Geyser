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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.registry.populator.conversion.BlockRemapper;
import org.geysermc.geyser.registry.type.PaletteItem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MINEZ：网易中国版 1.21.130（协议 898）的方块调色板与物品表。
 * <p>
 * 网易客户端与国际版一样按调色板的顺序给方块编号（服务端不在 StartGame 中启用方块哈希），而网易的调色板与国际版 1.21.130
 * 有三处不同，照搬国际版的会让大部分方块错位。这里由国际版 1.21.130 的数据按这三处差异生成网易的调色板，不另带数据文件：
 * <ol>
 *     <li>没有 {@code minecraft:chalkboard}（教育版的黑板，16 个状态）；</li>
 *     <li>多一个没有状态的 {@code minecraft:micro_block}，按名字的散列排在相应的位置（第 3211 位）；</li>
 *     <li>{@code minecraft:crafting_table}、{@code minecraft:furnace}、{@code minecraft:lit_furnace} 多一个
 *     字节型的 {@code custom_appearance} 状态，值总为 0。只有一个取值，不改变状态的个数与顺序，因此不影响编号，
 *     只影响以状态写出方块的地方（例如被活塞推动的方块）。</li>
 * </ol>
 * 物品表少教育版的 {@code minecraft:board} 与 {@code minecraft:chalkboard}，其余与国际版相同。物品的编号由服务端在进服时下发，
 * 资料中网易与国际版互换的几个编号因此不需要处理。
 * <p>
 * 网易客户端另有自己的物品（例如 {@code minecraft:debug_stick}、{@code minecraft:mod}），客户端自带，不在服务端下发的物品表中，
 * 也不能下发（同一个物品会注册两次）。它们的编号与名字写在 {@code bedrock/netease_items.<版本>.json} 中，自定义物品分配编号时
 * 跳过这些编号，否则会显示成网易的物品或透明。这个文件由客户端实际的物品表得出：MINEZ 客户端模组每个会话读出客户端在各编号上的
 * 物品，Geyser 比对后写进日志与 {@code netease_item_table.json}（见 {@code MinezItemTableCheck}）；其中“客户端有、Geyser 没有”
 * 的物品即网易自己的物品。网易更新客户端、日志中出现新的网易物品时，把它们补进这个文件。
 */
public final class NeteasePalette {
    static final String CHALKBOARD = "minecraft:chalkboard";
    static final String MICRO_BLOCK = "minecraft:micro_block";
    static final String CUSTOM_APPEARANCE = "custom_appearance";
    static final Set<String> CUSTOM_APPEARANCE_BLOCKS = Set.of("minecraft:crafting_table", "minecraft:furnace", "minecraft:lit_furnace");
    static final Set<String> REMOVED_ITEMS = Set.of("minecraft:board", "minecraft:chalkboard");
    /**
     * 网易客户端自己的物品的数据文件，{@code %s} 为调色板的版本（例如 {@code 1_21_130}）。
     */
    static final String NETEASE_ITEMS_FILE = "bedrock/netease_items.%s.json";
    /**
     * 网易客户端自己的物品：编号 → 名字，由 {@link #NETEASE_ITEMS_FILE} 读入。
     */
    private static volatile Int2ObjectMap<String> neteaseItems = Int2ObjectMaps.emptyMap();

    private NeteasePalette() {
    }

    /**
     * 由国际版 1.21.130 的调色板生成网易的调色板。
     *
     * @param vanillaStates 国际版的调色板，每项只有 {@code name} 与 {@code states}，按方块名的 FNV-1 64 位散列排序。
     * @return 网易的调色板，顺序即编号。
     */
    static List<NbtMap> blockStates(List<NbtMap> vanillaStates) {
        List<NbtMap> states = new ArrayList<>(vanillaStates.size());
        long previousHash = 0;
        for (NbtMap state : vanillaStates) {
            String name = state.getString("name");
            long hash = BlockRegistryPopulator.fnv164(name);
            if (Long.compareUnsigned(hash, previousHash) < 0) {
                // 插入 micro_block 的位置依赖这个顺序，顺序不对时宁可启动失败，也不要让方块错位。
                throw new IllegalStateException("The block palette is not sorted by the hashes of the block names at " + name);
            }
            previousHash = hash;
            if (name.equals(MICRO_BLOCK)) {
                throw new IllegalStateException("The block palette already has " + MICRO_BLOCK);
            }
            if (!name.equals(CHALKBOARD)) {
                states.add(remapBlock(state));
            }
        }

        long microBlockHash = BlockRegistryPopulator.fnv164(MICRO_BLOCK);
        int index = 0;
        while (index < states.size() && Long.compareUnsigned(BlockRegistryPopulator.fnv164(states.get(index).getString("name")), microBlockHash) < 0) {
            index++;
        }
        states.add(index, NbtMap.builder()
            .putString("name", MICRO_BLOCK)
            .putCompound("states", NbtMap.EMPTY)
            .build());
        return states;
    }

    /**
     * 为网易多出 {@code custom_appearance} 状态的方块补上这一状态，其他方块原样返回。
     */
    static NbtMap remapBlock(NbtMap tag) {
        if (!CUSTOM_APPEARANCE_BLOCKS.contains(tag.getString("name"))) {
            return tag;
        }
        NbtMap states = tag.getCompound("states");
        if (states.containsKey(CUSTOM_APPEARANCE)) {
            return tag;
        }
        return tag.toBuilder()
            .putCompound("states", states.toBuilder().putByte(CUSTOM_APPEARANCE, (byte) 0).build())
            .build();
    }

    /**
     * 先按国际版 1.21.130 的方式转换 Java 版的方块，再按网易的调色板补上状态。
     */
    static BlockRemapper remapper(BlockRemapper vanilla) {
        return tag -> remapBlock(vanilla.remapBlock(tag));
    }

    /**
     * 读入网易客户端自己的物品：{@code {"items": [{"name": "minecraft:debug_stick", "id": 1276}, ...]}}。
     */
    static Int2ObjectMap<String> readNeteaseItems(InputStream stream) {
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        Int2ObjectMap<String> items = new Int2ObjectOpenHashMap<>();
        for (JsonElement element : json.getAsJsonArray("items")) {
            JsonObject item = element.getAsJsonObject();
            items.put(item.get("id").getAsInt(), item.get("name").getAsString());
        }
        return Int2ObjectMaps.unmodifiable(items);
    }

    static void setNeteaseItems(Int2ObjectMap<String> items) {
        neteaseItems = items;
    }

    /**
     * @return 网易客户端自己的物品：编号 → 名字。
     */
    public static Int2ObjectMap<String> neteaseItems() {
        return neteaseItems;
    }

    /**
     * @return 从 id 起第一个没有被网易客户端自己的物品占用的编号。
     */
    static int nextFreeItemId(int id) {
        while (neteaseItems.containsKey(id)) {
            id++;
        }
        return id;
    }

    /**
     * 由国际版 1.21.130 的物品表生成网易的物品表：去掉网易客户端没有的物品，其余的编号不变。
     */
    static List<PaletteItem> items(List<PaletteItem> vanillaItems) {
        List<PaletteItem> items = new ArrayList<>(vanillaItems.size());
        for (PaletteItem item : vanillaItems) {
            if (!REMOVED_ITEMS.contains(item.getName())) {
                items.add(item);
            }
        }
        return items;
    }
}
