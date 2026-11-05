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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * MINEZ：测试用的 MessagePack 写入器，按规范构造客户端发来的 {@code PyRpc}。
 * 字符串默认写成二进制串（Python 2 的 str 是字节串），{@link Str} 包起来的写成字符串。
 */
final class MsgPackWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /**
     * 写成 MessagePack 字符串类型（而不是二进制串）的字符串。
     */
    record Str(String value) {
    }

    /**
     * 原样写入的字节。
     */
    record Raw(byte[] bytes) {
    }

    static byte[] encode(Object value) {
        MsgPackWriter writer = new MsgPackWriter();
        writer.write(value);
        return writer.out.toByteArray();
    }

    /**
     * @return 以 VarInt 为长度前缀、其后是 4 个字节小端序消息 ID 的 PyRpc 数据包负载。
     */
    static byte[] packet(byte[] payload, long msgId) {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        long length = payload.length;
        do {
            int b = (int) (length & 0x7f);
            length >>>= 7;
            packet.write(length == 0 ? b : b | 0x80);
        } while (length != 0);
        packet.writeBytes(payload);
        packet.writeBytes(ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt((int) msgId).array());
        return packet.toByteArray();
    }

    static byte[] packet(Object message) {
        return packet(encode(message), 9753608L);
    }

    private void write(Object value) {
        switch (value) {
            case null -> out.write(0xc0);
            case Boolean b -> out.write(b ? 0xc3 : 0xc2);
            case Integer i -> writeLong(i);
            case Long l -> writeLong(l);
            case Double d -> {
                out.write(0xcb);
                out.writeBytes(ByteBuffer.allocate(8).putDouble(d).array());
            }
            case Float f -> {
                out.write(0xca);
                out.writeBytes(ByteBuffer.allocate(4).putFloat(f).array());
            }
            case Str s -> {
                byte[] bytes = s.value().getBytes(StandardCharsets.UTF_8);
                header(bytes.length, 0xa0, 31, 0xd9, 0xda, 0xdb);
                out.writeBytes(bytes);
            }
            case String s -> {
                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                header(bytes.length, -1, -1, 0xc4, 0xc5, 0xc6);
                out.writeBytes(bytes);
            }
            case Raw raw -> out.writeBytes(raw.bytes());
            case Object[] array -> {
                header(array.length, 0x90, 15, -1, 0xdc, 0xdd);
                for (Object element : array) {
                    write(element);
                }
            }
            case Map<?, ?> map -> {
                header(map.size(), 0x80, 15, -1, 0xde, 0xdf);
                map.forEach((k, v) -> {
                    write(k);
                    write(v);
                });
            }
            default -> throw new IllegalArgumentException(value.getClass().getName());
        }
    }

    private void writeLong(long value) {
        if (value >= 0 && value <= 0x7f) {
            out.write((int) value);
        } else if (value < 0 && value >= -32) {
            out.write((int) value & 0xff);
        } else if (value == (int) value) {
            out.write(0xd2);
            out.writeBytes(ByteBuffer.allocate(4).putInt((int) value).array());
        } else {
            out.write(0xd3);
            out.writeBytes(ByteBuffer.allocate(8).putLong(value).array());
        }
    }

    /**
     * 写入长度：能用 fix 格式（{@code fixMax} 以内）时用它，否则依次用 8、16、32 位长度的格式，-1 表示没有这种格式。
     */
    private void header(int size, int fix, int fixMax, int eight, int sixteen, int thirtyTwo) {
        if (fix >= 0 && size <= fixMax) {
            out.write(fix | size);
        } else if (eight >= 0 && size <= 0xff) {
            out.write(eight);
            out.write(size);
        } else if (size <= 0xffff) {
            out.write(sixteen);
            out.writeBytes(ByteBuffer.allocate(2).putShort((short) size).array());
        } else {
            out.write(thirtyTwo);
            out.writeBytes(ByteBuffer.allocate(4).putInt(size).array());
        }
    }
}
