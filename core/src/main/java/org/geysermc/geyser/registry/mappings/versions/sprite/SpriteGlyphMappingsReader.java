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
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.mappings.MappingsReader;
import org.geysermc.geyser.text.SpriteGlyphs;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * MINEZ：读取映射文件中的 {@code sprites}：{@code {"图集": {"精灵": "字符"}}}，见 {@link SpriteGlyphs}。
 * 字符须为字符串；空字符串表示这个精灵由前面的精灵一并画出（多块拼成的图标中第一块以外的各块），不输出。格式不对的项记一条错误后跳过，
 * 不影响其他项。
 */
public class SpriteGlyphMappingsReader implements MappingsReader<String, String> {

    @Override
    public void read(Path file, JsonObject mappings, BiConsumer<String, String> consumer) {
        for (Map.Entry<String, JsonElement> atlasEntry : mappings.entrySet()) {
            Key atlas;
            try {
                atlas = Key.key(atlasEntry.getKey());
            } catch (InvalidKeyException exception) {
                GeyserImpl.getInstance().getLogger().error("Invalid sprite atlas " + atlasEntry.getKey() + " in custom mappings file " + file);
                continue;
            }
            if (!atlasEntry.getValue().isJsonObject()) {
                GeyserImpl.getInstance().getLogger().error("Sprites of atlas " + atlas.asString() + " in custom mappings file " + file + " are not an object");
                continue;
            }
            for (Map.Entry<String, JsonElement> spriteEntry : atlasEntry.getValue().getAsJsonObject().entrySet()) {
                JsonElement value = spriteEntry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    GeyserImpl.getInstance().getLogger().error("Glyph of sprite " + spriteEntry.getKey() + " in custom mappings file " + file + " is not a string");
                    continue;
                }
                try {
                    consumer.accept(SpriteGlyphs.key(atlas, Key.key(spriteEntry.getKey())), value.getAsString());
                } catch (InvalidKeyException exception) {
                    GeyserImpl.getInstance().getLogger().error("Invalid sprite " + spriteEntry.getKey() + " in custom mappings file " + file);
                }
            }
        }
    }
}
