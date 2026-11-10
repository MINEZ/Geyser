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
import org.geysermc.geyser.item.ModelIcons;
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
        SpriteGlyphs.setImages(Map.of());
        ModelIcons.set(Map.of());
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
            ModelIcons.set(Map.of("minez:dark_star_wings", Map.of("entity", "minez:icon_dark_star_wings", "scale", 1.0)));
            MinezClientUi ui = new MinezClientUi(session);

            // 模组就绪之前照旧，不另发。
            ui.beforeSend(text(TextPacket.Type.SYSTEM, "你好"));
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
            // 每个会话第一次就绪时请模组读出网易客户端的物品表，以下发的表中钻石的编号自检。
            Map<?, ?> itemTable = (Map<?, ?>) config.get("item_table");
            assertEquals(0L, ((Number) itemTable.get("from")).longValue());
            assertEquals("minecraft:diamond", ((List<?>) itemTable.get("check")).get(1));
            // 3D 物品图标的表随配置发出。
            Map<?, ?> icon = (Map<?, ?>) ((Map<?, ?>) config.get("icons")).get("minez:dark_star_wings");
            assertEquals("minez:icon_dark_star_wings", icon.get("entity"));

            // 聊天栏消息：照常发出，另发给模组；有发送者名字的按原版写成 <名字> 消息。
            ui.beforeSend(text(TextPacket.Type.SYSTEM, "§a起源开发组\uF1F1\uF1F2：大家好"));
            assertEquals("§a起源开发组\uF1F1\uF1F2：大家好", event(sent.get(1), "ChatMessage").get("text"));
            TextPacket chat = text(TextPacket.Type.CHAT, "你好");
            chat.setSourceName("Steve");
            ui.beforeSend(chat);
            assertEquals("<Steve> 你好", event(sent.get(2), "ChatMessage").get("text"));

            // 动作栏不在聊天栏中。
            ui.beforeSend(text(TextPacket.Type.TIP, "动作栏"));
            ui.beforeSend(text(TextPacket.Type.JUKEBOX_POPUP, "动作栏"));
            assertEquals(3, sent.size());

            // 复制到剪贴板：1.8.0 起的模组才能复制，更早的交给调用方另想办法。
            assertFalse(ui.copyToClipboard("邀请码 ABC"));
            assertEquals(3, sent.size());
        }
    }

    @Test
    void sendsOwnListName() throws Exception {
        try (MockedStatic<GameProtocol> protocol = mockStatic(GameProtocol.class)) {
            protocol.when(() -> GameProtocol.isNeteaseCodec(any())).thenReturn(true);
            SpriteGlyphs.setAnimated(Map.of("\uF800", Map.of("texture", "textures/minez/anim/item/title/builder", "tiles", 4L)));
            when(session.getPlayerEntity().getListName()).thenReturn("§b测试 §f\uF800");
            MinezClientUi ui = new MinezClientUi(session);

            // 模组就绪之前不发；就绪时随配置发一次，称号在第二行。
            ui.selfNameChanged();
            assertTrue(sent.isEmpty());
            ui.clientReady("1.5.0");
            assertEquals(2, sent.size());
            assertEquals("§b测试\n§f\uF800", event(sent.get(1), "PlayerName").get("text"));

            // 显示名改变后再发。
            when(session.getPlayerEntity().getListName()).thenReturn("§b测试");
            ui.selfNameChanged();
            assertEquals("§b测试", event(sent.get(2), "PlayerName").get("text"));
        }
    }

    @Test
    void copiesToClipboardWithNewMods() throws Exception {
        try (MockedStatic<GameProtocol> protocol = mockStatic(GameProtocol.class)) {
            protocol.when(() -> GameProtocol.isNeteaseCodec(any())).thenReturn(true);
            MinezClientUi ui = new MinezClientUi(session);
            assertFalse(ui.copyToClipboard("邀请码 ABC"));
            ui.clientReady("1.8.0");
            assertTrue(ui.copyToClipboard("邀请码 ABC"));
            assertEquals("邀请码 ABC", event(sent.get(sent.size() - 1), "Clipboard").get("text"));
        }
    }

    @Test
    void tellsNewModsAboutFlying() throws Exception {
        try (MockedStatic<GameProtocol> protocol = mockStatic(GameProtocol.class)) {
            protocol.when(() -> GameProtocol.isNeteaseCodec(any())).thenReturn(true);
            MinezClientUi ui = new MinezClientUi(session);
            // 模组就绪之前、或版本太旧时不发。
            ui.flyingChanged(true);
            ui.clientReady("1.11.0");
            int before = sent.size();
            ui.flyingChanged(true);
            assertEquals(before, sent.size());

            // 1.12.0 起发出；就绪时正在飞行的，随配置一并告知。
            when(session.isFlying()).thenReturn(true);
            ui.clientReady("1.12.0");
            assertEquals(1, ((Number) event(sent.get(sent.size() - 1), "Flying").get("flying")).intValue());
            ui.flyingChanged(false);
            assertEquals(0, ((Number) event(sent.get(sent.size() - 1), "Flying").get("flying")).intValue());
        }
    }

    @Test
    void showsWorldTextsWithIconsInTheMod() throws Exception {
        try (MockedStatic<GameProtocol> protocol = mockStatic(GameProtocol.class)) {
            protocol.when(() -> GameProtocol.isNeteaseCodec(any())).thenReturn(true);
            Map<String, Object> icon = Map.of("texture", "textures/minez/text_icons", "u", 0L, "v", 0L, "frame_width", 16L,
                "frame_height", 16L, "tiles", 1L);
            SpriteGlyphs.setImages(Map.of("\uF100", icon));
            MinezClientUi ui = new MinezClientUi(session);

            // 模组就绪之前、或版本太旧时照常以名牌显示。
            assertFalse(ui.showWorldText(1, "§f\uF100\n§7降落中"));
            ui.clientReady("1.8.0");
            assertFalse(((Map<?, ?>) event(sent.get(0), "ClientConfig")).containsKey("images"));
            assertFalse(ui.showWorldText(1, "§f\uF100\n§7降落中"));

            // 1.9.0 起随配置发出图标的表；有图标的文字交给模组，没有图标的仍以名牌显示。
            ui.clientReady("1.9.0");
            Map<?, ?> images = (Map<?, ?>) event(sent.get(sent.size() - 1), "ClientConfig").get("images");
            assertEquals("textures/minez/text_icons", ((Map<?, ?>) images.get("\uF100")).get("texture"));
            int before = sent.size();
            assertFalse(ui.showWorldText(1, "§f路径点"));
            assertEquals(before, sent.size());
            assertTrue(ui.showWorldText(1, "§f\uF100\n§7降落中"));
            Map<?, ?> shown = event(sent.get(before), "WorldText");
            assertEquals(1L, ((Number) shown.get("id")).longValue());
            assertEquals("§f\uF100\n§7降落中", shown.get("text"));
            // 文字没有变化时不重发；撤下时只发编号；文字中不再有图标时也撤下。
            assertTrue(ui.showWorldText(1, "§f\uF100\n§7降落中"));
            assertEquals(before + 1, sent.size());
            assertFalse(ui.showWorldText(1, "§f路径点"));
            assertFalse(event(sent.get(before + 1), "WorldText").containsKey("text"));
            ui.hideWorldText(1);
            assertEquals(before + 2, sent.size());
        }
    }

    @Test
    void internationalSessionsAreUntouched() {
        try (MockedStatic<GameProtocol> protocol = mockStatic(GameProtocol.class)) {
            protocol.when(() -> GameProtocol.isNeteaseCodec(any())).thenReturn(false);
            MinezClientUi ui = new MinezClientUi(session);
            ui.clientReady("1.4.0");
            assertFalse(ui.isReady());
            ui.beforeSend(text(TextPacket.Type.SYSTEM, "你好"));
            assertTrue(sent.isEmpty());
        }
    }
}
