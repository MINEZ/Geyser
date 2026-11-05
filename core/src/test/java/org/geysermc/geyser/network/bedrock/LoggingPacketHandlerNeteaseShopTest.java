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
import org.geysermc.geyser.api.event.bedrock.NeteaseShopEvent;
import org.geysermc.geyser.event.GeyserEventBus;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.geysermc.geyser.session.GeyserSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MINEZ：中国版会话发来的 PyRpc 与“购买成功”数据包中网易商城的通知发出 {@link NeteaseShopEvent}，其他的与坏数据只记 debug 日志，
 * 都不断开连接；国际版会话不发出事件。
 */
class LoggingPacketHandlerNeteaseShopTest {
    private static final BedrockCodec NETEASE = GameProtocol.getNeteaseCodec(898);
    private static final BedrockCodec VANILLA = GameProtocol.getBedrockCodec(898);

    private final GeyserImpl geyser = mock(GeyserImpl.class);
    private final GeyserLogger logger = mock(GeyserLogger.class);
    private final GeyserEventBus eventBus = mock(GeyserEventBus.class);
    private final GeyserSession session = mock(GeyserSession.class, RETURNS_DEEP_STUBS);

    private LoggingPacketHandler handler(BedrockCodec codec) {
        when(geyser.getLogger()).thenReturn(logger);
        when(geyser.eventBus()).thenReturn(eventBus);
        when(eventBus.fire(any())).thenReturn(FireResult.ok());
        when(session.isClosed()).thenReturn(false);
        when(session.getUpstream().getSession().getCodec()).thenReturn(codec);
        when(session.bedrockUsername()).thenReturn("测试玩家");
        return new LoggingPacketHandler(geyser, session);
    }

    /**
     * 按 PyRpc 的格式构造：以 VarInt 为长度前缀的 MessagePack，其后是小端序的消息 ID。
     * 消息为 ["ModEventC2S", [命名空间, 系统名, 事件名, {"playerId": "-2"}], nil]，字符串写成二进制串。
     */
    private static NeteasePacket.PyRpc modEvent(String eventName) {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        message.write(0x93);
        bin(message, "ModEventC2S");
        message.write(0x94);
        bin(message, "Minecraft");
        bin(message, "Engine");
        bin(message, eventName);
        message.write(0x81);
        bin(message, "playerId");
        bin(message, "-2");
        message.write(0xc0);
        byte[] payload = message.toByteArray();

        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(payload.length);
        packet.writeBytes(payload);
        packet.writeBytes(new byte[]{0x08, (byte) 0xd4, (byte) 0x94, 0x00});
        NeteasePacket.PyRpc pyRpc = new NeteasePacket.PyRpc();
        pyRpc.payload(packet.toByteArray());
        return pyRpc;
    }

    private static void bin(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.write(0xc4);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }

    private List<NeteaseShopEvent> fired(int count) {
        ArgumentCaptor<NeteaseShopEvent> events = ArgumentCaptor.forClass(NeteaseShopEvent.class);
        verify(eventBus, times(count)).fire(events.capture());
        return events.getAllValues();
    }

    @Test
    void shopEventsAreFired() {
        LoggingPacketHandler handler = handler(NETEASE);
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(modEvent("StoreBuySuccServerEvent")));
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(modEvent("UrgeShipEvent")));
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(new NeteasePacket.StoreBuySuccess()));

        List<NeteaseShopEvent> events = fired(3);
        assertEquals(NeteaseShopEvent.Kind.BUY_SUCCESS, events.get(0).kind());
        assertSame(session, events.get(0).connection());
        assertEquals("Minecraft", events.get(0).namespace());
        assertEquals("Engine", events.get(0).systemName());
        assertEquals(Map.of("playerId", "-2"), events.get(0).data());
        assertEquals(NeteaseShopEvent.Kind.URGE_SHIP, events.get(1).kind());
        assertEquals("UrgeShipEvent", events.get(1).eventName());
        assertEquals(NeteaseShopEvent.Kind.BUY_SUCCESS, events.get(2).kind());
        assertEquals("StoreBuySuccess", events.get(2).eventName());
        assertNull(events.get(2).namespace());
        assertEquals(Map.of(), events.get(2).data());
        verify(session, never()).disconnect(anyString());
    }

    @Test
    void otherPacketsAndBadDataAreOnlyLogged() {
        LoggingPacketHandler handler = handler(NETEASE);
        NeteasePacket.PyRpc garbage = new NeteasePacket.PyRpc();
        garbage.payload(new byte[]{0x05, (byte) 0xc1, 0x00, 0x00, 0x00, 0x00, 0x00});
        NeteasePacket.PyRpc oversized = new NeteasePacket.PyRpc();
        oversized.payload(new byte[]{(byte) 0x80, (byte) 0x80, 0x10});

        for (NeteasePacket packet : List.of(modEvent("OnButtonClick"), garbage, oversized, new NeteasePacket.PyRpc(),
            new NeteasePacket.NeteaseJson(), new NeteasePacket.SyncSkin())) {
            assertEquals(PacketSignal.HANDLED, handler.handlePacket(packet));
        }
        verify(eventBus, never()).fire(any());
        verify(session, never()).disconnect(anyString());
        verify(logger, times(6)).debug(anyString());
    }

    @Test
    void internationalSessionsFireNothing() {
        LoggingPacketHandler handler = handler(VANILLA);
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(modEvent("UrgeShipEvent")));
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(new NeteasePacket.StoreBuySuccess()));
        verify(eventBus, never()).fire(any());
    }

    @Test
    void aFailingListenerIsLogged() {
        LoggingPacketHandler handler = handler(NETEASE);
        RuntimeException failure = new RuntimeException("listener failed");
        when(eventBus.fire(any())).thenReturn(FireResult.resultFor(Map.of(mock(org.geysermc.event.subscribe.Subscriber.class), failure)));
        assertEquals(PacketSignal.HANDLED, handler.handlePacket(modEvent("UrgeShipEvent")));
        verify(logger).error(anyString(), org.mockito.ArgumentMatchers.eq(failure));
        verify(session, never()).disconnect(anyString());
    }
}
