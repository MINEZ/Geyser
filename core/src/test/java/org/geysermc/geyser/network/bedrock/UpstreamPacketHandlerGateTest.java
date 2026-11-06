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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.login.LoginGate;
import org.geysermc.geyser.api.login.LoginGateResult;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.scoreboard.network.util.GeyserMockContext;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.GatedLoginImpl;
import org.geysermc.geyser.session.auth.NeteaseProfile;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.NeteaseLoginChain;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MINEZ：资源包下发完成后，没有登录闸门时照旧直接连接 Java 端；有闸门时先进入闸门，放行后再连接。
 */
class UpstreamPacketHandlerGateTest {
    private static final NeteaseProfile PROFILE = new NeteaseProfile("2147483648", "测试玩家", "NE2147483648", "1234567890",
        "obt", "pc", "windows", "1.21.130", "3.10.5.301327", "64", NeteaseLoginChain.NETEASE_ROOT_KEY, "sid", true, false);

    private static ResourcePackClientResponsePacket completed() {
        ResourcePackClientResponsePacket packet = new ResourcePackClientResponsePacket();
        packet.setStatus(ResourcePackClientResponsePacket.Status.COMPLETED);
        return packet;
    }

    private void run(AuthType authType, @Nullable LoginGate gate, Consumer<GeyserSession> test) {
        GeyserMockContext.mockContext(context -> {
            GeyserImpl geyser = context.mockOrSpy(GeyserImpl.class);
            GeyserConfig config = geyser.config();
            GeyserConfig.JavaConfig java = mock(GeyserConfig.JavaConfig.class);
            when(config.java()).thenReturn(java);
            when(java.authType()).thenReturn(authType);
            GeyserConfig.BedrockConfig bedrock = mock(GeyserConfig.BedrockConfig.class);
            GeyserConfig.NeteaseConfig netease = mock(GeyserConfig.NeteaseConfig.class);
            when(config.bedrock()).thenReturn(bedrock);
            when(bedrock.netease()).thenReturn(netease);
            // 默认要求载入 MINEZ 客户端模组；测试中没有下发它的包，因此不检查，与没有这项要求时相同。
            when(netease.requireClientMod()).thenReturn(true);
            when(geyser.loginGate()).thenReturn(gate);
            when(geyser.getPendingMicrosoftAuthentication()).thenReturn(mock(PendingMicrosoftAuthentication.class));

            GeyserSession session = mock(GeyserSession.class, RETURNS_DEEP_STUBS);
            when(session.getGeyser()).thenReturn(geyser);
            when(session.isClosed()).thenReturn(false);
            when(session.getUpstream().isClosed()).thenReturn(false);
            when(session.getNeteaseProfile()).thenReturn(PROFILE);
            when(session.getAuthData()).thenReturn(new AuthData("测试玩家", UUID.randomUUID(), "1234567890", 0, null));
            doAnswer(call -> {
                ((Runnable) call.getArgument(0)).run();
                return null;
            }).when(session).ensureInEventLoop(any());

            // 只用于最后那条日志，语言文件在测试中没有加载。
            try (MockedStatic<GeyserLocale> locale = mockStatic(GeyserLocale.class)) {
                new UpstreamPacketHandler(geyser, session).handle(completed());
            }
            test.accept(session);
        });
    }

    @Test
    void withoutAGateTheJavaServerIsJoinedAtOnce() {
        run(AuthType.OFFLINE, null, session -> {
            // 与 G0009 相同：网易玩家以 NeteaseLogin 算出的名字直接连接。
            verify(session).authenticate("NE2147483648");
            verify(session, never()).setGatedLogin(any());
        });
    }

    @Test
    void floodgateWithoutAGateIsUnchanged() {
        run(AuthType.FLOODGATE, null, session -> verify(session).authenticate("NE2147483648"));
    }

    @Test
    void withAGateTheJavaServerWaitsForIt() {
        AtomicReference<CompletableFuture<LoginGateResult>> result = new AtomicReference<>();
        AtomicReference<String> defaultName = new AtomicReference<>();
        LoginGate gate = login -> {
            defaultName.set(login.defaultUsername());
            result.set(new CompletableFuture<>());
            return result.get();
        };
        run(AuthType.OFFLINE, gate, session -> {
            assertNotNull(result.get(), "the gate was asked");
            assertEquals("NE2147483648", defaultName.get());
            verify(session).setGatedLogin(any(GatedLoginImpl.class));
            verify(session, never()).authenticate(anyString());
            verify(session, never()).prepareForConfigurationForm();

            result.get().complete(LoginGateResult.allow("张三"));
            verify(session).authenticate("张三");
        });
    }

    @Test
    void onlineAuthDoesNotUseTheGate() {
        AtomicReference<Boolean> asked = new AtomicReference<>(false);
        run(AuthType.ONLINE, login -> {
            asked.set(true);
            return new CompletableFuture<>();
        }, session -> {
            assertFalse(asked.get());
            verify(session).connect();
            verify(session, never()).setGatedLogin(any());
        });
    }
}
