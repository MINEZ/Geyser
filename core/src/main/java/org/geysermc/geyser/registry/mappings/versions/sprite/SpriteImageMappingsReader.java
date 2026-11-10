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
import com.google.gson.JsonPrimitive;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.mappings.MappingsReader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * MINEZ：读取映射文件中文字图标的图片表，原样转发给客户端模组，这里只检查 texture 为字符串、其余各项为数字：
 * <ul>
 *     <li>{@code animated_sprites}：动态图标的帧条，{@code {"字符": {"texture": "贴图路径", "frame_width": 64, "frame_height": 16,
 *     "frames": 8, "fps": 10, "tiles": 4}}}，见 {@link org.geysermc.geyser.text.SpriteGlyphs#animated()}；</li>
 *     <li>{@code text_icons}：全部文字图标在一张图中的位置，{@code {"字符": {"texture": "贴图路径", "u": 0, "v": 0, "frame_width": 16,
 *     "frame_height": 16, "tiles": 1}}}，见 {@link org.geysermc.geyser.text.SpriteGlyphs#images()}。</li>
 * </ul>
 */
public class SpriteImageMappingsReader implements MappingsReader<String, Map<String, Object>> {
    public static final String[] ANIMATED = {"frame_width", "frame_height", "frames", "fps", "tiles"};
    public static final String[] IMAGES = {"u", "v", "frame_width", "frame_height", "tiles"};

    private final String[] numbers;

    public SpriteImageMappingsReader(String... numbers) {
        this.numbers = numbers;
    }

    @Override
    public void read(Path file, JsonObject mappings, BiConsumer<String, Map<String, Object>> consumer) {
        for (Map.Entry<String, JsonElement> entry : mappings.entrySet()) {
            Map<String, Object> sprite = read(entry.getValue());
            if (entry.getKey().isEmpty() || sprite == null) {
                GeyserImpl.getInstance().getLogger().error("Invalid sprite image " + entry.getKey() + " in custom mappings file " + file);
                continue;
            }
            consumer.accept(entry.getKey(), sprite);
        }
    }

    private Map<String, Object> read(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement texture = object.get("texture");
        if (texture == null || !texture.isJsonPrimitive() || !texture.getAsJsonPrimitive().isString()) {
            return null;
        }
        Map<String, Object> sprite = new LinkedHashMap<>();
        sprite.put("texture", texture.getAsString());
        for (String key : numbers) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            JsonPrimitive number = value.getAsJsonPrimitive();
            double d = number.getAsDouble();
            sprite.put(key, d == Math.rint(d) ? (Object) (long) d : (Object) d);
        }
        return Map.copyOf(sprite);
    }
}
