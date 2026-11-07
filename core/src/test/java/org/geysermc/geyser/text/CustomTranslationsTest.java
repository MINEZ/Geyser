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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MINEZ：服务器资源包中新增的翻译（例如新魔咒的名称）在基岩版中显示为翻译后的文字。
 */
class CustomTranslationsTest {
    private static final MinecraftTranslationRegistry RENDERER = new MinecraftTranslationRegistry();

    @AfterEach
    void clear() {
        CustomTranslations.set(Map.of());
    }

    @Test
    void customKeysAreTranslated() {
        CustomTranslations.set(Map.of("ZH_CN", Map.of("enchantment.minez.tunneling", "掘进"),
            "en_us", Map.of("enchantment.minez.smelting", "Smelting")));

        assertEquals("掘进", MinecraftLocale.getLocaleStringIfPresent("enchantment.minez.tunneling", "zh_cn"));
        // 玩家的语言中没有时依次找 en_us 与其他语言。
        assertEquals("Smelting", CustomTranslations.get("enchantment.minez.smelting", "zh_cn"));
        assertEquals("掘进", CustomTranslations.get("enchantment.minez.tunneling", "ja_jp"));
        assertNull(CustomTranslations.get("enchantment.minez.unknown", "zh_cn"));

        Component lore = Component.translatable("enchantment.minez.tunneling").append(Component.text(" I"));
        assertEquals("掘进 I", PlainTextComponentSerializer.plainText().serialize(RENDERER.render(lore, "zh_cn")));
    }
}
