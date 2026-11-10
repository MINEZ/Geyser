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

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.mappings.MappingsType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MINEZ：物品格中的 3D 物品图标。
 * <p>
 * 基岩版的物品图标只能是物品图集中的一格，3D 模型的物品（例如翅膀）事先渲染成的图标受图集大小所限只有 32 像素。MINEZ 的资源包
 * “MINEZ 资源”（由 MINEZ/CraftengineResources 的生成工具生成）为映射文件的 {@code model_icons} 中的物品另外按 Java 版物品栏的画法
 * （gui 显示设置的变换与光照）渲染一张高清图片，写明贴图路径、每帧的宽高、帧数与每秒帧数。MINEZ 客户端模组把这张图片盖在原版图标的
 * 位置与大小上，见 {@link org.geysermc.geyser.session.MinezClientUi}。Geyser 启动与重载时读入（{@link #load()}）。
 */
public final class ModelIcons {
    private static volatile Map<String, Map<String, Object>> icons = Map.of();

    private ModelIcons() {
    }

    /**
     * 从 custom_mappings 中的映射文件读入。
     */
    public static void load() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        MappingsConfigReader.loadCustomMappingsFromJson(MappingsType.MODEL_ICONS, map::put);
        set(map);
        if (!map.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + map.size() + " model icons");
        }
    }

    /**
     * 替换整张表。
     */
    public static void set(Map<String, Map<String, Object>> map) {
        icons = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /**
     * 3D 图标的表：键为物品模型（{@code minez:<模型>}，即基岩版物品标识中“.”之前的部分），值为图片的贴图路径、每帧的宽高、帧数与每秒
     * 帧数。原样发给客户端模组。
     */
    public static Map<String, Map<String, Object>> icons() {
        return icons;
    }
}
