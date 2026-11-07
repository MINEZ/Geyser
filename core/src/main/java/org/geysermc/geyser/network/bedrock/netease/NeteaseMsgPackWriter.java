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

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * MINEZ：写 MessagePack，用于发给网易客户端模组的 {@code PyRpc}。与网易客户端（Python 2）的写法一致：字符串一律写成 bin（Python 2
 * 中读出为 str），映射的键也是字符串；整数、浮点数、布尔、nil、列表与映射按 MessagePack 规范。
 */
public final class NeteaseMsgPackWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /**
     * 写入一个值：null、CharSequence、Boolean、整数（Byte、Short、Integer、Long）、Float、Double、List、Map（键按字符串写）。
     *
     * @throws IllegalArgumentException 不支持的类型。
     */
    public NeteaseMsgPackWriter value(@Nullable Object value) {
        if (value == null) {
            out.write(0xc0);
        } else if (value instanceof CharSequence text) {
            string(text.toString());
        } else if (value instanceof Boolean bool) {
            out.write(bool ? 0xc3 : 0xc2);
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            integer(((Number) value).longValue());
        } else if (value instanceof Float || value instanceof Double) {
            out.write(0xcb);
            long bits = Double.doubleToLongBits(((Number) value).doubleValue());
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) (bits >>> shift));
            }
        } else if (value instanceof List<?> list) {
            arrayHeader(list.size());
            for (Object element : list) {
                value(element);
            }
        } else if (value instanceof Map<?, ?> map) {
            header(map.size(), 0x80, 0xde, 0xdf);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                string(String.valueOf(entry.getKey()));
                value(entry.getValue());
            }
        } else {
            throw new IllegalArgumentException("Cannot write " + value.getClass().getName() + " as MessagePack");
        }
        return this;
    }

    /**
     * 写入数组头，其后须再写 size 个值。
     */
    public NeteaseMsgPackWriter arrayHeader(int size) {
        header(size, 0x90, 0xdc, 0xdd);
        return this;
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    private void string(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0xff) {
            out.write(0xc4);
            out.write(bytes.length);
        } else if (bytes.length <= 0xffff) {
            out.write(0xc5);
            write16(bytes.length);
        } else {
            out.write(0xc6);
            write32(bytes.length);
        }
        out.writeBytes(bytes);
    }

    private void integer(long value) {
        if (value >= 0 && value < 128) {
            out.write((int) value);
        } else if (value < 0 && value >= -32) {
            out.write((int) (value & 0xff));
        } else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            out.write(0xd2);
            write32((int) value);
        } else {
            out.write(0xd3);
            write32((int) (value >>> 32));
            write32((int) value);
        }
    }

    private void header(int size, int fix, int sixteen, int thirtyTwo) {
        if (size < 16) {
            out.write(fix | size);
        } else if (size <= 0xffff) {
            out.write(sixteen);
            write16(size);
        } else {
            out.write(thirtyTwo);
            write32(size);
        }
    }

    private void write16(int value) {
        out.write(value >>> 8);
        out.write(value);
    }

    private void write32(int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }
}
