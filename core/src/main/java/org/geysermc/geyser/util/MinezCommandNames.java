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

package org.geysermc.geyser.util;

import java.util.Collection;
import java.util.Locale;

/**
 * MINEZ：名字以“/”开头的 Java 版命令（例如 WorldEdit 的 {@code //set}，命令名为 {@code /set}）在基岩版中的写法。
 * <p>
 * 基岩版客户端不发出以“//”开头的命令，输入后什么也不会发生。因此在基岩版中把命令名开头的“/”写作 {@value #PREFIX}：
 * 下发的命令表中 {@code /set} 显示为 {@code _set}（输入 {@code /_set stone} 即可补全），收到的 {@code /_set stone}
 * 换回 {@code //set stone}。只在 Java 版服务器有 {@code /set}、没有 {@code _set} 这个命令时换，不影响名字本来就以“_”开头的命令。
 */
public final class MinezCommandNames {
    /**
     * 基岩版中代替命令名开头的“/”的字符。
     */
    public static final String PREFIX = "_";

    private MinezCommandNames() {
    }

    /**
     * Java 版命令名在基岩版命令表中的名字。
     *
     * @param javaName  Java 版的命令名（小写）
     * @param javaNames Java 版服务器的全部命令名（小写）
     */
    public static String toBedrock(String javaName, Collection<String> javaNames) {
        if (javaName.length() > 1 && javaName.startsWith("/") && !javaNames.contains(PREFIX + javaName.substring(1))) {
            return PREFIX + javaName.substring(1);
        }
        return javaName;
    }

    /**
     * 基岩版玩家输入的命令（不含开头的“/”）发给 Java 版服务器时的写法。
     *
     * @param command   不含开头“/”的命令，例如 {@code _set stone}
     * @param javaNames Java 版服务器的全部命令名（小写）
     */
    public static String toJava(String command, Collection<String> javaNames) {
        if (!command.startsWith(PREFIX)) {
            return command;
        }
        int space = command.indexOf(' ');
        String name = (space < 0 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);
        String javaName = "/" + name.substring(PREFIX.length());
        if (name.length() > PREFIX.length() && !javaNames.contains(name) && javaNames.contains(javaName)) {
            return "/" + command.substring(PREFIX.length());
        }
        return command;
    }
}
