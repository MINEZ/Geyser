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

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.login.LoginGate;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.network.bedrock.netease.MinezClientMod;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.GatedLoginImpl;
import org.geysermc.geyser.session.auth.NeteaseProfile;
import org.geysermc.geyser.text.GeyserLocale;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpstreamPacketHandlerPlatformPacksTest {
    private static final String MOD = "a858dec6-447c-46c8-ae1f-dd384ae6dd27";
    private static final String BEHAVIOR = "03529531-08fb-4f27-b402-01d1fe545883";
    private static final String OTHER = "2254393d-8430-45b0-838a-bd397828c765";

    private void run(String env, boolean malformed, BiConsumer<UpstreamPacketHandler, Fixture> test) {
        run(env, malformed, "", test);
    }

    private void run(String env, boolean malformed, String probeUserId, BiConsumer<UpstreamPacketHandler, Fixture> test) {
        GeyserImpl geyser = mock(GeyserImpl.class, RETURNS_DEEP_STUBS);
        GeyserSession session = mock(GeyserSession.class, RETURNS_DEEP_STUBS);
        when(session.getNeteaseProfile()).thenReturn(new NeteaseProfile("1", "test", "NE1", "1", env,
            "pe", "android", "3.10", "3.10", "64", "", null, true, false));
        when(session.bedrockUsername()).thenReturn("test");
        when(session.getGeyser()).thenReturn(geyser);
        when(geyser.config().java().authType()).thenReturn(AuthType.OFFLINE);
        when(geyser.config().bedrock().netease().requireClientMod()).thenReturn(true);
        when(geyser.config().bedrock().netease().platformResourcePacks()).thenReturn(List.of(malformed ? "bad" : MOD + "_1.18.6"));
        when(geyser.config().bedrock().netease().platformBehaviorPacks()).thenReturn(List.of(BEHAVIOR + "_1.18.6"));
        when(geyser.config().bedrock().netease().platformResourcePackProbeUserId()).thenReturn(probeUserId);
        LoginGate gate = mock(LoginGate.class);
        when(geyser.loginGate()).thenReturn(gate);
        when(geyser.getBootstrap().getConfigFolder()).thenReturn(java.nio.file.Path.of("build", "test-platform-cache"));
        List<BedrockPacket> packets = new ArrayList<>();
        doAnswer(call -> { packets.add(call.getArgument(0)); return null; }).when(session).sendUpstreamPacket(any());
        Map<UUID, String> offered = new LinkedHashMap<>();
        offered.put(UUID.fromString(MOD), "1.18.5");
        offered.put(UUID.fromString(OTHER), "1.1.5");
        try (var impl = mockStatic(GeyserImpl.class);
             var locale = mockStatic(GeyserLocale.class)) {
            impl.when(GeyserImpl::getInstance).thenReturn(geyser);
            try (var construction = mockConstruction(SessionLoadResourcePacksEventImpl.class, (event, context) -> {
                when(event.unregister(any())).thenAnswer(call -> offered.remove(call.getArgument(0)) != null);
                when(event.orderedPacks()).thenAnswer(call -> offered.entrySet().stream()
                    .map(e -> new ResourcePackStackPacket.Entry(e.getKey().toString(), e.getValue(), "")).toList());
                when(event.infoPacketEntries()).thenAnswer(call -> offered.entrySet().stream()
                    .map(e -> new ResourcePacksInfoPacket.Entry(e.getKey(), e.getValue(), 1, "", "", "", false, false, false, "")).toList());
            })) {
                var handler = new UpstreamPacketHandler(geyser, session);
                handler.sendResourcePacksInfo();
                test.accept(handler, new Fixture(session, gate, packets, offered));
            }
        }
    }

    private record Fixture(GeyserSession session, LoginGate gate, List<BedrockPacket> packets, Map<UUID, String> offered) { }

    private static ResourcePackClientResponsePacket response(ResourcePackClientResponsePacket.Status status) {
        var packet = new ResourcePackClientResponsePacket();
        packet.setStatus(status);
        return packet;
    }

    @Test
    void releaseSessionActivatesPlatformPacksAndKeepsTheReadyGate() {
        run("obt", false, (handler, f) -> {
            var info = (ResourcePacksInfoPacket) f.packets().getFirst();
            assertEquals(List.of(UUID.fromString(OTHER)), info.getResourcePackInfos().stream().map(ResourcePacksInfoPacket.Entry::getPackId).toList());
            assertTrue(info.isScriptingEnabled());
            assertTrue(info.isHasAddonPacks());
            assertFalse(f.offered().containsKey(UUID.fromString(MOD)));
            handler.handle(response(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS));
            var stack = (ResourcePackStackPacket) f.packets().getLast();
            assertEquals("1.18.6", stack.getResourcePacks().getFirst().getPackVersion());
            assertEquals(BEHAVIOR, stack.getBehaviorPacks().getFirst().getPackId());
            try (var gated = mockStatic(GatedLoginImpl.class)) {
                handler.handle(response(ResourcePackClientResponsePacket.Status.COMPLETED));
                gated.verify(() -> GatedLoginImpl.start(f.session(), f.gate(), "NE1", new MinezClientMod.Version(1, 18, 6)));
            }
            verify(f.session(), never()).authenticate(anyString());
        });
    }

    @Test
    void developmentSessionKeepsItsServerDownloadAndOriginalStack() {
        run("expr1", false, (handler, f) -> {
            var info = (ResourcePacksInfoPacket) f.packets().getFirst();
            assertEquals(2, info.getResourcePackInfos().size());
            assertFalse(info.isScriptingEnabled());
            handler.handle(response(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS));
            var stack = (ResourcePackStackPacket) f.packets().getLast();
            assertEquals("1.18.5", stack.getResourcePacks().getFirst().getPackVersion());
            assertTrue(stack.getBehaviorPacks().isEmpty());
        });
    }

    @Test
    void invalidPlatformConfigurationStopsBeforeOfferingAnyPack() {
        run("obt", true, (handler, f) -> {
            assertTrue(f.packets().isEmpty());
            verify(f.session()).disconnect("服务器的平台资源包配置有误，请联系服主。");
        });
    }

    @Test
    void platformPackRequestIsNotServedFromTheOldServerCopy() {
        run("obt", false, (handler, f) -> {
            var request = response(ResourcePackClientResponsePacket.Status.SEND_PACKS);
            request.getPackIds().add(MOD + "_1.18.5");
            handler.handle(request);
            assertEquals(1, f.packets().size());
            verify(f.session()).disconnect("平台资源包未就绪，请退出网络游戏并从大厅重新进入以更新资源。");
        });
    }

    @Test
    void probeAdvertisesOnlyResourceMetadataAndStillRequiresReady() {
        run("obt", false, "1", (handler, f) -> {
            var info = (ResourcePacksInfoPacket) f.packets().getFirst();
            assertEquals(List.of(UUID.fromString(OTHER), UUID.fromString(MOD)),
                info.getResourcePackInfos().stream().map(ResourcePacksInfoPacket.Entry::getPackId).toList());
            assertEquals(0, info.getResourcePackInfos().getLast().getPackSize());
            assertFalse(f.offered().containsKey(UUID.fromString(MOD)));
            handler.handle(response(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS));
            assertInstanceOf(ResourcePackStackPacket.class, f.packets().getLast());
            try (var gated = mockStatic(GatedLoginImpl.class)) {
                handler.handle(response(ResourcePackClientResponsePacket.Status.COMPLETED));
                gated.verify(() -> GatedLoginImpl.start(f.session(), f.gate(), "NE1", new MinezClientMod.Version(1, 18, 6)));
            }
            verify(f.session(), never()).authenticate(anyString());
        });
    }

    @Test
    void probeDoesNotAffectAnotherPlayerOrDevelopmentEnvironment() {
        run("obt", false, "2", (handler, f) -> {
            var info = (ResourcePacksInfoPacket) f.packets().getFirst();
            assertEquals(List.of(UUID.fromString(OTHER)),
                info.getResourcePackInfos().stream().map(ResourcePacksInfoPacket.Entry::getPackId).toList());
        });
        run("expr1", false, "1", (handler, f) -> {
            var info = (ResourcePacksInfoPacket) f.packets().getFirst();
            assertEquals("1.18.5", info.getResourcePackInfos().getFirst().getPackVersion());
        });
    }

    @Test
    void mixedDownloadRequestIsRejectedBeforeSendingEvenAnOrdinaryPack() {
        run("obt", false, "1", (handler, f) -> {
            var request = response(ResourcePackClientResponsePacket.Status.SEND_PACKS);
            request.getPackIds().add(OTHER + "_1.1.5");
            request.getPackIds().add(MOD + "_1.18.6");
            request.getPackIds().add(BEHAVIOR + "_1.18.6");
            handler.handle(request);
            assertEquals(1, f.packets().size());
            verify(f.session()).disconnect("平台资源包诊断：客户端请求下载预装包，请将本次服务器日志发给服主。");
        });
    }
}
