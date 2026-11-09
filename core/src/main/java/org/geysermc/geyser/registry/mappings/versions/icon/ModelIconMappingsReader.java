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

package org.geysermc.geyser.registry.mappings.versions.icon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.mappings.MappingsReader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * MINEZ：读取映射文件中的 {@code model_icons}：{@code {"minez:模型": {"entity": "minez:icon_模型", "scale": 1.0, "rot_x": 0,
 * "rot_y": 0, "rot_z": 0, "offset_x": 0, "offset_y": 0}}}，见 {@link org.geysermc.geyser.item.ModelIcons}。entity 须为字符串，
 * 其余各项可以省略，有的须为数字；格式不对的项记一条错误后跳过。
 */
public class ModelIconMappingsReader implements MappingsReader<String, Map<String, Object>> {
    private static final String[] NUMBERS = {"scale", "rot_x", "rot_y", "rot_z", "offset_x", "offset_y"};

    @Override
    public void read(Path file, JsonObject mappings, BiConsumer<String, Map<String, Object>> consumer) {
        for (Map.Entry<String, JsonElement> entry : mappings.entrySet()) {
            Map<String, Object> icon = read(entry.getValue());
            if (entry.getKey().isEmpty() || icon == null) {
                GeyserImpl.getInstance().getLogger().error("Invalid model icon " + entry.getKey() + " in custom mappings file " + file);
                continue;
            }
            consumer.accept(entry.getKey(), icon);
        }
    }

    private static Map<String, Object> read(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement entity = object.get("entity");
        if (entity == null || !entity.isJsonPrimitive() || !entity.getAsJsonPrimitive().isString() || entity.getAsString().isEmpty()) {
            return null;
        }
        Map<String, Object> icon = new LinkedHashMap<>();
        icon.put("entity", entity.getAsString());
        for (String key : NUMBERS) {
            JsonElement value = object.get(key);
            if (value == null) {
                continue;
            }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            JsonPrimitive number = value.getAsJsonPrimitive();
            icon.put(key, number.getAsDouble());
        }
        return Map.copyOf(icon);
    }
}
