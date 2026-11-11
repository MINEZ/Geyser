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

package org.geysermc.geyser.registry.mappings.versions.display;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.DisplayModels;
import org.geysermc.geyser.registry.mappings.MappingsReader;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * MINEZ：读取映射文件中的 {@code display_entities}：{@code {"物品模型|显示方式": "实体标识"}}，见 {@link DisplayModels}。物品模型与实体标识
 * 都须为带命名空间的标识，实体标识不能用 minecraft 命名空间；显示方式为 Java 版物品展示实体的 display（none、head 等）。格式不对的项记一条
 * 错误后跳过，不影响其他项。
 */
public class DisplayEntityMappingsReader implements MappingsReader<String, String> {

    @Override
    public void read(Path file, JsonObject mappings, BiConsumer<String, String> consumer) {
        for (Map.Entry<String, JsonElement> entry : mappings.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            JsonElement value = entry.getValue();
            if (parts.length != 2 || !DisplayModels.CONTEXTS.contains(parts[1]) || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
                GeyserImpl.getInstance().getLogger().error("Invalid display entity " + entry.getKey() + " in custom mappings file " + file);
                continue;
            }
            Key model;
            Key entity;
            try {
                model = Key.key(parts[0]);
                entity = Key.key(value.getAsString());
            } catch (InvalidKeyException exception) {
                entity = null;
                model = null;
            }
            if (model == null || entity.namespace().equals(Key.MINECRAFT_NAMESPACE)) {
                GeyserImpl.getInstance().getLogger().error("Invalid display entity " + entry.getKey() + " in custom mappings file " + file);
                continue;
            }
            consumer.accept(DisplayModels.key(model, parts[1]), entity.asString());
        }
    }
}
