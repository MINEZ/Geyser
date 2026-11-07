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

package org.geysermc.geyser.registry.mappings.versions.sprite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.mappings.MappingsReader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * MINEZ：读取映射文件中的 {@code translations}：{@code {"zh_cn": {"键": "文字"}}}，见 {@link org.geysermc.geyser.text.CustomTranslations}。
 * 语言按小写处理；值不是字符串的项记一条错误后跳过。
 */
public class TranslationMappingsReader implements MappingsReader<String, Map<String, String>> {

    @Override
    public void read(Path file, JsonObject mappings, BiConsumer<String, Map<String, String>> consumer) {
        for (Map.Entry<String, JsonElement> locale : mappings.entrySet()) {
            if (!locale.getValue().isJsonObject()) {
                GeyserImpl.getInstance().getLogger().error("Translations of " + locale.getKey() + " in custom mappings file " + file + " are not an object");
                continue;
            }
            Map<String, String> entries = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : locale.getValue().getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    GeyserImpl.getInstance().getLogger().error("Translation " + entry.getKey() + " in custom mappings file " + file + " is not a string");
                    continue;
                }
                entries.put(entry.getKey(), value.getAsString());
            }
            consumer.accept(locale.getKey().toLowerCase(Locale.ROOT), entries);
        }
    }
}
