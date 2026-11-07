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

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：文本中内联的玩家头像与图标在基岩版中不显示成“[unknown player head]”之类的文字。
 */
class ObjectComponentRenderTest {
    private static final MinecraftTranslationRegistry RENDERER = new MinecraftTranslationRegistry();

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(RENDERER.render(component, "zh_cn"));
    }

    @Test
    void playerHeadsAreDropped() {
        Component chat = Component.text().append(Component.object(ObjectContents.playerHead(UUID.randomUUID()))).append(Component.text(" 玩家：你好")).build();
        assertEquals(" 玩家：你好", plain(chat));
    }

    @Test
    void fallbackIsUsedWhenPresent() {
        Component sprite = Component.object(builder -> builder.contents(ObjectContents.sprite(Key.key("minez", "item/electronic_component"))).fallback(Component.text("电子零件")));
        assertEquals("电子零件×1", plain(Component.text().append(sprite).append(Component.text("×1")).build()));
    }

    @Test
    void customSpritesWithoutFallbackAreDropped() {
        Component sprite = Component.object(ObjectContents.sprite(Key.key("minez", "item/electronic_component")));
        assertEquals("×1", plain(Component.text().append(sprite).append(Component.text("×1")).build()));
    }

    @AfterEach
    void clearGlyphs() {
        SpriteGlyphs.set(Map.of());
    }

    @Test
    void spritesWithAGlyphShowTheGlyph() {
        Key items = Key.key("minecraft", "items");
        SpriteGlyphs.set(Map.of(
            SpriteGlyphs.key(items, Key.key("minez", "item/electronic_component")), "\uF100",
            SpriteGlyphs.key(Key.key("minecraft", "blocks"), Key.key("minecraft", "block/iron_block")), "\uF101"));

        // 有字形时优先于备用文字，与 Java 版一样显示图标。
        Component withFallback = Component.object(builder -> builder
            .contents(ObjectContents.sprite(items, Key.key("minez", "item/electronic_component"))).fallback(Component.text("电子零件")));
        assertEquals("\uF100×1", plain(Component.text().append(withFallback).append(Component.text("×1")).build()));
        // 没有写图集时为 minecraft:blocks，与 Java 版相同。
        assertEquals("\uF101", plain(Component.object(ObjectContents.sprite(Key.key("block/iron_block")))));
        // 图集不同的不算同一个精灵：没有字形，照旧显示名称或不显示。
        assertEquals("", plain(Component.object(ObjectContents.sprite(Key.key("minecraft", "gui"), Key.key("minez", "item/electronic_component")))));
    }
}
