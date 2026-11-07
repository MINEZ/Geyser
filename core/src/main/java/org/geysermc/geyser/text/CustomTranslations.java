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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.mappings.MappingsType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MINEZ：服务器资源包中新增的翻译（例如新魔咒的名称 {@code enchantment.minez.tunneling}）。Java 版客户端从服务器的资源包中取得它们，
 * Geyser 只有原版的语言文件，原先把这些键原样显示。映射文件的 {@code translations} 写明各语言的翻译（由 MINEZ/CraftengineResources
 * 的生成工具从资源包的 lang/ 中取出），原版的翻译中找不到某个键时使用：先找玩家的语言，再找 en_us，最后找任意一种语言。
 */
public final class CustomTranslations {
    private static volatile Map<String, Map<String, String>> translations = Map.of();

    private CustomTranslations() {
    }

    /**
     * 从 custom_mappings 中的映射文件读入。
     */
    public static void load() {
        Map<String, Map<String, String>> map = new HashMap<>();
        MappingsConfigReader.loadCustomMappingsFromJson(MappingsType.TRANSLATIONS,
            (locale, entries) -> map.computeIfAbsent(locale, k -> new HashMap<>()).putAll(entries));
        set(map);
        if (!map.isEmpty()) {
            int count = map.values().stream().mapToInt(Map::size).sum();
            GeyserImpl.getInstance().getLogger().info("Registered " + count + " custom translations in " + map.size() + " languages");
        }
    }

    /**
     * 替换全部翻译：{语言（小写）: {键: 文字}}。
     */
    public static void set(Map<String, Map<String, String>> map) {
        Map<String, Map<String, String>> copy = new HashMap<>();
        map.forEach((locale, entries) -> copy.put(locale.toLowerCase(Locale.ROOT), Map.copyOf(entries)));
        translations = Map.copyOf(copy);
    }

    /**
     * @return 这个键的翻译，没有时为 null。
     */
    public static @Nullable String get(String key, String locale) {
        Map<String, Map<String, String>> all = translations;
        if (all.isEmpty()) {
            return null;
        }
        Map<String, String> entries = all.get(locale.toLowerCase(Locale.ROOT));
        if (entries != null && entries.containsKey(key)) {
            return entries.get(key);
        }
        entries = all.get("en_us");
        if (entries != null && entries.containsKey(key)) {
            return entries.get(key);
        }
        for (Map<String, String> other : all.values()) {
            String value = other.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
