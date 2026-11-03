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

package org.geysermc.geyser.util;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.netty.codec.FrameIdCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionEncoder;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.network.bedrock.netease.NeteaseClients;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.session.auth.NeteaseProfile;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MINEZ：网易客户端的登录流程：验证通过的记下身份并开始加密握手，不是网易客户端的按配置拒绝或交回上游。
 */
class NeteaseLoginTest {
    private final NeteaseTestChains chains = new NeteaseTestChains();
    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final GeyserSession session = mock(GeyserSession.class, RETURNS_DEEP_STUBS);

    NeteaseLoginTest() {
        // 代替 RakNet 的帧编解码器，加密的处理器加在它后面
        channel.pipeline().addLast(FrameIdCodec.NAME, new ChannelInboundHandlerAdapter());
        when(session.getUpstream().getSession().getPeer().getChannel()).thenReturn(channel);
        when(session.getUpstream().getProtocolVersion()).thenReturn(898);
        when(session.getUpstream().getSession().getCodec()).thenReturn(GameProtocol.getNeteaseCodec(898));
        when(session.getGeyser().config().debugMode()).thenReturn(true);
    }

    private void configure(boolean onlyNetease, String... trustedRootKeys) {
        when(session.getGeyser().config().bedrock().netease().onlyNeteaseClients()).thenReturn(onlyNetease);
        when(session.getGeyser().config().bedrock().netease().trustedRootKeys()).thenReturn(List.of(trustedRootKeys));
    }

    private String clientData() {
        return chains.clientData(Map.of("GameVersion", "1.21.130", "LanguageCode", "zh_CN", "DeviceOS", 7));
    }

    @Test
    void neteaseClientLogsInAndStartsTheHandshake() throws Exception {
        configure(true, NeteaseTestChains.encoded(chains.root));
        NeteaseClients.markRaknet8(channel);

        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.chain(), AuthType.FULL), clientData());

        assertEquals(NeteaseLogin.Outcome.AWAITING_HANDSHAKE, outcome);
        verify(session, never()).disconnect(anyString());

        ArgumentCaptor<NeteaseProfile> profile = ArgumentCaptor.forClass(NeteaseProfile.class);
        verify(session).setNeteaseProfile(profile.capture());
        assertEquals("2147483648", profile.getValue().uid());
        assertEquals("测试玩家", profile.getValue().displayName());
        assertEquals("NE2147483648", profile.getValue().javaUsername());
        assertEquals("development", profile.getValue().env());
        assertEquals("3.10.5.301327", profile.getValue().patchVersion());
        assertEquals("secret-session-id-not-for-logs", profile.getValue().sessionId());
        assertFalse(profile.getValue().toString().contains("secret-session-id-not-for-logs"));

        ArgumentCaptor<AuthData> authData = ArgumentCaptor.forClass(AuthData.class);
        verify(session).setAuthData(authData.capture());
        assertEquals("测试玩家", authData.getValue().name());
        assertEquals("1234567890", authData.getValue().xuid());
        assertEquals("6f8ad1fb-6c2b-3d4f-9a1e-7d4a1c2b3e4f", authData.getValue().uuid().toString());

        ArgumentCaptor<BedrockClientData> data = ArgumentCaptor.forClass(BedrockClientData.class);
        verify(session).setClientData(data.capture());
        assertEquals("", data.getValue().getServerAddress());

        ArgumentCaptor<ServerToClientHandshakePacket> handshake = ArgumentCaptor.forClass(ServerToClientHandshakePacket.class);
        verify(session).sendUpstreamPacketImmediately(handshake.capture());
        JsonWebSignature jwt = new JsonWebSignature();
        jwt.setCompactSerialization(handshake.getValue().getJwt());
        jwt.setKey(NeteaseLoginChain.parseKey(jwt.getHeader("x5u")));
        assertTrue(jwt.verifySignature());
        assertEquals(16, Base64.getDecoder().decode((String) JsonUtil.parseJson(jwt.getPayload()).get("salt")).length);
        assertNotNull(channel.pipeline().get(BedrockEncryptionEncoder.NAME));
        assertNotNull(channel.pipeline().get(BedrockEncryptionDecoder.NAME));
    }

    @Test
    void chainOfAnUntrustedRootIsRefusedWithTheMessage() throws Exception {
        configure(true);
        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.chain(), AuthType.FULL), clientData());
        assertEquals(NeteaseLogin.Outcome.REFUSED, outcome);
        verify(session).disconnect("客户端版本错误，请使用中国版客户端加入游戏。");
        verify(session, never()).setAuthData(any());
        verify(session, never()).setNeteaseProfile(any());
    }

    @Test
    void internationalLoginIsRefusedWithTheMessage() throws Exception {
        configure(true);
        LoginPacket login = new LoginPacket();
        login.setAuthPayload(new TokenPayload("header.payload.signature", AuthType.FULL));
        login.setClientJwt(clientData());

        assertFalse(LoginEncryptionUtils.encryptPlayerConnection(session, login));
        verify(session).disconnect("客户端版本错误，请使用中国版客户端加入游戏。");
        verify(session, never()).setAuthData(any());
    }

    @Test
    void otherClientsGoTheUpstreamWayWhenAllowed() throws Exception {
        configure(false);
        NeteaseClients.markRaknet8(channel);
        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.chain(), AuthType.FULL), clientData());
        assertEquals(NeteaseLogin.Outcome.NOT_NETEASE, outcome);
        verify(session, never()).disconnect(anyString());
        // 像网易客户端一样连接、登录链却不是网易签发的，换回国际版的编解码器
        verify(session.getUpstream().getSession()).setCodec(GameProtocol.getBedrockCodec(898));
    }

    @Test
    void clientDataSignedByAnotherKeyIsRejected() {
        configure(true, NeteaseTestChains.encoded(chains.root));
        String forged = NeteaseTestChains.sign(NeteaseTestChains.keyPair(), Map.of("GameVersion", "1.21.130"));
        assertThrows(IllegalStateException.class,
            () -> NeteaseLogin.login(session, new CertificateChainPayload(chains.chain(), AuthType.FULL), forged));
        verify(session, never()).setAuthData(any());
    }

    @Test
    void javaUsernames() {
        assertEquals("Steve_1", NeteaseLogin.javaUsername("Steve_1", "1"));
        assertEquals("NE2147483648", NeteaseLogin.javaUsername("测试玩家", "2147483648"));
        assertEquals("NE2147483648", NeteaseLogin.javaUsername("Steve 1", "2147483648"));
        assertEquals("NE2147483648", NeteaseLogin.javaUsername("SeventeenCharName", "2147483648"));
        String name = NeteaseLogin.javaUsername("名字", "123456789012345678");
        assertEquals("NE56789012345678", name);
        assertEquals(16, name.length());
    }

    @Test
    void bothSidesDeriveTheSameKey() throws Exception {
        KeyPair server = NeteaseTestChains.keyPair();
        KeyPair client = NeteaseTestChains.keyPair();
        byte[] salt = new byte[16];
        SecretKey serverKey = NeteaseLogin.secretKey(server.getPrivate(), client.getPublic(), salt);
        SecretKey clientKey = NeteaseLogin.secretKey(client.getPrivate(), server.getPublic(), salt);
        assertArrayEquals(serverKey.getEncoded(), clientKey.getEncoded());
        assertEquals(32, serverKey.getEncoded().length);
    }
}
