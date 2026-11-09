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
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.mappings.MappingsType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MINEZ：物品栏中实时渲染的 3D 物品图标。
 * <p>
 * 基岩版的物品图标只能是一张贴图，3D 模型的物品（例如翅膀）只能事先渲染成图片。MINEZ 的资源包“MINEZ 资源”（由
 * MINEZ/CraftengineResources 的生成工具生成）为这些物品各另附一个只用于显示的客户端实体（{@code minez:icon_<模型>}，几何体为物品栏中
 * 看到的模型），映射文件的 {@code model_icons} 写明每个模型对应的实体与显示参数。MINEZ 客户端模组在物品格上用网易的纸娃娃控件渲染这个
 * 实体，代替贴图图标，见 {@link org.geysermc.geyser.session.MinezClientUi}。
 * <p>
 * 基岩版只认得服务器在 AvailableEntityIdentifiers 中列出的实体，因此读入时把这些实体加进列表（不可召唤、没有刷怪蛋，服务器不会生成
 * 它们）。Geyser 启动与重载时读入（{@link #load()}）；重载时从映射中删去的实体仍留在列表中，直到重启（不影响什么）。
 */
public final class ModelIcons {
    private static final AtomicInteger RUNTIME_ID_ALLOCATOR = new AtomicInteger(200000);
    private static volatile Map<String, Map<String, Object>> icons = Map.of();

    private ModelIcons() {
    }

    /**
     * 从 custom_mappings 中的映射文件读入，并把其中的实体加进基岩版的实体列表。
     */
    public static void load() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        MappingsConfigReader.loadCustomMappingsFromJson(MappingsType.MODEL_ICONS, map::put);
        set(map);
        if (!map.isEmpty()) {
            int added = registerEntities(map);
            GeyserImpl.getInstance().getLogger().info("Registered " + map.size() + " model icons (" + added + " new icon entities)");
        }
    }

    /**
     * 替换整张表。
     */
    public static void set(Map<String, Map<String, Object>> map) {
        icons = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /**
     * 3D 图标的表：键为物品模型（{@code minez:<模型>}，即基岩版物品标识中“.”之前的部分），值为实体标识与显示参数。原样发给客户端模组。
     */
    public static Map<String, Map<String, Object>> icons() {
        return icons;
    }

    /**
     * 把表中的实体加进基岩版的实体列表，已经在列表中的（例如重载时）不重复添加。
     *
     * @return 新加的实体数。
     */
    private static int registerEntities(Map<String, Map<String, Object>> map) {
        NbtMap identifiers = Registries.BEDROCK_ENTITY_IDENTIFIERS.get();
        NbtMap updated = withEntities(identifiers, map);
        int added = updated.getList("idlist", NbtType.COMPOUND).size() - identifiers.getList("idlist", NbtType.COMPOUND).size();
        if (added > 0) {
            Registries.BEDROCK_ENTITY_IDENTIFIERS.set(updated);
        }
        return added;
    }

    /**
     * @return 加上表中的实体之后的实体列表（AvailableEntityIdentifiers 的内容），已有的不重复添加。
     */
    static NbtMap withEntities(NbtMap identifiers, Map<String, Map<String, Object>> map) {
        List<NbtMap> list = new ArrayList<>(identifiers.getList("idlist", NbtType.COMPOUND));
        Set<String> known = new HashSet<>();
        for (NbtMap entry : list) {
            known.add(entry.getString("id"));
        }
        boolean changed = false;
        for (Map<String, Object> icon : map.values()) {
            String entity = (String) icon.get("entity");
            if (!known.add(entity)) {
                continue;
            }
            list.add(NbtMap.builder()
                .putBoolean("hasSpawnEgg", false)
                .putString("id", entity)
                .putBoolean("summonable", false)
                .putString("bid", "")
                .putInt("rid", RUNTIME_ID_ALLOCATOR.getAndIncrement())
                .putBoolean("experimental", false)
                .build());
            changed = true;
        }
        return changed ? identifiers.toBuilder().putList("idlist", NbtType.COMPOUND, list).build() : identifiers;
    }
}
