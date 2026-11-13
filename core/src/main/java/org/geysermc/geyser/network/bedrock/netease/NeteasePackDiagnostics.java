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

package org.geysermc.geyser.network.bedrock.netease;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 只为资源协商与 SyncUsingMod 生成有大小上限的单行诊断摘要。 */
public final class NeteasePackDiagnostics {
    public static final int MAX_OUTPUT = 16384;
    private static final int MAX_NODES = 256;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_STRING = 4096;

    private final StringBuilder output = new StringBuilder();
    private int nodes;

    private NeteasePackDiagnostics() {
    }

    public static String describe(Object value) {
        NeteasePackDiagnostics diagnostic = new NeteasePackDiagnostics();
        diagnostic.value(value, 0);
        if (diagnostic.output.length() > MAX_OUTPUT) {
            diagnostic.output.setLength(MAX_OUTPUT - 15);
            diagnostic.output.append("...[truncated]");
        }
        return diagnostic.output.toString();
    }

    private void value(Object value, int depth) {
        if (output.length() > MAX_OUTPUT) {
            return;
        }
        if (++nodes > MAX_NODES || depth > MAX_DEPTH) {
            output.append("\"[truncated]\"");
        } else if (value == null || value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof String text) {
            string(text);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                if (nodes >= MAX_NODES || output.length() > MAX_OUTPUT) {
                    output.append("\"[truncated]\":true");
                    break;
                }
                String key = String.valueOf(entry.getKey());
                string(key);
                output.append(':');
                value(sensitive(key) ? "[redacted]" : entry.getValue(), depth + 1);
            }
            output.append('}');
        } else if (value instanceof List<?> list) {
            output.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    output.append(',');
                }
                if (nodes >= MAX_NODES || output.length() > MAX_OUTPUT) {
                    output.append("\"[truncated]\"");
                    break;
                }
                value(list.get(i), depth + 1);
            }
            output.append(']');
        } else {
            output.append("\"[unsupported]\"");
        }
    }

    private static boolean sensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("token") || normalized.contains("password") || normalized.contains("secret")
            || normalized.contains("signature") || normalized.endsWith("sid") || normalized.equals("uid")
            || normalized.equals("xuid") || normalized.equals("playerid") || normalized.equals("identity")
            || normalized.equals("authorization") || normalized.endsWith("key");
    }

    private void string(String text) {
        output.append('"');
        int length = Math.min(text.length(), MAX_STRING);
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"', '\\' -> output.append('\\').append(ch);
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (Character.isISOControl(ch) || ch == '\u2028' || ch == '\u2029' || ch == '\u00a7') {
                        output.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        output.append(ch);
                    }
                }
            }
        }
        if (text.length() > length) {
            output.append("...[truncated]");
        }
        output.append('"');
    }
}
