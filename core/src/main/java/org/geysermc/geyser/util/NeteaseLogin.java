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

import io.netty.channel.Channel;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.netty.util.nethernet.TransportIdentityBinding;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.LoginLimits;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.netty.codec.FrameIdCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionEncoder;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.network.bedrock.netease.NeteaseClients;
import org.geysermc.geyser.network.bedrock.nethernet.NetherNetPeer;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.session.auth.NeteaseProfile;
import org.geysermc.geyser.text.GeyserLocale;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwx.HeaderParameterNames;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * MINEZ：网易中国版客户端的登录。
 * <p>
 * 登录链由受信任的网易根公钥签发时，由这里完成验证、记下网易的身份信息并开始加密握手，换用网易的编解码器；
 * 否则在只接受网易客户端时断开连接，不然交回上游的流程。整个过程不用协议库的 {@code EncryptionUtils}，
 * 它在类加载时会访问微软的服务，在国内可能失败。
 */
final class NeteaseLogin {
    /**
     * Java 版允许的玩家名。
     */
    private static final Pattern JAVA_USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final AlgorithmConstraints ES384_ONLY =
        new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);
    private static final SecureRandom RANDOM = new SecureRandom();
    /**
     * MINEZ：自签名登录的开发者身份在 Java 端的名字前缀。
     */
    static final String DEVELOPER_PREFIX = "DEV";

    private static volatile @Nullable TrustedRoots trustedRoots;

    private NeteaseLogin() {
    }

    enum Outcome {
        /**
         * 不是网易客户端，交回上游的流程。
         */
        NOT_NETEASE,
        /**
         * 网易客户端已登录，已发出加密握手，等待客户端答复。
         */
        AWAITING_HANDSHAKE,
        /**
         * 网易客户端已登录，不需要加密握手。
         */
        LOGGED_IN,
        /**
         * 已断开连接。
         */
        REFUSED
    }

    static Outcome login(GeyserSession session, AuthPayload payload, String clientJwt) throws Exception {
        GeyserImpl geyser = session.getGeyser();
        GeyserLogger logger = geyser.getLogger();
        GeyserConfig.NeteaseConfig config = geyser.config().bedrock().netease();
        Channel channel = session.getUpstream().getSession().getPeer().getChannel();
        boolean raknet8 = NeteaseClients.isRaknet8(channel);

        NeteaseLoginChain.Result result = NeteaseLoginChain.validate(payload,
            trustedRoots(config.trustedRootKeys(), logger), System.currentTimeMillis() / 1000);
        NeteaseLoginChain.Verified verified = result.verified();

        if (geyser.config().debugMode() && (raknet8 || verified != null || NeteaseLoginChain.looksLikeNetease(payload))) {
            logger.debug("China Edition login from " + address(session) + " (RakNet " + (raknet8 ? NeteaseClients.RAKNET_PROTOCOL_VERSION : "11 or unknown")
                + ", protocol " + session.getUpstream().getProtocolVersion() + "): " + result.reason());
            for (String line : NeteaseLoginChain.describe(payload)) {
                logger.debug(line);
            }
        }

        if (verified == null) {
            // MINEZ：像网易客户端一样连接、用网易的协议，登录链却是自签名的一段（例如 Mod PC 开发包）
            NeteaseLoginChain.SelfSigned selfSigned = raknet8 && GameProtocol.getNeteaseCodec(session.getUpstream().getProtocolVersion()) != null
                ? NeteaseLoginChain.selfSigned(payload) : null;
            if (selfSigned != null && config.allowSelfSignedLogins()) {
                return loginSelfSigned(session, payload, clientJwt, selfSigned);
            }
            if (config.onlyNeteaseClients()) {
                String hint;
                if (selfSigned != null) {
                    hint = ". It is a self-signed China Edition login, as the development build of the PC client (Mod PC) sends;"
                        + " \"bedrock.netease.allow-self-signed-logins\" would let it join under an isolated developer identity";
                } else if (raknet8 && !geyser.config().debugMode()) {
                    hint = ". It connected like a China Edition client; enable debug mode to log its login chain";
                } else {
                    hint = "";
                }
                logger.info("Refused a login from " + address(session) + " that is not from a China Edition client: " + result.reason() + hint);
                NeteaseClients.markRefused(channel);
                session.disconnect(NeteaseClients.NOT_NETEASE_MESSAGE);
                return Outcome.REFUSED;
            }
            if (raknet8) {
                // 像网易客户端一样连接，登录链却不是网易签发的：换回国际版的编解码器，交给上游的流程
                BedrockCodec vanilla = GameProtocol.getBedrockCodec(session.getUpstream().getProtocolVersion());
                if (vanilla != null) {
                    session.getUpstream().getSession().setCodec(vanilla);
                }
                logger.debug("Treating the RakNet 8 connection from " + address(session) + " as an international client: " + result.reason());
            }
            return Outcome.NOT_NETEASE;
        }

        // 网易客户端都应是网易的编解码器；关闭 RakNet cookie 时取不到 RakNet 版本，在这里补上
        int protocolVersion = session.getUpstream().getProtocolVersion();
        BedrockCodec neteaseCodec = GameProtocol.getNeteaseCodec(protocolVersion);
        if (neteaseCodec == null) {
            logger.info("Refused a China Edition client on protocol " + protocolVersion + " from " + address(session)
                + "; supported: " + GameProtocol.getNeteaseBedrockVersions());
            session.disconnect(GeyserLocale.getLocaleStringLog(GameProtocol.isNewerThanNeteaseCodecs(protocolVersion)
                ? "geyser.network.outdated.server" : "geyser.network.outdated.client", GameProtocol.getNeteaseBedrockVersions()));
            return Outcome.REFUSED;
        }
        if (session.getUpstream().getSession().getCodec() != neteaseCodec) {
            logger.debug("Switching " + address(session) + " to the China Edition codec, as its RakNet version was not known");
            session.getUpstream().getSession().setCodec(neteaseCodec);
        }

        if (!acceptClientData(session, payload, clientJwt, verified.identityPublicKey())) {
            return Outcome.REFUSED;
        }

        NeteaseProfile profile = profile(verified);
        session.setNeteaseProfile(profile);
        Map<String, Object> extraData = verified.extraData();
        UUID identity = parseUuid(NeteaseLoginChain.text(extraData.get("identity")));
        if (identity == null) {
            // 暂时的做法：没有可用的 identity 时由网易 UID 生成，同一账号总得到同一个 UUID
            identity = UUID.nameUUIDFromBytes(("netease:" + profile.uid()).getBytes(StandardCharsets.UTF_8));
        }
        String xuid = profile.xuid() != null ? profile.xuid() : profile.uid();
        long issuedAt = verified.identity().get("iat") instanceof Number iat ? iat.longValue() : -1;
        session.setAuthData(new AuthData(profile.displayName(), identity, xuid, issuedAt, null));

        logger.debug("China Edition client " + profile + " logged in from " + address(session) + ", root key " + NeteaseLoginChain.fingerprintOf(profile.rootKey()));
        if (!profile.javaUsername().equals(profile.displayName())) {
            logger.debug("The name \"" + profile.displayName() + "\" of China Edition player " + profile.uid()
                + " is not a valid Java username, so they join the Java server as " + profile.javaUsername());
        }

        return startEncryptionHandshake(session, verified.identityPublicKey()) ? Outcome.AWAITING_HANDSHAKE : Outcome.LOGGED_IN;
    }

    /**
     * 验证并记下客户端数据，经 NetherNet 连接时再核对传输层的身份绑定。
     *
     * @return 是否通过；不通过时已断开连接
     */
    private static boolean acceptClientData(GeyserSession session, AuthPayload payload, String clientJwt, PublicKey identityPublicKey) throws Exception {
        byte[] clientData = verifyClientData(clientJwt, identityPublicKey);
        if (clientData == null) {
            throw new IllegalStateException("Client data isn't signed by the given chain data");
        }
        BedrockClientData data = JsonUtils.fromJson(clientData, BedrockClientData.class);
        data.setOriginalString(clientJwt);
        if (data.getServerAddress() == null) {
            // 部分网易客户端不带这一项，而上游取加入地址时默认它存在
            data.setServerAddress("");
        }
        session.setClientData(data);
        if (payload instanceof CertificateChainPayload chainPayload) {
            session.setCertChainData(chainPayload.getChain());
        }

        GeyserImpl geyser = session.getGeyser();
        if (!geyser.config().advanced().bedrock().useWaterdogpeForwarding()
                && session.getUpstream().getSession().getPeer() instanceof NetherNetPeer) {
            Channel channel = session.getUpstream().getSession().getPeer().getChannel();
            return !LoginEncryptionUtils.refusedByBinding(geyser, session, TransportIdentityBinding.mismatch(channel, identityPublicKey));
        }
        return true;
    }

    /**
     * MINEZ：以隔离的开发者身份放行自签名的网易登录（例如 Mod PC 开发包），只在开启 allow-self-signed-logins 时调用。
     * <p>
     * 这种登录中的昵称、UID、XUID、identity 都由客户端自己填写，无法核实，所以都不用作身份：Java 端的名字为
     * {@link #developerUsername}，Bedrock 端的 UUID 与 XUID 都由这个名字算出，与真实的网易账号互不相干。
     * 同一昵称总得到同一身份（离线模式下即同一角色）。会话上的 {@link NeteaseProfile#developer()} 为 true。
     */
    private static Outcome loginSelfSigned(GeyserSession session, AuthPayload payload, String clientJwt,
                                           NeteaseLoginChain.SelfSigned selfSigned) throws Exception {
        GeyserLogger logger = session.getGeyser().getLogger();
        // 调用方已确认这是网易客户端的连接与协议，编解码器理应已是网易的；以防万一再确认一次
        BedrockCodec neteaseCodec = GameProtocol.getNeteaseCodec(session.getUpstream().getProtocolVersion());
        if (neteaseCodec != null && session.getUpstream().getSession().getCodec() != neteaseCodec) {
            session.getUpstream().getSession().setCodec(neteaseCodec);
        }
        if (!acceptClientData(session, payload, clientJwt, selfSigned.identityPublicKey())) {
            return Outcome.REFUSED;
        }

        String username = developerUsername(selfSigned.displayName());
        Map<String, Object> extraData = selfSigned.extraData();
        NeteaseProfile profile = new NeteaseProfile(null, selfSigned.displayName(), username, null,
            NeteaseLoginChain.text(extraData.get("env")),
            NeteaseLoginChain.text(extraData.get("platform")),
            NeteaseLoginChain.text(extraData.get("os_name")),
            NeteaseLoginChain.text(extraData.get("engineVersion")),
            NeteaseLoginChain.text(extraData.get("patchVersion")),
            NeteaseLoginChain.text(extraData.get("bit")),
            Base64.getEncoder().encodeToString(selfSigned.identityPublicKey().getEncoded()),
            null, true);
        session.setNeteaseProfile(profile);
        session.setAuthData(new AuthData(username, developerUuid(username), developerXuid(username), -1, null));

        logger.debug("Self-signed China Edition login from " + address(session) + " (nickname \"" + selfSigned.displayName()
            + "\", client key " + NeteaseLoginChain.fingerprintOf(profile.rootKey()) + "): ignoring the name, UID, XUID and identity it claims, "
            + "it joins under the isolated developer identity " + username);
        logger.info("Let a self-signed China Edition login (such as the Mod PC development build) from " + address(session)
            + " join under the isolated developer identity " + username + ", as allow-self-signed-logins is on; its name and UID cannot be verified");

        return startEncryptionHandshake(session, selfSigned.identityPublicKey()) ? Outcome.AWAITING_HANDSHAKE : Outcome.LOGGED_IN;
    }

    /**
     * MINEZ：自签名登录进入 Java 服时用的名字：“DEV”加昵称中的字母、数字与下划线，总长不超过 16 个字符，例如 DEVSteve；
     * 昵称中一个这样的字符都没有（例如全是中文）时，改为 “DEV” 加由昵称算出的 8 位十六进制散列，例如 DEV1a2b3c4d。
     * 名字只由昵称决定，同一昵称总得到同一名字。
     */
    static String developerUsername(String displayName) {
        String kept = displayName.replaceAll("[^A-Za-z0-9_]", "");
        if (kept.isEmpty()) {
            kept = HexFormat.of().formatHex(sha256(displayName), 0, 4);
        }
        String name = DEVELOPER_PREFIX + kept;
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    /**
     * MINEZ：开发者身份的 Bedrock 端 UUID，由名字算出，与客户端自报的 identity 无关。
     */
    static UUID developerUuid(String username) {
        return UUID.nameUUIDFromBytes(("netease-developer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * MINEZ：开发者身份的 XUID，由名字算出的 63 位正整数，最高的第 62 位总为 1，不会与网易 UID、Xbox 的 XUID 重合。
     * Floodgate 等处把 XUID 当作数字解析，所以不用字母。
     */
    static String developerXuid(String username) {
        byte[] digest = sha256("netease-developer:" + username);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (digest[i] & 0xFF);
        }
        return Long.toString((value & 0x3FFF_FFFF_FFFF_FFFFL) | 0x4000_0000_0000_0000L);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static NeteaseProfile profile(NeteaseLoginChain.Verified verified) {
        Map<String, Object> extraData = verified.extraData();
        String displayName = NeteaseLoginChain.text(extraData.get("displayName"));
        String xuid = NeteaseLoginChain.text(extraData.get("XUID"));
        String uid = NeteaseLoginChain.text(extraData.get("uid"));
        if (uid == null) {
            uid = xuid;
        }
        return new NeteaseProfile(uid, displayName, javaUsername(displayName, uid), xuid,
            NeteaseLoginChain.text(extraData.get("env")),
            NeteaseLoginChain.text(extraData.get("platform")),
            NeteaseLoginChain.text(extraData.get("os_name")),
            NeteaseLoginChain.text(extraData.get("engineVersion")),
            NeteaseLoginChain.text(extraData.get("patchVersion")),
            NeteaseLoginChain.text(extraData.get("bit")),
            verified.rootKey(),
            NeteaseLoginChain.text(extraData.get("netease_sid")),
            false);
    }

    /**
     * 进入 Java 服时用的玩家名。登录链中的名字本身合乎 Java 版的规则（字母、数字与下划线，不超过 16 个字符）就照用；
     * 否则改为 “NE” 加网易 UID，例如 NE123456789。
     * <p>
     * 不逐个替换不合规的字符：离线模式下玩家名就是账号，“张三”与“李四”替换后都成了同一个名字，会进入同一个存档。
     * 网易 UID 每个账号唯一，同一玩家每次得到的名字也相同。
     */
    static String javaUsername(String displayName, String uid) {
        if (JAVA_USERNAME.matcher(displayName).matches()) {
            return displayName;
        }
        String id = uid.replaceAll("[^A-Za-z0-9_]", "");
        if (id.isEmpty()) {
            // 不会发生：校验时要求有 uid 或 XUID。以防万一，用名字的摘要
            id = Integer.toUnsignedString(displayName.hashCode(), 36);
        }
        // 超出 16 个字符时保留末尾，末尾的数字区分度最高
        return "NE" + (id.length() > 14 ? id.substring(id.length() - 14) : id);
    }

    private static @Nullable UUID parseUuid(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static byte @Nullable [] verifyClientData(String clientJwt, PublicKey identityPublicKey) throws Exception {
        LoginLimits.checkJwtHeader(clientJwt);
        JsonWebSignature clientData = new JsonWebSignature();
        clientData.setCompactSerialization(clientJwt);
        clientData.setAlgorithmConstraints(ES384_ONLY);
        clientData.setKey(identityPublicKey);
        return clientData.verifySignature() ? clientData.getUnverifiedPayloadBytes() : null;
    }

    /**
     * 与国际版相同的加密握手：服务端的临时 P-384 密钥与客户端的身份公钥做 ECDH，密钥为 SHA-256（salt ‖ 共享密钥），
     * AES-256-CTR，IV 为密钥的前 12 字节加 00 00 00 02。
     *
     * @return 是否发出了需要客户端答复的握手
     */
    private static boolean startEncryptionHandshake(GeyserSession session, PublicKey clientKey) throws Exception {
        if (session.getUpstream().getSession().getPeer() instanceof NetherNetPeer) {
            // 与上游相同：DTLS 已经加密
            return false;
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair serverKeyPair = generator.generateKeyPair();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        ServerToClientHandshakePacket packet = new ServerToClientHandshakePacket();
        packet.setJwt(handshakeJwt(serverKeyPair, salt));
        session.sendUpstreamPacketImmediately(packet);

        enableEncryption(session.getUpstream().getSession().getPeer().getChannel(), secretKey(serverKeyPair.getPrivate(), clientKey, salt));
        return true;
    }

    static String handshakeJwt(KeyPair serverKeyPair, byte[] salt) throws Exception {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);
        signature.setHeader(HeaderParameterNames.X509_URL, Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded()));
        signature.setKey(serverKeyPair.getPrivate());
        JwtClaims claims = new JwtClaims();
        claims.setClaim("salt", Base64.getEncoder().encodeToString(salt));
        signature.setPayload(claims.toJson());
        return signature.getCompactSerialization();
    }

    static SecretKey secretKey(PrivateKey localPrivateKey, PublicKey remotePublicKey, byte[] salt) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(localPrivateKey);
        agreement.doPhase(remotePublicKey, true);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        digest.update(agreement.generateSecret());
        return new SecretKeySpec(digest.digest(), "AES");
    }

    private static void enableEncryption(Channel channel, SecretKey key) throws Exception {
        if (channel.pipeline().get(BedrockEncryptionEncoder.class) != null || channel.pipeline().get(BedrockEncryptionDecoder.class) != null) {
            throw new IllegalStateException("Encryption is already enabled");
        }
        channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionEncoder.NAME, new BedrockEncryptionEncoder(key, cipher(Cipher.ENCRYPT_MODE, key)));
        channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionDecoder.NAME, new BedrockEncryptionDecoder(key, cipher(Cipher.DECRYPT_MODE, key)));
    }

    static Cipher cipher(int mode, SecretKey key) throws Exception {
        byte[] iv = Arrays.copyOf(key.getEncoded(), 16);
        Arrays.fill(iv, 12, 16, (byte) 0);
        iv[15] = 2;
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(mode, key, new IvParameterSpec(iv));
        return cipher;
    }

    private static String address(GeyserSession session) {
        return session.getGeyser().config().logPlayerIpAddresses() ? String.valueOf(session.getSocketAddress()) : "<IP address withheld>";
    }

    /**
     * 受信任的根公钥。配置不变时只解析一次，读不出来的项也只警告一次。
     */
    static Set<String> trustedRoots(List<String> configured, GeyserLogger logger) {
        TrustedRoots cached = trustedRoots;
        if (cached != null && cached.configured().equals(configured)) {
            return cached.roots();
        }
        List<String> copy = new ArrayList<>();
        for (String key : configured) {
            copy.add(key);
        }
        Set<String> roots = NeteaseLoginChain.parseTrustedRoots(copy, logger::warning);
        trustedRoots = new TrustedRoots(copy, roots);
        return roots;
    }

    private record TrustedRoots(List<String> configured, Set<String> roots) {
    }
}
