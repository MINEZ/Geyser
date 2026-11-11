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

package org.geysermc.geyser.entity;

import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.mappings.MappingsType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MINEZ：物品展示实体在基岩版中的样子。
 * <p>
 * 基岩版没有物品展示实体，上游 Geyser 不转换它，基岩版玩家看不到降落中的空投箱、降落伞与身上的翅膀。MINEZ 的资源包“MINEZ 资源”（由
 * MINEZ/CraftengineResources 的生成工具生成）为映射文件 {@code display_entities} 中的每个物品模型与显示方式生成一种基岩版的自定义实体：
 * Java 版的模型按这种显示方式的变换（与物品展示实体自带的转向）事先转成基岩版的模型，原点即实体的位置。Geyser 启动时注册这些实体（{@link
 * #register(List)}），物品展示实体有了物品后按物品模型与显示方式换成对应的实体，见 {@link org.geysermc.geyser.entity.type.ItemDisplayEntity}。
 * 自定义实体只在启动时注册，映射文件改动后须重启。
 */
public final class DisplayModels {
    /**
     * Java 版 ItemDisplayContext 的名称，按序号排列（物品展示实体的 display 以序号同步）。
     */
    public static final List<String> CONTEXTS = List.of("none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand",
        "firstperson_righthand", "head", "gui", "ground", "fixed", "on_shelf");

    private static volatile Map<String, CustomBedrockEntityDefinition> definitions = Map.of();

    private DisplayModels() {
    }

    public static String key(Key model, String context) {
        return model.asString() + "|" + context;
    }

    /**
     * 从 custom_mappings 中的映射文件读入，为其中的实体创建并注册自定义实体定义，加进 {@code registered}（随后写进下发给客户端的实体
     * 标识表）。
     */
    public static void register(List<CustomBedrockEntityDefinition> registered) {
        Map<String, String> mappings = new LinkedHashMap<>();
        MappingsConfigReader.loadCustomMappingsFromJson(MappingsType.DISPLAY_ENTITIES, mappings::put);
        Map<String, CustomBedrockEntityDefinition> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            CustomBedrockEntityDefinition definition = CustomBedrockEntityDefinition.getOrCreate(Identifier.of(entry.getValue()));
            if (!definition.registered()) {
                Registries.BEDROCK_ENTITY_DEFINITIONS.register(definition.identifier(), definition);
                registered.add(definition);
            }
            map.put(entry.getKey(), definition);
        }
        definitions = Collections.unmodifiableMap(map);
        if (!map.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + map.size() + " display models");
        }
    }

    /**
     * 物品模型以这种显示方式显示时的基岩版实体，没有时为 null。
     */
    public static @Nullable CustomBedrockEntityDefinition get(Key model, int context) {
        if (context < 0 || context >= CONTEXTS.size()) {
            return null;
        }
        return definitions.get(key(model, CONTEXTS.get(context)));
    }

    /**
     * 替换整张表（测试用）。
     */
    public static void set(Map<String, CustomBedrockEntityDefinition> map) {
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
