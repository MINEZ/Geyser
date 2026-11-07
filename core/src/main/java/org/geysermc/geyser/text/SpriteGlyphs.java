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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * MINEZ：Java 版文字中的图集精灵（sprite 对象组件）在基岩版中对应的字体字符。
 * <p>
 * 基岩版的文字不能显示图集中的贴图，MINEZ 的资源包“MINEZ 资源”（由 MINEZ/CraftengineResources 的生成工具生成）把这些贴图画进字形图
 * （font/glyph_F1.png 等，私用区字符），同一个工具生成的映射文件 {@code custom_mappings/minez_resources.json} 的 {@code sprites}
 * 写明每个精灵对应的字符：{@code {"图集": {"精灵": "字符"}}}。图集与精灵都按 {@link Key} 规范化，没有写命名空间的为 {@code minecraft}。
 * 多块拼成的图标（例如称号的各块）整组合成一个字，第一块对应这个字，其余各块对应空字符串（不输出），见 {@link #groupOnNewLine}。
 * 动态图标另见 {@link #animated()}。Geyser 启动时读入（{@link #load()}），关闭时清空。
 */
public final class SpriteGlyphs {
    private static volatile Map<String, String> glyphs = Map.of();
    private static volatile Map<String, Map<String, Object>> animated = Map.of();
    private static volatile Set<Character> groups = Set.of();

    private SpriteGlyphs() {
    }

    /**
     * 从 custom_mappings 中的映射文件读入。
     */
    public static void load() {
        Map<String, String> map = new Object2ObjectOpenHashMap<>();
        MappingsConfigReader.loadCustomMappingsFromJson(MappingsType.SPRITES, map::put);
        set(map);
        Map<String, Map<String, Object>> animations = new LinkedHashMap<>();
        MappingsConfigReader.loadCustomMappingsFromJson(MappingsType.ANIMATED_SPRITES, animations::put);
        setAnimated(animations);
        if (!map.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + map.size() + " sprite glyphs, " + animations.size() + " of them animated");
        }
    }

    /**
     * 替换全部映射，键见 {@link #key}。
     */
    public static void set(Map<String, String> map) {
        glyphs = Map.copyOf(map);
    }

    /**
     * 替换动态图标的表。
     */
    public static void setAnimated(Map<String, Map<String, Object>> map) {
        animated = Collections.unmodifiableMap(new LinkedHashMap<>(map));
        Set<Character> chars = new HashSet<>();
        map.forEach((key, value) -> {
            if (key.length() == 1 && value.get("tiles") instanceof Number tiles && tiles.intValue() > 1) {
                chars.add(key.charAt(0));
            }
        });
        groups = Set.copyOf(chars);
    }

    /**
     * 动态图标的表：键为一组字形字符（例如一个称号的各块），值为模组播放动画所需的贴图路径、每帧的宽高（像素）、帧数、每秒帧数与块数。
     * 由映射文件的 {@code animated_sprites} 读入，原样发给 MINEZ 客户端模组，见 {@link org.geysermc.geyser.session.MinezClientUi}。
     */
    public static Map<String, Map<String, Object>> animated() {
        return animated;
    }

    /**
     * 把文字中第一个多块拼成的动态图标（例如称号）换到下一行：去掉它前面的空格，前面的颜色与格式代码移到换行之后。
     * 用于玩家列表中的名字：暂停菜单中人物模型上方的名字因此显示为“名字”与“称号”两行。图标前面没有别的文字时原样返回。
     */
    public static String groupOnNewLine(String text) {
        Set<Character> chars = groups;
        int index = -1;
        for (int i = 0; i < text.length() && !chars.isEmpty(); i++) {
            if (chars.contains(text.charAt(i))) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return text;
        }
        int end = index;
        StringBuilder codes = new StringBuilder();
        while (end > 0) {
            if (end >= 2 && text.charAt(end - 2) == '\u00a7') {
                codes.insert(0, text, end - 2, end);
                end -= 2;
            } else if (Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            } else {
                break;
            }
        }
        String before = text.substring(0, end);
        if (before.replaceAll("\u00a7.", "").isBlank()) {
            return text;
        }
        return before + "\n" + codes + text.substring(index);
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
