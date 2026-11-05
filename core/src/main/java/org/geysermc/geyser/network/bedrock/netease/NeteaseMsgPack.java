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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MINEZ：读取 {@code PyRpc} 负载的 MessagePack，只读不写，按 MessagePack 规范自行实现。
 * <p>
 * 类型按网易文档《Spigot 服与客户端 python 通信原理简介》中 Python 发往 Java 的对应关系转换：nil 为 null，布尔为 {@link Boolean}，
 * 整数在 int 范围内为 {@link Integer}、在 long 范围内为 {@link Long}、更大的无符号数为 {@link BigInteger}，浮点数为 {@link Double}，
 * 字符串与二进制串都按 UTF-8 转为 {@link String}（Python 2 的 str 是字节串，可能编码为二进制串），数组为只读的 {@link List}，
 * 映射为只读的 {@link Map}，键都转为字符串。扩展类型与无效的字节一律视为格式错误。
 * <p>
 * 数据来自客户端，读取有限制：嵌套的深度、元素的总数、字符串的长度都有上限，长度字段大于剩余的字节时立即失败，
 * 不会按长度字段预先分配内存。整个负载必须恰好是一个值，后面不能有多余的字节。
 */
public final class NeteaseMsgPack {
    /**
     * 数组与映射嵌套的最大深度，根为第 1 层。
     */
    public static final int MAX_DEPTH = 16;
    /**
     * 数组的元素与映射的键、值合计的最大个数。
     */
    public static final int MAX_ELEMENTS = 4096;

    private final ByteBuffer buffer;
    private int elements;

    private NeteaseMsgPack(byte[] data) {
        this.buffer = ByteBuffer.wrap(data);
    }

    /**
     * 读取恰好一个值。
     *
     * @param data MessagePack 数据。
     * @return 读出的值，可能为 null（nil）。
     * @throws FormatException 格式不符、超出限制或末尾有多余的字节。
     */
    public static Object read(byte[] data) throws FormatException {
        NeteaseMsgPack reader = new NeteaseMsgPack(data);
        Object value = reader.value(1);
        if (reader.buffer.hasRemaining()) {
            throw new FormatException(reader.buffer.remaining() + " trailing bytes");
        }
        return value;
    }

    private Object value(int depth) throws FormatException {
        int b = u8();
        if (b <= 0x7f) {
            return b;
        }
        if (b >= 0xe0) {
            return (int) (byte) b;
        }
        if (b <= 0x8f) {
            return map(b & 0x0f, depth);
        }
        if (b <= 0x9f) {
            return array(b & 0x0f, depth);
        }
        if (b <= 0xbf) {
            return string(b & 0x1f);
        }
        return switch (b) {
            case 0xc0 -> null;
            case 0xc2 -> Boolean.FALSE;
            case 0xc3 -> Boolean.TRUE;
            case 0xc4 -> string(u8());
            case 0xc5 -> string(u16());
            case 0xc6 -> string(length32());
            case 0xca -> (double) Float.intBitsToFloat(bytes(4).getInt());
            case 0xcb -> bytes(8).getDouble();
            case 0xcc -> u8();
            case 0xcd -> u16();
            case 0xce -> integer(Integer.toUnsignedLong(bytes(4).getInt()));
            case 0xcf -> {
                long value = bytes(8).getLong();
                yield value >= 0 ? integer(value) : new BigInteger(Long.toUnsignedString(value));
            }
            case 0xd0 -> (int) bytes(1).get();
            case 0xd1 -> (int) bytes(2).getShort();
            case 0xd2 -> bytes(4).getInt();
            case 0xd3 -> integer(bytes(8).getLong());
            case 0xd9 -> string(u8());
            case 0xda -> string(u16());
            case 0xdb -> string(length32());
            case 0xdc -> array(u16(), depth);
            case 0xdd -> array(length32(), depth);
            case 0xde -> map(u16(), depth);
            case 0xdf -> map(length32(), depth);
            // 0xc1 不使用；0xc7~0xc9、0xd4~0xd8 为扩展类型，Python 的数据中不会出现。
            default -> throw new FormatException(String.format("unsupported type 0x%02x", b));
        };
    }

    private static Object integer(long value) {
        return value == (int) value ? (Object) (int) value : (Object) value;
    }

    private List<Object> array(int size, int depth) throws FormatException {
        enter(size, depth);
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(value(depth + 1));
        }
        return Collections.unmodifiableList(list);
    }

    private Map<String, Object> map(int size, int depth) throws FormatException {
        // 每个键值对至少两个字节。
        if (size > buffer.remaining() / 2) {
            throw new FormatException("map of " + size + " entries exceeds the data");
        }
        enter(size * 2, depth);
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            Object key = value(depth + 1);
            if (key instanceof Map || key instanceof List || key == null) {
                throw new FormatException("map key is not a scalar");
            }
            map.put(String.valueOf(key), value(depth + 1));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * 进入一层数组或映射前检查深度、元素总数与剩余的字节（每个元素至少一个字节）。
     */
    private void enter(int elementCount, int depth) throws FormatException {
        if (depth > MAX_DEPTH) {
            throw new FormatException("nested deeper than " + MAX_DEPTH);
        }
        if (elementCount > buffer.remaining()) {
            throw new FormatException(elementCount + " elements exceed the data");
        }
        elements += elementCount;
        if (elements > MAX_ELEMENTS) {
            throw new FormatException("more than " + MAX_ELEMENTS + " elements");
        }
    }

    private String string(int length) throws FormatException {
        ByteBuffer bytes = bytes(length);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(bytes)
                .toString();
        } catch (CharacterCodingException e) {
            throw new FormatException("string is not UTF-8");
        }
    }

    private int u8() throws FormatException {
        return Byte.toUnsignedInt(bytes(1).get());
    }

    private int u16() throws FormatException {
        return Short.toUnsignedInt(bytes(2).getShort());
    }

    private int length32() throws FormatException {
        int length = bytes(4).getInt();
        if (length < 0) {
            throw new FormatException("length " + Integer.toUnsignedString(length) + " exceeds the data");
        }
        return length;
    }

    /**
     * 取出接下来的若干字节，不足时失败。返回的缓冲区为大端序，与 MessagePack 一致。
     */
    private ByteBuffer bytes(int length) throws FormatException {
        if (length > buffer.remaining()) {
            throw new FormatException("needs " + length + " bytes, " + buffer.remaining() + " left");
        }
        ByteBuffer slice = buffer.slice(buffer.position(), length);
        buffer.position(buffer.position() + length);
        return slice;
    }

    /**
     * 数据不是格式正确的 MessagePack，或超出了读取的限制。
     */
    public static final class FormatException extends Exception {
        public FormatException(String message) {
            super(message, null, false, false);
        }
    }
}
