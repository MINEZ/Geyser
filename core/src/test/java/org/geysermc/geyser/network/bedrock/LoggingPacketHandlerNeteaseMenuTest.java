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

package org.geysermc.geyser.network.bedrock;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.event.FireResult;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.event.GeyserEventBus;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.geysermc.geyser.session.GeyserSession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MINEZ：中国版会话中 MINEZ 客户端模组的服务器菜单按钮替玩家执行 /menu：每秒最多一次，只在进入世界后响应，命名空间、系统名或事件名不符的不响应，
 * 国际版会话不响应，都不发出商城事件、不断开连接。
 */
class LoggingPacketHandlerNeteaseMenuTest {
    private static final BedrockCodec NETEASE = GameProtocol.getNeteaseCodec(898);
    private static final BedrockCodec VANILLA = GameProtocol.getBedrockCodec(898);

    private final GeyserImpl geyser = mock(GeyserImpl.class);
    private final GeyserLogger logger = mock(GeyserLogger.class);
    private final GeyserEventBus eventBus = mock(GeyserEventBus.class);
    private final GeyserSession session = mock(GeyserSession.class, RETURNS_DEEP_STUBS);

    private LoggingPacketHandler handler(BedrockCodec codec, boolean spawned) {
        when(geyser.getLogger()).thenReturn(logger);
        when(geyser.eventBus()).thenReturn(eventBus);
        when(eventBus.fire(any())).thenReturn(FireResult.ok());
        when(session.isClosed()).thenReturn(false);
        when(session.isSpawned()).thenReturn(spawned);
        when(session.getUpstream().getSession().getCodec()).thenReturn(codec);
        when(session.bedrockUsername()).thenReturn("测试玩家");
        // 测试中直接在当前线程执行。
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(session).ensureInEventLoop(any());
        return new LoggingPacketHandler(geyser, session);
    }

    /**
     * 按 PyRpc 的格式构造 ["ModEventC2S", [命名空间, 系统名, 事件名, {}], nil]，字符串写成二进制串。
     */
    private static NeteasePacket.PyRpc modEvent(String namespace, String systemName, String eventName) {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(0x93);
        bin(message, "ModEventC2S");
        message.write(0x94);
        bin(message, namespace);
        bin(message, systemName);
        bin(message, eventName);
        message.write(0x80);
        message.write(0xc0);
        byte[] payload = message.toByteArray();

        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(payload.length);
        packet.writeBytes(payload);
        packet.writeBytes(new byte[]{0x01, 0x00, 0x00, 0x00});
        NeteasePacket.PyRpc pyRpc = new NeteasePacket.PyRpc();
        pyRpc.payload(packet.toByteArray());
        return pyRpc;
    }

    private static NeteasePacket.PyRpc menu() {
        return modEvent("MinezClient", "MenuSystem", "OpenServerMenu");
    }

    private static NeteasePacket.PyRpc usingMod() {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(0x92);
        bin(message, "SyncUsingMod");
        message.write(0x91);
        bin(message, "MinezClient");
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(message.size());
        packet.writeBytes(message.toByteArray());
        packet.writeBytes(new byte[]{0x01, 0x00, 0x00, 0x00});
        NeteasePacket.PyRpc pyRpc = new NeteasePacket.PyRpc();
        pyRpc.payload(packet.toByteArray());
        return pyRpc;
    }

    private static void bin(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(0xc4);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }

    @Test
    void theMenuButtonRunsTheMenuCommandOncePerSecond() {
        LoggingPacketHandler handler = handler(NETEASE, true);
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(menu()));
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(menu()));
        verify(session, times(1)).sendCommand("menu");
        verify(eventBus, never()).fire(any());
        verify(session, never()).disconnect(anyString());
    }

    @Test
    void theMenuButtonWaitsUntilSpawned() {
        LoggingPacketHandler handler = handler(NETEASE, false);
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(menu()));
        verify(session, never()).sendCommand(anyString());
    }

    @Test
    void otherEventsDoNotOpenTheMenu() {
        LoggingPacketHandler handler = handler(NETEASE, true);
        handler.handlePacket(modEvent("Minecraft", "MenuSystem", "OpenServerMenu"));
        handler.handlePacket(modEvent("MinezClient", "Engine", "OpenServerMenu"));
        handler.handlePacket(modEvent("MinezClient", "MenuSystem", "OpenMenu"));
        verify(session, never()).sendCommand(anyString());
        verify(eventBus, never()).fire(any());
    }

    @Test
    void internationalSessionsDoNotOpenTheMenu() {
        LoggingPacketHandler handler = handler(VANILLA, true);
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(menu()));
        verify(session, never()).sendCommand(anyString());
    }

    @Test
    void usingModDiagnosticsAreDebugOnlyAndLimited() {
        LoggingPacketHandler handler = handler(NETEASE, false);
        handler.handlePacket(usingMod());
        verify(logger, never()).debug(contains("[MINEZ-PackDiag1]"));
        when(logger.isDebug()).thenReturn(true);
        for (int i = 0; i < 5; i++) {
            handler.handlePacket(usingMod());
        }
        verify(logger, times(2)).debug(contains("[MINEZ-PackDiag1] SyncUsingMod 来自 测试玩家: {\"value\":[\"MinezClient\"]}"));
        verify(session, never()).sendCommand(anyString());
        verify(session, never()).getMinezClientUi();
        verify(eventBus, never()).fire(any());
    }

    @Test
    void usingModDiagnosticsDoNotRunForInternationalSessions() {
        LoggingPacketHandler handler = handler(VANILLA, false);
        when(logger.isDebug()).thenReturn(true);
        handler.handlePacket(usingMod());
        verify(logger, never()).debug(contains("[MINEZ-PackDiag1]"));
    }
}
