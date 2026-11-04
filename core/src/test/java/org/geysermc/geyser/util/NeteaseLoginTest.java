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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private void allowSelfSigned(boolean allow) {
        when(session.getGeyser().config().bedrock().netease().allowSelfSignedLogins()).thenReturn(allow);
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
    @Test
    void selfSignedNeteaseLoginIsRefusedWhenNotAllowed() throws Exception {
        configure(true);
        allowSelfSigned(false);
        NeteaseClients.markRaknet8(channel);
        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.selfSignedChain("ev"), AuthType.SELF_SIGNED), clientData());
        assertEquals(NeteaseLogin.Outcome.REFUSED, outcome);
        verify(session).disconnect("客户端版本错误，请使用中国版客户端加入游戏。");
        verify(session, never()).setAuthData(any());
        verify(session, never()).setNeteaseProfile(any());
    }

    @Test
    void selfSignedNeteaseLoginJoinsAsAnIsolatedDeveloper() throws Exception {
        configure(true);
        allowSelfSigned(true);
        NeteaseClients.markRaknet8(channel);
        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.selfSignedChain("ev"), AuthType.SELF_SIGNED), clientData());

        assertEquals(NeteaseLogin.Outcome.AWAITING_HANDSHAKE, outcome);
        verify(session, never()).disconnect(anyString());

        ArgumentCaptor<NeteaseProfile> profile = ArgumentCaptor.forClass(NeteaseProfile.class);
        verify(session).setNeteaseProfile(profile.capture());
        assertTrue(profile.getValue().developer());
        assertEquals("DEVev", profile.getValue().javaUsername());
        assertEquals("ev", profile.getValue().displayName());
        // 自报的 UID、XUID 与会话 ID 都不记
        assertNull(profile.getValue().uid());
        assertNull(profile.getValue().xuid());
        assertNull(profile.getValue().sessionId());

        ArgumentCaptor<AuthData> authData = ArgumentCaptor.forClass(AuthData.class);
        verify(session).setAuthData(authData.capture());
        assertEquals("DEVev", authData.getValue().name());
        assertEquals(NeteaseLogin.developerXuid("DEVev"), authData.getValue().xuid());
        assertNotEquals("2147483648", authData.getValue().xuid());
        assertTrue(Long.parseLong(authData.getValue().xuid()) >= 1L << 62);
        assertNotEquals("7724e383-c82b-359f-822e-021d6db84e6c", authData.getValue().uuid().toString());
        assertEquals(NeteaseLogin.developerUuid("DEVev"), authData.getValue().uuid());

        verify(session).sendUpstreamPacketImmediately(any(ServerToClientHandshakePacket.class));
        assertNotNull(channel.pipeline().get(BedrockEncryptionEncoder.NAME));
    }

    @Test
    void selfSignedNeteaseLoginOutsideTheNeteaseConnectionIsRefused() throws Exception {
        // 没有以 RakNet 8 连接（不像网易客户端）时，即使开启了开关也不放行
        configure(true);
        allowSelfSigned(true);
        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.selfSignedChain("ev"), AuthType.SELF_SIGNED), clientData());
        assertEquals(NeteaseLogin.Outcome.REFUSED, outcome);
        verify(session).disconnect("客户端版本错误，请使用中国版客户端加入游戏。");
        verify(session, never()).setAuthData(any());
    }

    @Test
    void selfSignedInternationalLoginIsStillRefused() throws Exception {
        configure(true);
        allowSelfSigned(true);
        NeteaseClients.markRaknet8(channel);
        Map<String, Object> extra = Map.of("XUID", "", "displayName", "Steve", "identity", "7724e383-c82b-359f-822e-021d6db84e6c");
        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.selfSignedChain(extra), AuthType.SELF_SIGNED), clientData());
        assertEquals(NeteaseLogin.Outcome.REFUSED, outcome);
        verify(session).disconnect("客户端版本错误，请使用中国版客户端加入游戏。");
        verify(session, never()).setAuthData(any());
        verify(session, never()).setNeteaseProfile(any());
    }

    @Test
    void signedNeteaseLoginIsUnchangedWhenSelfSignedLoginsAreAllowed() throws Exception {
        configure(true, NeteaseTestChains.encoded(chains.root));
        allowSelfSigned(true);
        NeteaseClients.markRaknet8(channel);
        NeteaseLogin.Outcome outcome = NeteaseLogin.login(session, new CertificateChainPayload(chains.chain(), AuthType.FULL), clientData());
        assertEquals(NeteaseLogin.Outcome.AWAITING_HANDSHAKE, outcome);

        ArgumentCaptor<NeteaseProfile> profile = ArgumentCaptor.forClass(NeteaseProfile.class);
        verify(session).setNeteaseProfile(profile.capture());
        assertFalse(profile.getValue().developer());
        assertEquals("2147483648", profile.getValue().uid());
        assertEquals("NE2147483648", profile.getValue().javaUsername());
        ArgumentCaptor<AuthData> authData = ArgumentCaptor.forClass(AuthData.class);
        verify(session).setAuthData(authData.capture());
        assertEquals("1234567890", authData.getValue().xuid());
        assertEquals("6f8ad1fb-6c2b-3d4f-9a1e-7d4a1c2b3e4f", authData.getValue().uuid().toString());
    }

    @Test
    void uidIsNotReplacedByTheXuid() throws Exception {
        // MINEZ：登录链中没有 uid 时，uid 为 null，不以 XUID 代替；没有登录闸门时的名字与身份照旧由 XUID 得出
        chains.extraData.remove("uid");
        configure(true, NeteaseTestChains.encoded(chains.root));
        NeteaseClients.markRaknet8(channel);
        NeteaseLogin.login(session, new CertificateChainPayload(chains.chain(), AuthType.FULL), clientData());

        ArgumentCaptor<NeteaseProfile> profile = ArgumentCaptor.forClass(NeteaseProfile.class);
        verify(session).setNeteaseProfile(profile.capture());
        assertNull(profile.getValue().uid());
        assertEquals("1234567890", profile.getValue().xuid());
        assertEquals("1234567890", profile.getValue().accountId());
        assertEquals("NE1234567890", profile.getValue().javaUsername());
        ArgumentCaptor<AuthData> authData = ArgumentCaptor.forClass(AuthData.class);
        verify(session).setAuthData(authData.capture());
        assertEquals("1234567890", authData.getValue().xuid());
    }

    @Test
    void onlyTheBuiltInRootIsOfficial() throws Exception {
        // 测试用的根公钥经 trusted-root-keys 信任，不是内置的网易根公钥
        configure(true, NeteaseTestChains.encoded(chains.root));
        NeteaseClients.markRaknet8(channel);
        NeteaseLogin.login(session, new CertificateChainPayload(chains.chain(), AuthType.FULL), clientData());
        ArgumentCaptor<NeteaseProfile> profile = ArgumentCaptor.forClass(NeteaseProfile.class);
        verify(session).setNeteaseProfile(profile.capture());
        assertFalse(profile.getValue().officialRoot());

        // 内置的网易根公钥签发的登录（无法以真的私钥签名，直接构造验证结果）
        assertEquals(NeteaseLoginChain.NETEASE_ROOT_KEY, NeteaseLoginChain.canonical(NeteaseLoginChain.NETEASE_ROOT_KEY));
        NeteaseLoginChain.Verified official = new NeteaseLoginChain.Verified(
            (java.security.interfaces.ECPublicKey) chains.client.getPublic(), Map.of(), chains.extraData, NeteaseLoginChain.NETEASE_ROOT_KEY);
        assertTrue(NeteaseLogin.profile(official).officialRoot());
        assertEquals("2147483648", NeteaseLogin.profile(official).uid());
    }

    @Test
    void developerIdentityIsNeverOfficial() throws Exception {
        configure(true);
        allowSelfSigned(true);
        NeteaseClients.markRaknet8(channel);
        NeteaseLogin.login(session, new CertificateChainPayload(chains.selfSignedChain("ev"), AuthType.SELF_SIGNED), clientData());
        ArgumentCaptor<NeteaseProfile> profile = ArgumentCaptor.forClass(NeteaseProfile.class);
        verify(session).setNeteaseProfile(profile.capture());
        assertTrue(profile.getValue().developer());
        assertFalse(profile.getValue().officialRoot());
        assertNull(profile.getValue().accountId());
    }

    @Test
    void developerUsernames() {
        assertEquals("DEVev", NeteaseLogin.developerUsername("ev"));
        assertEquals("DEVSteve_1", NeteaseLogin.developerUsername("Steve_1"));
        assertEquals("DEVSteve1", NeteaseLogin.developerUsername("Steve 1!"));
        assertEquals("DEVAb_c", NeteaseLogin.developerUsername("Ab_c-测试"));
        assertEquals("DEVSeventeenChar", NeteaseLogin.developerUsername("SeventeenCharName"));

        // 全是中文时用散列：同一昵称总得到同一名字，不同昵称得到不同名字
        String zhangSan = NeteaseLogin.developerUsername("张三");
        assertTrue(zhangSan.matches("DEV[0-9a-f]{8}"), zhangSan);
        assertEquals(zhangSan, NeteaseLogin.developerUsername("张三"));
        assertNotEquals(zhangSan, NeteaseLogin.developerUsername("李四"));
        for (String name : List.of("ev", "张三", "SeventeenCharName", "a b c d e f g h i j k l m n o p")) {
            assertTrue(NeteaseLogin.developerUsername(name).matches("DEV[A-Za-z0-9_]{1,13}"), name);
        }
    }
}
