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

package org.geysermc.geyser.session;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.geysermc.geyser.network.bedrock.netease.NeteasePyRpc;
import org.geysermc.geyser.text.SpriteGlyphs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * MINEZ：模组就绪后转发聊天栏消息、预览标记改为预览框、模组没有就绪或不是中国版时照旧。
 */
class MinezClientUiTest {
    private final GeyserSession session = mock(GeyserSession.class, RETURNS_DEEP_STUBS);
    private final List<BedrockPacket> sent = new ArrayList<>();

    MinezClientUiTest() {
        doAnswer(call -> sent.add(call.getArgument(0))).when(session.getUpstream()).sendPacket(any());
        when(session.bedrockUsername()).thenReturn("测试玩家");
    }

    @AfterEach
    void clear() {
        SpriteGlyphs.setAnimated(Map.of());
    }

    private static TextPacket text(TextPacket.Type type, String message) {
        TextPacket packet = new TextPacket();
        packet.setType(type);
        packet.setMessage(message);
        packet.setSourceName("");
        return packet;
    }

    private static Map<?, ?> event(BedrockPacket packet, String name) throws Exception {
        NeteasePyRpc.Message message = NeteasePyRpc.decode(((NeteasePacket.PyRpc) packet).payload());
        List<?> arguments = (List<?>) message.data().get("value");
        assertEquals(List.of("MinezClient", "MenuSystem", name), arguments.subList(0, 3));
        return (Map<?, ?>) arguments.get(3);
    }

    @Test
    void forwardsChatOnceTheModIsReady() throws Exception {
        try (MockedStatic<GameProtocol> protocol = mockStatic(GameProtocol.class)) {
            protocol.when(() -> GameProtocol.isNeteaseCodec(any())).thenReturn(true);
            SpriteGlyphs.setAnimated(Map.of("\uF1F1\uF1F2", Map.of("texture", "textures/minez/anim/item.title.builder", "frames", 8L)));
            MinezClientUi ui = new MinezClientUi(session);

            // 模组就绪之前照旧，不另发。
            assertFalse(ui.beforeSend(text(TextPacket.Type.SYSTEM, "你好")));
            assertTrue(sent.isEmpty());

            // 1.4.0 之前的模组不会画聊天栏，照旧。
            ui.clientReady("1.3.0");
            assertFalse(ui.isReady());
            assertTrue(sent.isEmpty());
            ui.clientReady("1.4.0");
            assertTrue(ui.isReady());
            Map<?, ?> config = event(sent.get(0), "ClientConfig");
            assertEquals(1L, ((Number) config.get("chat")).longValue());
            Map<?, ?> sprite = (Map<?, ?>) ((Map<?, ?>) config.get("sprites")).get("\uF1F1\uF1F2");
            assertEquals("textures/minez/anim/item.title.builder", sprite.get("texture"));
            assertEquals(8L, ((Number) sprite.get("frames")).longValue());

            // 聊天栏消息：照常发出，另发给模组；有发送者名字的按原版写成 <名字> 消息。
            assertFalse(ui.beforeSend(text(TextPacket.Type.SYSTEM, "§a起源开发组\uF1F1\uF1F2：大家好")));
            assertEquals("§a起源开发组\uF1F1\uF1F2：大家好", event(sent.get(1), "ChatMessage").get("text"));
            TextPacket chat = text(TextPacket.Type.CHAT, "你好");
            chat.setSourceName("Steve");
            assertFalse(ui.beforeSend(chat));
            assertEquals("<Steve> 你好", event(sent.get(2), "ChatMessage").get("text"));

            // 动作栏不在聊天栏中。
            assertFalse(ui.beforeSend(text(TextPacket.Type.TIP, "动作栏")));
            assertFalse(ui.beforeSend(text(TextPacket.Type.JUKEBOX_POPUP, "动作栏")));
            assertEquals(3, sent.size());

            // 预览：开头（颜色代码之后）为标记的系统消息不显示在聊天栏中，改为预览框，去掉标记。
            assertTrue(ui.beforeSend(text(TextPacket.Type.SYSTEM, "§r§f" + MinezClientUi.PREVIEW_MARKER + "§7佩戴效果：Steve\uF1F1\uF1F2")));
            assertEquals("§r§f§7佩戴效果：Steve\uF1F1\uF1F2", event(sent.get(3), "Preview").get("text"));
            // 标记不在开头、或不是系统消息（例如玩家在聊天中打出这个字符）时照常显示。
            assertFalse(ui.beforeSend(text(TextPacket.Type.SYSTEM, "Steve：" + MinezClientUi.PREVIEW_MARKER)));
            assertFalse(ui.beforeSend(text(TextPacket.Type.CHAT, MinezClientUi.PREVIEW_MARKER + "你好")));
            assertEquals(6, sent.size());
            assertEquals("Steve：" + MinezClientUi.PREVIEW_MARKER, event(sent.get(4), "ChatMessage").get("text"));
        }
    }

    @Test
    void internationalSessionsAreUntouched() {
        try (MockedStatic<GameProtocol> protocol = mockStatic(GameProtocol.class)) {
            protocol.when(() -> GameProtocol.isNeteaseCodec(any())).thenReturn(false);
            MinezClientUi ui = new MinezClientUi(session);
            ui.clientReady("1.4.0");
            assertFalse(ui.isReady());
            assertFalse(ui.beforeSend(text(TextPacket.Type.SYSTEM, MinezClientUi.PREVIEW_MARKER + "预览")));
            assertTrue(sent.isEmpty());
        }
    }
}
