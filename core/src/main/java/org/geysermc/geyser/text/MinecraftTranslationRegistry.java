/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is used for mapping a translation key with the already loaded Java locale data
 * Used in MessageTranslator.java as part of the KyoriPowered/Adventure library
 */
public class MinecraftTranslationRegistry extends TranslatableComponentRenderer<String> {
    private final Pattern stringReplacement = Pattern.compile("%s");
    private final Pattern positionalStringReplacement = Pattern.compile("%([0-9]+)\\$s");
    private final Pattern escapeBraces = Pattern.compile("\\{+['{]+\\{+|\\{+");

    // Exists to maintain compatibility with Velocity's older Adventure version
    @Override
    public @Nullable MessageFormat translate(@NonNull String key, @NonNull String locale) {
        return this.translate(key, null, locale);
    }

    @Override
    protected @Nullable MessageFormat translate(@NonNull String key, @Nullable String fallback, @NonNull String locale) {
        // Get the locale string
        String localeString = MinecraftLocale.getLocaleStringIfPresent(key, locale);
        if (localeString == null) {
            if (fallback != null) {
                // Fallback strings will still have their params inserted
                localeString = fallback;
            } else {
                // The original translation will be translated
                // Can be tested with 1.19.4: {"translate":"%s","with":[{"text":"weeeeeee"}]}
                return null;
            }
        }

        // replace single quote instances which get lost in MessageFormat otherwise
        localeString = localeString.replace("'", "''");

        // Escape all left curly brackets with single quote - fixes https://github.com/GeyserMC/Geyser/issues/4662
        Pattern p = escapeBraces;
        Matcher m = p.matcher(localeString);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, "'" + m.group() + "'");
        }
        m.appendTail(sb);

        // Replace the `%s` with numbered inserts `{0}`
        p = stringReplacement;
        m = p.matcher(sb.toString());
        sb = new StringBuilder();
        int i = 0;
        while (m.find()) {
            m.appendReplacement(sb, "{" + (i++) + "}");
        }
        m.appendTail(sb);

        // Replace the `%x$s` with numbered inserts `{x}`
        p = positionalStringReplacement;
        m = p.matcher(sb.toString());
        sb = new StringBuilder();
        while (m.find()) {
            i = Integer.parseInt(m.group(1)) - 1;
            m.appendReplacement(sb, "{" + i + "}");
        }
        m.appendTail(sb);

        // Locale shouldn't need to be specific - dates for example will not be handled
        return new MessageFormat(sb.toString(), Locale.ROOT);
    }
    /**
     * MINEZ：基岩版不能显示文本中内联的图标与玩家头像（Java 版的 object 组件），原先显示为“[unknown player head]”“[item/iron_ingot@items]”这样的文字。
     * 现在依次为：资源包中有这个图标的字形时显示字形（见 {@link SpriteGlyphs}），与 Java 版相同；有备用文字时显示备用文字；
     * 原版物品与方块的图标（{@code item/<名称>}、{@code block/<名称>}）显示为它的名称；其余的不显示。
     */
    @Override
    protected @NonNull Component renderObject(@NonNull ObjectComponent component, @NonNull String locale) {
        Component replacement;
        Component fallback = component.fallback();
        String glyph = component.contents() instanceof SpriteObjectContents sprite ? SpriteGlyphs.glyph(sprite.atlas(), sprite.sprite()) : null;
        if (glyph != null) {
            // MINEZ：资源包中有这个图标的字形时显示字形，与 Java 版相同。
            replacement = Component.text(glyph);
        } else if (fallback != null) {
            replacement = render(fallback, locale);
        } else if (component.contents() instanceof SpriteObjectContents sprite) {
            String name = spriteName(sprite.sprite(), locale);
            replacement = name == null ? Component.empty() : Component.text(name);
        } else {
            replacement = Component.empty();
        }
        TextComponent.Builder builder = Component.text().style(component.style()).append(replacement);
        for (Component child : component.children()) {
            builder.append(render(child, locale));
        }
        return builder.build();
    }

    /**
     * @return 原版物品或方块图标对应的名称，没有时为 {@code null}。
     */
    private static @Nullable String spriteName(Key sprite, String locale) {
        if (!Key.MINECRAFT_NAMESPACE.equals(sprite.namespace())) {
            return null;
        }
        String path = sprite.value();
        int slash = path.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String kind = path.substring(0, slash);
        if (!kind.equals("item") && !kind.equals("block")) {
            return null;
        }
        String name = path.substring(slash + 1);
        String translated = MinecraftLocale.getLocaleStringIfPresent(kind + ".minecraft." + name, locale);
        if (translated == null && kind.equals("item")) {
            // 方块的物品图标在 item 目录中，名称却是 block 的。
            translated = MinecraftLocale.getLocaleStringIfPresent("block.minecraft." + name, locale);
        }
        return translated;
    }
}
