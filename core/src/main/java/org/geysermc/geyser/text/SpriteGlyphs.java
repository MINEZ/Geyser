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

package org.geysermc.geyser.text;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.mappings.MappingsType;

import java.util.Map;

/**
 * MINEZ：Java 版文字中的图集精灵（sprite 对象组件）在基岩版中对应的字体字符。
 * <p>
 * 基岩版的文字不能显示图集中的贴图，MINEZ 的资源包“MINEZ 资源”（由 MINEZ/CraftengineResources 的生成工具生成）把这些贴图画进字形图
 * （font/glyph_F1.png 等，私用区字符），同一个工具生成的映射文件 {@code custom_mappings/minez_resources.json} 的 {@code sprites}
 * 写明每个精灵对应的字符：{@code {"图集": {"精灵": "字符"}}}。图集与精灵都按 {@link Key} 规范化，没有写命名空间的为 {@code minecraft}。
 * Geyser 启动时读入（{@link #load()}），关闭时清空。
 */
public final class SpriteGlyphs {
    private static volatile Map<String, String> glyphs = Map.of();

    private SpriteGlyphs() {
    }

    /**
     * 从 custom_mappings 中的映射文件读入。
     */
    public static void load() {
        Map<String, String> map = new Object2ObjectOpenHashMap<>();
        MappingsConfigReader.loadCustomMappingsFromJson(MappingsType.SPRITES, map::put);
        set(map);
        if (!map.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + map.size() + " sprite glyphs");
        }
    }

    /**
     * 替换全部映射，键见 {@link #key}。
     */
    public static void set(Map<String, String> map) {
        glyphs = Map.copyOf(map);
    }

    /**
     * @return 映射中使用的键。
     */
    public static String key(Key atlas, Key sprite) {
        return atlas.asString() + "|" + sprite.asString();
    }

    /**
     * @return 这个精灵在基岩版中对应的字符；没有映射时为 null。
     */
    public static @Nullable String glyph(Key atlas, Key sprite) {
        return glyphs.get(key(atlas, sprite));
    }
}
