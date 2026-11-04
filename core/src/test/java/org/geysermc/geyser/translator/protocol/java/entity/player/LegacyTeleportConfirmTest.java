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

package org.geysermc.geyser.translator.protocol.java.entity.player;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.level.physics.CollisionManager;
import org.geysermc.geyser.scoreboard.network.util.GeyserMockContext;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.UpstreamSession;
import org.geysermc.geyser.session.cache.TeleportCache;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockSetLocalPlayerAsInitializedTranslator;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.BedrockPlayerActionTranslator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MINEZ：1.21.130 的客户端在加载界面期间不理会发给自己的 MovePlayer。服务端连续两次把玩家传送到同一处时，
 * 1.21.130 的会话保留待确认的传送，客户端结束加载界面时立即重发；26.40 及以上照旧。
 */
class LegacyTeleportConfirmTest {
    private static final int LEGACY = Bedrock_v898.CODEC.getProtocolVersion();
    private static final int CURRENT = Bedrock_v2225.CODEC.getProtocolVersion();

    private final AtomicReference<TeleportCache> unconfirmed = new AtomicReference<>();
    private final AtomicReference<Vector3f> position = new AtomicReference<>(Vector3f.from(10.5, -60, -5.5));
    private GeyserSession session;
    private SessionPlayerEntity entity;

    /**
     * 在模拟的 Geyser 环境中运行（会话、玩家实体与碰撞管理的类在初始化时要用到 Geyser 的 API）。
     */
    private void run(boolean legacy, Runnable test) {
        GeyserMockContext.mockContext(context -> {
            setUp(context.mockOrSpy(GeyserImpl.class), legacy ? LEGACY : CURRENT);
            test.run();
        });
    }

    private void setUp(GeyserImpl geyser, int protocol) {
        session = mock(GeyserSession.class);
        entity = mock(SessionPlayerEntity.class);
        when(session.getGeyser()).thenReturn(geyser);
        CollisionManager collisionManager = mock(CollisionManager.class);
        when(collisionManager.adjustPositionForBedrock(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(session.getCollisionManager()).thenReturn(collisionManager);
        when(session.protocolVersion()).thenReturn(protocol);
        when(session.isLoggedIn()).thenReturn(true);
        when(session.isSpawned()).thenReturn(true);
        when(session.getPlayerEntity()).thenReturn(entity);
        when(session.getUnconfirmedTeleport()).thenAnswer(invocation -> unconfirmed.get());
        doAnswer(invocation -> {
            unconfirmed.set(invocation.getArgument(0));
            return null;
        }).when(session).setUnconfirmedTeleport(any());

        when(entity.position()).thenAnswer(invocation -> position.get());
        when(entity.getMotion()).thenReturn(Vector3f.ZERO);
        when(entity.getAttributes()).thenReturn(new HashMap<>());
        doAnswer(invocation -> {
            position.set(invocation.getArgument(0));
            return null;
        }).when(entity).moveAbsolute(any(Vector3f.class), anyFloat(), anyFloat(), anyBoolean(), anyBoolean());
    }

    private void teleport(int id, double x, double y, double z) {
        new JavaPlayerPositionTranslator().translate(session, new ClientboundPlayerPositionPacket(id, x, y, z, 0, 0, 0, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void secondTeleportToTheSamePosition(boolean legacy) {
        run(legacy, () -> {
            teleport(2, 300.5, -60, 300.5);
            assertEquals(2, unconfirmed.get().getTeleportConfirmId());

            // 第一次传送尚未被客户端确认，紧接着又传送到同一处（例如插件在进服时传送）。
            teleport(3, 300.5, -60, 300.5);
            if (legacy) {
                TeleportCache kept = unconfirmed.get();
                assertNotNull(kept, "1.21.130 keeps waiting for the client to arrive");
                assertEquals(3, kept.getTeleportConfirmId());
                assertEquals(Vector3f.from(300.5, -60, 300.5), kept.getPosition());
            } else {
                assertNull(unconfirmed.get(), "26.40 and later behave as upstream");
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void teleportToTheConfirmedPosition(boolean legacy) {
        run(legacy, () -> {
            // 没有待确认的传送时，传送到客户端已在的位置照旧不等确认（客户端会忽略这样的传送）。
            teleport(4, 10.5, -60, -5.5);
            assertNull(unconfirmed.get());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void resendWhenTheClientFinishesChangingDimension(boolean legacy) {
        run(legacy, () -> {
            teleport(2, 300.5, -60, 300.5);

            PlayerActionPacket done = new PlayerActionPacket();
            done.setAction(PlayerActionType.DIMENSION_CHANGE_SUCCESS);
            new BedrockPlayerActionTranslator().translate(session, done);
            verify(session, legacy ? times(1) : never()).resendUnconfirmedTeleport();

            // 没有待确认的传送时不重发。
            unconfirmed.set(null);
            new BedrockPlayerActionTranslator().translate(session, done);
            verify(session, legacy ? times(1) : never()).resendUnconfirmedTeleport();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void resendWhenTheClientFinishesLoading(boolean legacy) {
        run(legacy, () -> {
            UpstreamSession upstream = mock(UpstreamSession.class);
            when(session.getUpstream()).thenReturn(upstream);
            when(session.remoteServer()).thenReturn(mock(org.geysermc.geyser.api.network.RemoteServer.class));
            teleport(2, 300.5, -60, 300.5);
            // 只看重发，不走登录完成之后的流程。
            when(session.isLoggedIn()).thenReturn(false);

            SetLocalPlayerAsInitializedPacket initialized = new SetLocalPlayerAsInitializedPacket();
            initialized.setRuntimeEntityId(entity.geyserId());
            new BedrockSetLocalPlayerAsInitializedTranslator().translate(session, initialized);
            verify(session, legacy ? times(1) : never()).resendUnconfirmedTeleport();
        });
    }
}
