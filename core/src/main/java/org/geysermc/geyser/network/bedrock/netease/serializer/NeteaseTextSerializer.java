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

/*
 * MINEZ：本文件改写自 CloudburstMC Protocol（https://github.com/CloudburstMC/Protocol）中的 TextSerializer_v898
 * 等序列化器，按网易中国版客户端的数据包格式修改了部分字段。CloudburstMC Protocol 以 Apache License 2.0 授权，
 * Copyright 2020 NukkitX，许可证全文见本仓库的
 * core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt，构建产物中也附带一份。
 */

package org.geysermc.geyser.network.bedrock.netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.TextSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.util.TextConverter;

/**
 * MINEZ：网易 898 的 TextPacket。
 * <ul>
 *     <li>“作者与消息”一类（聊天、私聊、公告）在消息之后、XUID 之前多一个参数列表（VarInt 数量 + 字符串），其余两类与原版相同；</li>
 *     <li>网易客户端在“名字 + 消息”达到 512 个字符时会判定数据包违规并断开，发出前把消息截短到 511 个字符以内。</li>
 * </ul>
 */
public class NeteaseTextSerializer extends TextSerializer_v898 {
    public static final NeteaseTextSerializer INSTANCE = new NeteaseTextSerializer();

    /**
     * “名字 + 消息”的长度须小于此值。
     */
    static final int MAX_NAME_AND_MESSAGE = 512;

    private static final int MESSAGE_ONLY = 0;
    private static final int AUTHOR_AND_MESSAGE = 1;

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
        TextPacket.Type type = packet.getType();
        switch (type) {
            case RAW, TIP, SYSTEM, JSON, WHISPER_JSON, ANNOUNCEMENT_JSON -> {
                TextConverter converter = helper.getTextConverter();
                CharSequence message = packet.getMessage(CharSequence.class);
                writeNeedsTranslation(buffer, converter, message, packet);
                buffer.writeByte(MESSAGE_ONLY);
                helper.writeString(buffer, "raw");
                helper.writeString(buffer, "tip");
                helper.writeString(buffer, "systemMessage");
                helper.writeString(buffer, "textObjectWhisper");
                helper.writeString(buffer, "textObjectAnnouncement");
                helper.writeString(buffer, "textObject");
                buffer.writeByte(type.ordinal());
                boolean json = type == TextPacket.Type.JSON || type == TextPacket.Type.WHISPER_JSON || type == TextPacket.Type.ANNOUNCEMENT_JSON;
                String text = json ? converter.serializeJson(message) : converter.serialize(message);
                helper.writeString(buffer, clamp("", text));
                writeSuffix(buffer, helper, converter, packet);
            }
            case CHAT, WHISPER, ANNOUNCEMENT -> {
                TextConverter converter = helper.getTextConverter();
                CharSequence message = packet.getMessage(CharSequence.class);
                writeNeedsTranslation(buffer, converter, message, packet);
                buffer.writeByte(AUTHOR_AND_MESSAGE);
                helper.writeString(buffer, "chat");
                helper.writeString(buffer, "whisper");
                helper.writeString(buffer, "announcement");
                buffer.writeByte(type.ordinal());
                String sourceName = packet.getSourceName() == null ? "" : packet.getSourceName();
                helper.writeString(buffer, sourceName);
                helper.writeString(buffer, clamp(sourceName, converter.serialize(message)));
                helper.writeArray(buffer, packet.getParameters(), helper::writeString);
                writeSuffix(buffer, helper, converter, packet);
            }
            // 带参数的翻译、弹出提示与唱片提示，与原版相同。
            default -> super.serialize(buffer, helper, packet);
        }
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
        int start = buffer.readerIndex();
        boolean needsTranslation = buffer.readBoolean();
        if (buffer.readUnsignedByte() != AUTHOR_AND_MESSAGE) {
            buffer.readerIndex(start);
            super.deserialize(buffer, helper, packet);
            return;
        }

        TextConverter converter = helper.getTextConverter();
        packet.setNeedsTranslation(needsTranslation);
        for (int i = 0; i < 3; i++) {
            helper.readString(buffer); // 三个分类名。
        }
        packet.setType(TextPacket.Type.values()[buffer.readUnsignedByte()]);
        packet.setSourceName(helper.readString(buffer));
        packet.setMessage(converter.deserialize(helper.readString(buffer), needsTranslation));
        helper.readArray(buffer, packet.getParameters(), helper::readString);
        packet.setXuid(helper.readString(buffer));
        packet.setPlatformChatId(helper.readString(buffer));
        String filtered = helper.readOptional(buffer, "", (buf, codecHelper) -> codecHelper.readString(buf));
        packet.setFilteredMessage(converter.deserialize(filtered, needsTranslation));
    }

    private static void writeNeedsTranslation(ByteBuf buffer, TextConverter converter, CharSequence message, TextPacket packet) {
        Boolean needsTranslation = converter.needsTranslation(message);
        buffer.writeBoolean(needsTranslation != null ? needsTranslation : packet.isNeedsTranslation());
    }

    private static void writeSuffix(ByteBuf buffer, BedrockCodecHelper helper, TextConverter converter, TextPacket packet) {
        helper.writeString(buffer, packet.getXuid());
        helper.writeString(buffer, packet.getPlatformChatId());
        String filtered = converter.serialize(packet.getFilteredMessage(CharSequence.class));
        helper.writeOptional(buffer, s -> !s.isEmpty(), filtered, (buf, codecHelper, s) -> codecHelper.writeString(buf, s));
    }

    /**
     * 让“名字 + 消息”短于 {@link #MAX_NAME_AND_MESSAGE} 个字符，不拆开代理对。空消息照原版写成一个空格。
     */
    static String clamp(String sourceName, String text) {
        if (text.isEmpty()) {
            return " ";
        }
        int allowed = MAX_NAME_AND_MESSAGE - 1 - Math.min(sourceName.length(), MAX_NAME_AND_MESSAGE - 1);
        if (text.length() <= allowed) {
            return text;
        }
        int end = allowed;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return end == 0 ? " " : text.substring(0, end);
    }
}
