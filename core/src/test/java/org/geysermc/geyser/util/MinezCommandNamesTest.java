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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MINEZ：名字以“/”开头的命令在基岩版中写作“_”开头，见 {@link MinezCommandNames}。
 */
public class MinezCommandNamesTest {

    @Test
    public void renamesSlashCommandsForBedrock() {
        Set<String> javaNames = Set.of("/set", "/pos1", "tp", "_own", "/own");
        assertEquals("_set", MinezCommandNames.toBedrock("/set", javaNames));
        assertEquals("tp", MinezCommandNames.toBedrock("tp", javaNames));
        // Java 版本来就有 _own：/own 不改名，以免两个命令同名
        assertEquals("/own", MinezCommandNames.toBedrock("/own", javaNames));
        assertEquals("/", MinezCommandNames.toBedrock("/", Set.of("/")));
    }

    @Test
    public void restoresSlashCommandsForJava() {
        List<String> javaNames = List.of("/set", "/pos1", "tp", "_own", "/own");
        assertEquals("/set stone", MinezCommandNames.toJava("_set stone", javaNames));
        // 大小写原样保留（Java 版的命令名不分大小写）
        assertEquals("/POS1", MinezCommandNames.toJava("_POS1", javaNames));
        // 本来就以“_”开头的命令、Java 版没有对应命令的、普通命令都原样发送
        assertEquals("_own x", MinezCommandNames.toJava("_own x", javaNames));
        assertEquals("_nothing", MinezCommandNames.toJava("_nothing", javaNames));
        assertEquals("_", MinezCommandNames.toJava("_", javaNames));
        assertEquals("tp a b", MinezCommandNames.toJava("tp a b", javaNames));
    }
}
