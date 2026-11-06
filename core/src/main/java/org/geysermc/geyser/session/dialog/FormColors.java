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

package org.geysermc.geyser.session.dialog;

/**
 * MINEZ：让对话框的文字在基岩版表单的背景上看得清。
 * <p>
 * Java 版的对话框整体画在变暗的游戏画面上，服务器的文字因此多用白色、黄色等浅色。基岩版的表单中，标题栏、按钮与下拉框是浅灰色的，
 * 浅色的文字几乎看不见；正文区域则是深色的，黑色与深灰色的文字同样看不清。这里按背景替换颜色代码，色相尽量不变：
 * <ul>
 *     <li>浅色背景（标题、按钮、下拉框中的选项）：白色与灰色换成黑色（按钮在悬停时变为绿色，深灰色在绿色上看不清），黄色与金色换成铜色（基岩版的 {@code §n}，不支持的客户端显示默认的深色），
 *     浅绿、浅蓝、浅紫换成对应的深色；红色等本来就看得清的不变。</li>
 *     <li>深色背景（正文）：黑色与深灰色换成灰色。</li>
 * </ul>
 * 输入与输出都是基岩版的文字（{@code MessageTranslator} 转换之后），只替换颜色代码，格式代码不变。
 */
public final class FormColors {
    private static final char SECTION = '§';
    /** 浅色背景上的替换，下标与 {@link #LIGHT_FROM} 对应。 */
    private static final String LIGHT_FROM = "f7e6abd";
    private static final String LIGHT_TO = "00nn235";
    private static final String DARK_FROM = "08";
    private static final String DARK_TO = "77";

    private FormColors() {
    }

    /** 用于浅色背景：标题、按钮与下拉框中的选项。 */
    public static String onLight(String text) {
        return replace(text, LIGHT_FROM, LIGHT_TO);
    }

    /** 用于深色背景：正文。 */
    public static String onDark(String text) {
        return replace(text, DARK_FROM, DARK_TO);
    }

    private static String replace(String text, String from, String to) {
        if (text.indexOf(SECTION) < 0) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(c);
            if (c == SECTION && i + 1 < text.length()) {
                char code = text.charAt(++i);
                int index = from.indexOf(Character.toLowerCase(code));
                builder.append(index < 0 ? code : to.charAt(index));
            }
        }
        return builder.toString();
    }
}
