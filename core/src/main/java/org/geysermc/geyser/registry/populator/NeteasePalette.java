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
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.registry.populator.conversion.BlockRemapper;
import org.geysermc.geyser.registry.type.PaletteItem;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 网易客户端另有自己的物品（{@code minecraft:mod}、{@code minecraft:mod_ex}，PC 开发版另有 {@code minecraft:debug_stick}），客户端
 * 自带，不在服务端下发的物品表中，也不能下发（同一个物品会注册两次）。它们的编号不固定，随服务器下发的自定义物品的个数移动：
 * 2026-11-16 比对两份完整的客户端物品表（正式服的安卓正式版 3.10.5、测试服的 PC 开发版 3.10.0，自定义物品 649 与 814 个），
 * 以自定义区间（原版物品之后，见 {@link #RESERVED_OFFSETS}）分配的条目数为 T、起点为 S，网易物品落在 S + T 往前的固定位置：安卓的
 * mod、mod_ex 在 −213、−212，PC 的 debug_stick、mod、mod_ex 在 −216、−212、−211。自定义物品用到这些编号时显示成网易的物品或透明。
 * T 不含方块物品覆盖项（原版物品换成自定义方块的方块物品，b1305.19 起登记）的条目：网易客户端不数它们，2026-11-21 正式版客户端的
 * 物品表核对中网易物品比按含覆盖项算出的位置靠前 117 个，正是覆盖项的个数（见 ItemRegistryPopulator 的 blockItemOverrideCount）。
 * 两次观测时 Geyser 都在自定义区间里留了 2 个空号，因此分不清网易数的是“条目数”还是“最大编号”（后者随空号数变化）；
 * {@link #RESERVED_OFFSETS} 预留的 7 个位置对两种情况都成立。实际的物品表仍由 MINEZ 客户端模组每个会话读出、Geyser 比对后写进日志与
 * {@code netease_item_table.json}（见 {@code MinezItemTableCheck}），网易更新客户端后据此核对；{@code bedrock/netease_items.<版本>.json}
 * 只记网易物品的名字，供比对时认出它们。
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
     * 网易客户端自己的物品：名字 → 是否每种客户端都有，由 {@link #NETEASE_ITEMS_FILE} 读入。
     */
    private static volatile Map<String, Boolean> neteaseItems = Map.of();

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
     * 读入网易客户端自己的物品：{@code {"items": [{"name": "minecraft:mod"}, {"name": "minecraft:debug_stick", "optional": true}, ...]}}。
     * optional 为只有部分客户端才有的物品（例如 debug_stick 只有 PC 开发版有），客户端没有时不算问题。编号不记：它们随自定义物品的
     * 个数移动（见类的说明）。
     *
     * @return 名字 → 是否每种客户端都有
     */
    static Map<String, Boolean> readNeteaseItems(InputStream stream) {
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Boolean> items = new LinkedHashMap<>();
        for (JsonElement element : json.getAsJsonArray("items")) {
            JsonObject item = element.getAsJsonObject();
            JsonElement optional = item.get("optional");
            items.put(item.get("name").getAsString(), optional == null || !optional.getAsBoolean());
        }
        return Collections.unmodifiableMap(items);
    }

    static void setNeteaseItems(Map<String, Boolean> items) {
        neteaseItems = items;
    }

    /**
     * @return 网易客户端自己的物品：名字 → 是否每种客户端都有。
     */
    public static Map<String, Boolean> neteaseItems() {
        return neteaseItems;
    }

    /**
     * 网易物品在自定义区间末尾往前的位置（见类的说明）：以 S + T 为 0，安卓与 PC 的网易物品依次在 −216、−213、−212、−211；网易若数的是
     * 最大编号，空号数为 7 时它们再往后移 5，落在 −211、−208、−207、−206。合起来正好 7 个，预留后两种情况都不撞号。
     */
    static final int[] RESERVED_OFFSETS = {-216, -213, -212, -211, -208, -207, -206};

    /**
     * 自定义区间的起点（原版物品之后的第一个编号）与这次要分配的条目数，由分配前统计（国际版 1.21.130 的物品表与网易的原版部分
     * 相同，先分配，条目数一致）。
     */
    private static volatile IntSet reservedIds = IntSets.EMPTY_SET;

    /**
     * 按自定义区间算出网易物品可能落在的编号（见 {@link #RESERVED_OFFSETS}），之后分配编号时跳过。
     *
     * @param start 自定义区间的起点
     * @param count 这次要分配的条目数（不含预留的空号）
     */
    static void setCustomRange(int start, int count) {
        reservedIds = reservedIds(start, count);
    }

    static IntSet reservedIds(int start, int count) {
        IntSet ids = new IntOpenHashSet();
        for (int offset : RESERVED_OFFSETS) {
            int id = start + count + offset;
            if (id >= start) {
                ids.add(id);
            }
        }
        return IntSets.unmodifiable(ids);
    }

    static IntSet reservedIds() {
        return reservedIds;
    }

    /**
     * @return 从 id 起第一个不是网易物品可能落在的编号。
     */
    static int nextFreeItemId(int id) {
        IntSet reserved = reservedIds;
        while (reserved.contains(id)) {
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
