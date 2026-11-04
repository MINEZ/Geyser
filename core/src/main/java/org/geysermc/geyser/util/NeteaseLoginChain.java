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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.codec.LoginLimits;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * MINEZ：校验网易中国版客户端的登录链。
 * <p>
 * 结构与微软的三段链相同：第一段由客户端用自己的密钥签名，载荷中的 {@code identityPublicKey} 指向根公钥；第二段由根公钥签名；
 * 第三段带身份数据。每一段都要用上一段的 {@code identityPublicKey} 验签，并与该段头部的 {@code x5u} 一致。
 * 与微软不同的只是根公钥：须为网易的根公钥，或配置中额外信任的根公钥。算法为 ES384。
 * <p>
 * 这里不用协议库的 {@code EncryptionUtils}：它在类加载时就会访问微软的服务，在国内可能失败。
 */
public final class NeteaseLoginChain {
    /**
     * 网易正式环境（obt）的根公钥，P-384，Base64 编码的 X.509 公钥。多个公开的网易互通实现（例如网易官方的 WaterdogPE_Netease
     * 所依赖的 AllayMC/Protocol，以及 Nukkit-MOT）中的这一公钥完全一致。
     */
    public static final String NETEASE_ROOT_KEY = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEEsmU+IF/XeAF3yiqJ7Ko36btx6JtdB26wV9Eyw4AYR/nmesznkfXxwQ4B0NkSnGIZccbb2f3nFUYughKSoAcNHx+lQm8F9h9RwhrNgeN907z06LUA2AqWcwqasxyaU0E";

    /**
     * 判断第二、三段是否过期时容许的时钟误差，与协议库对微软登录链的处理相同。
     */
    static final long ALLOWED_CLOCK_SKEW_SECONDS = 60;

    private static final AlgorithmConstraints ES384_ONLY =
        new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);

    /**
     * 记入 debug 日志的 extraData 字段。其余字段只记名字，{@code netease_sid} 只记是否存在。
     */
    private static final List<String> LOGGED_EXTRA_DATA = List.of("displayName", "uid", "XUID", "identity", "env",
        "platform", "os_name", "engineVersion", "patchVersion", "bit", "netease_uid", "version");

    /**
     * MINEZ：只有网易客户端才在 extraData 中带的字段，至少带其中一项的自签名登录才当作网易客户端。
     * Mod PC 3.10 的开发包实测带 netease_uid 与 netease_sid（后者为空），netease_uuid 见于资料中的房间登录。
     */
    static final List<String> NETEASE_ONLY_EXTRA_DATA = List.of("netease_uid", "netease_uuid", "netease_sid");

    private NeteaseLoginChain() {
    }

    /**
     * 验证通过的登录链。
     *
     * @param identityPublicKey 客户端的身份公钥，用来验证客户端数据并完成加密握手。
     * @param identity          第三段的载荷。
     * @param extraData         第三段载荷中的 extraData。
     * @param rootKey           签发第二段的根公钥（规范化后的 Base64）。
     */
    public record Verified(ECPublicKey identityPublicKey, Map<String, Object> identity, Map<String, Object> extraData, String rootKey) {
    }

    /**
     * MINEZ：自签名的网易登录，例如 Mod PC 开发包。登录链只有一段，由客户端用自己的密钥签名，没有网易的签名，
     * 其中的名字、UID 等都无法核实。
     *
     * @param identityPublicKey 客户端的公钥，即这一段头部的 {@code x5u}，也是载荷中的 {@code identityPublicKey}。
     * @param extraData         载荷中的 extraData。
     * @param displayName       extraData 中的昵称，去掉了首尾空白。
     */
    public record SelfSigned(ECPublicKey identityPublicKey, Map<String, Object> extraData, String displayName) {
    }

    /**
     * 校验的结果。
     *
     * @param verified 验证通过时的内容，否则为 null。
     * @param reason   不通过的原因，用于日志。
     */
    public record Result(@Nullable Verified verified, String reason) {
        static Result failed(String reason) {
            return new Result(null, reason);
        }
    }

    /**
     * 校验登录链是否由受信任的网易根公钥签发，并且各段都在有效期内。
     *
     * @param payload      登录包中的认证信息。
     * @param trustedRoots 受信任的根公钥，规范化后的 Base64，见 {@link #parseTrustedRoots}。
     * @param nowSeconds   当前时间（Unix 秒）。
     */
    public static Result validate(AuthPayload payload, Set<String> trustedRoots, long nowSeconds) {
        if (!(payload instanceof CertificateChainPayload chainPayload)) {
            return Result.failed("the login carries a " + payload.getClass().getSimpleName() + " (" + payload.getAuthType() + "), not a certificate chain");
        }
        List<String> chain = chainPayload.getChain();
        if (chain == null || chain.size() != 3) {
            return Result.failed("the certificate chain has " + (chain == null ? 0 : chain.size()) + " entries instead of 3");
        }

        try {
            // 先只看第二段的签发者，国际版的登录不必逐段验签。
            String rootKey = canonical(header(chain.get(1), HeaderParameterNames.X509_URL));
            if (rootKey == null || !trustedRoots.contains(rootKey)) {
                return Result.failed("the chain is not signed by a trusted NetEase root key (root key " + fingerprintOf(rootKey) + ")");
            }

            ECPublicKey currentKey = null;
            Map<String, Object> claims = null;
            for (int i = 0; i < chain.size(); i++) {
                String entry = chain.get(i);
                LoginLimits.checkJwtHeader(entry);
                JsonWebSignature signature = new JsonWebSignature();
                signature.setCompactSerialization(entry);
                signature.setAlgorithmConstraints(ES384_ONLY);

                ECPublicKey advertised = parseKey(signature.getHeader(HeaderParameterNames.X509_URL));
                if (currentKey == null) {
                    currentKey = advertised;
                } else if (!sameKey(currentKey, advertised)) {
                    return Result.failed("entry " + i + " names a different key than the previous entry trusts");
                }
                signature.setKey(currentKey);
                if (!signature.verifySignature()) {
                    return Result.failed("the signature of entry " + i + " does not match");
                }

                claims = LoginLimits.parseJson(signature.getUnverifiedPayload());
                if (i > 0) {
                    String window = checkWindow(claims, nowSeconds);
                    if (window != null) {
                        return Result.failed("entry " + i + " " + window);
                    }
                }
                if (!(claims.get("identityPublicKey") instanceof String nextKey)) {
                    return Result.failed("entry " + i + " has no identityPublicKey");
                }
                currentKey = parseKey(nextKey);
            }

            if (!(claims.get("extraData") instanceof Map<?, ?> rawExtraData)) {
                return Result.failed("the identity entry has no extraData");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraData = (Map<String, Object>) rawExtraData;
            if (!(extraData.get("displayName") instanceof String name) || name.isBlank()) {
                return Result.failed("the identity entry has no displayName");
            }
            if (text(extraData.get("uid")) == null && text(extraData.get("XUID")) == null) {
                return Result.failed("the identity entry has neither uid nor XUID");
            }
            return new Result(new Verified(currentKey, claims, extraData, rootKey), "signed by root key " + fingerprintOf(rootKey));
        } catch (Exception e) {
            return Result.failed("the chain could not be read: " + e.getMessage());
        }
    }

    /**
     * MINEZ：判断登录是否为自签名的网易登录：登录链只有一段；头部的 {@code x5u} 与载荷中的 {@code identityPublicKey}
     * 是同一个公钥，并且签名对得上（只允许 ES384）；extraData 中带网易特有的字段（见 {@link #NETEASE_ONLY_EXTRA_DATA}）
     * 与不为空的 {@code displayName}。不检查有效期：自签名的有效期由客户端自己写，没有意义。
     * <p>
     * 这只说明登录“像网易客户端”，不能证明任何身份；是否放行、以什么身份放行由调用方决定。
     *
     * @return 是自签名的网易登录时为其内容，否则为 null。
     */
    public static @Nullable SelfSigned selfSigned(AuthPayload payload) {
        if (!(payload instanceof CertificateChainPayload chainPayload) || chainPayload.getChain() == null
                || chainPayload.getChain().size() != 1) {
            return null;
        }
        try {
            String entry = chainPayload.getChain().getFirst();
            LoginLimits.checkJwtHeader(entry);
            JsonWebSignature signature = new JsonWebSignature();
            signature.setCompactSerialization(entry);
            signature.setAlgorithmConstraints(ES384_ONLY);
            ECPublicKey key = parseKey(signature.getHeader(HeaderParameterNames.X509_URL));
            signature.setKey(key);
            if (!signature.verifySignature()) {
                return null;
            }
            Map<String, Object> claims = LoginLimits.parseJson(signature.getUnverifiedPayload());
            if (!(claims.get("identityPublicKey") instanceof String identityKey) || !sameKey(key, parseKey(identityKey))) {
                return null;
            }
            if (!(claims.get("extraData") instanceof Map<?, ?> rawExtraData)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> extraData = (Map<String, Object>) rawExtraData;
            if (NETEASE_ONLY_EXTRA_DATA.stream().noneMatch(extraData::containsKey)) {
                return null;
            }
            if (!(extraData.get("displayName") instanceof String name) || name.isBlank()) {
                return null;
            }
            return new SelfSigned(key, extraData, name.strip());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return 时间窗口不对时的说明，在有效期内或没有写明时为 null。
     */
    private static @Nullable String checkWindow(Map<String, Object> claims, long nowSeconds) {
        // 网易的登录链是否总带有效期，资料中没有说明。写明了就检查，没写明则放行，并在 debug 日志中看得到。
        if (claims.get("exp") instanceof Number exp && nowSeconds > exp.longValue() + ALLOWED_CLOCK_SKEW_SECONDS) {
            return "expired at " + exp.longValue();
        }
        if (claims.get("nbf") instanceof Number nbf && nowSeconds + ALLOWED_CLOCK_SKEW_SECONDS < nbf.longValue()) {
            return "is not valid until " + nbf.longValue();
        }
        return null;
    }

    /**
     * 不验签，只看登录链是否像网易签发的，决定是否在 debug 日志中写出它的信息。
     */
    public static boolean looksLikeNetease(AuthPayload payload) {
        if (!(payload instanceof CertificateChainPayload chainPayload) || chainPayload.getChain() == null) {
            return false;
        }
        for (String entry : chainPayload.getChain()) {
            Map<String, Object> claims = unverifiedClaims(entry);
            if (claims == null) {
                continue;
            }
            if ("NetEase".equalsIgnoreCase(text(claims.get("iss")))) {
                return true;
            }
            if (claims.get("extraData") instanceof Map<?, ?> extraData
                && (extraData.containsKey("uid") || extraData.containsKey("env")
                    || NETEASE_ONLY_EXTRA_DATA.stream().anyMatch(extraData::containsKey))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入 debug 日志的登录链信息：每段的签发者、算法、密钥指纹与有效期，第二段写出完整的根公钥，
     * 以便加入 {@code trusted-root-keys}；第三段写出 extraData 中的版本与环境等字段。
     * 不写出完整的令牌、签名或 {@code netease_sid}。
     */
    public static List<String> describe(AuthPayload payload) {
        List<String> lines = new ArrayList<>();
        if (!(payload instanceof CertificateChainPayload chainPayload) || chainPayload.getChain() == null) {
            lines.add("auth payload: " + payload.getClass().getSimpleName() + " (" + payload.getAuthType() + "), no certificate chain");
            return lines;
        }
        List<String> chain = chainPayload.getChain();
        lines.add("certificate chain: " + chain.size() + " entries, auth type " + payload.getAuthType());
        for (int i = 0; i < chain.size(); i++) {
            String entry = chain.get(i);
            StringBuilder line = new StringBuilder("  entry ").append(i).append(':');
            String x5u = header(entry, HeaderParameterNames.X509_URL);
            line.append(" alg=").append(header(entry, "alg"));
            line.append(", x5u ").append(fingerprintOf(canonical(x5u)));
            Map<String, Object> claims = unverifiedClaims(entry);
            if (claims == null) {
                lines.add(line.append(", payload unreadable").toString());
                continue;
            }
            for (String claim : List.of("iss", "iat", "nbf", "exp", "randomNonce")) {
                if (claims.containsKey(claim)) {
                    line.append(", ").append(claim).append('=').append(claims.get(claim));
                }
            }
            line.append(", identityPublicKey ").append(fingerprintOf(canonical(text(claims.get("identityPublicKey")))));
            line.append(", claims ").append(new TreeSet<>(claims.keySet()));
            lines.add(line.toString());
            if (i == 1 && x5u != null) {
                lines.add("  root key (x5u of entry 1): " + x5u);
            }
            if (claims.get("extraData") instanceof Map<?, ?> extraData) {
                StringBuilder extra = new StringBuilder("  extraData of entry ").append(i).append(':');
                for (String key : LOGGED_EXTRA_DATA) {
                    if (extraData.containsKey(key)) {
                        extra.append(' ').append(key).append('=').append(extraData.get(key));
                    }
                }
                Object sid = extraData.get("netease_sid");
                if (sid != null) {
                    extra.append(" netease_sid=<").append(String.valueOf(sid).length()).append(" characters, withheld>");
                }
                extra.append(", keys ").append(new TreeSet<>(stringKeys(extraData)));
                lines.add(extra.toString());
            }
        }
        return lines;
    }

    /**
     * 把配置中的根公钥规范化。读不出来的写一条警告后跳过。
     *
     * @return 网易的根公钥与配置中的根公钥，规范化后的 Base64。
     */
    public static Set<String> parseTrustedRoots(Collection<String> configured, Consumer<String> warning) {
        Set<String> roots = new LinkedHashSet<>();
        roots.add(NETEASE_ROOT_KEY);
        for (String key : configured) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String canonical = canonical(key.strip());
            if (canonical == null) {
                warning.accept("Ignoring an entry of \"trusted-root-keys\" that is not a Base64-encoded EC public key: " + key);
            } else {
                roots.add(canonical);
            }
        }
        return Set.copyOf(roots);
    }

    /**
     * @return 公钥的 SHA-256 指纹前 16 位十六进制，读不出来时说明原因。
     */
    public static String fingerprintOf(@Nullable String canonicalKey) {
        if (canonicalKey == null) {
            return "<missing or unreadable>";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(canonicalKey));
            return "SHA-256:" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * @return 规范化后的 Base64 编码的 X.509 公钥，不是 EC 公钥时为 null。
     */
    static @Nullable String canonical(@Nullable String base64Key) {
        if (base64Key == null) {
            return null;
        }
        try {
            return Base64.getEncoder().encodeToString(parseKey(base64Key).getEncoded());
        } catch (Exception e) {
            return null;
        }
    }

    static ECPublicKey parseKey(String base64Key) throws Exception {
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64Key)));
        if (!(key instanceof ECPublicKey ecKey)) {
            throw new IllegalArgumentException("Not an EC public key");
        }
        return ecKey;
    }

    private static boolean sameKey(PublicKey a, PublicKey b) {
        return MessageDigest.isEqual(a.getEncoded(), b.getEncoded());
    }

    private static @Nullable String header(String jwt, String name) {
        try {
            LoginLimits.checkJwtHeader(jwt);
            JsonWebSignature signature = new JsonWebSignature();
            signature.setCompactSerialization(jwt);
            return signature.getHeader(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable Map<String, Object> unverifiedClaims(String jwt) {
        try {
            LoginLimits.checkJwtHeader(jwt);
            JsonWebSignature signature = new JsonWebSignature();
            signature.setCompactSerialization(jwt);
            return LoginLimits.parseJson(signature.getUnverifiedPayload());
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> stringKeys(Map<?, ?> map) {
        List<String> keys = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    /**
     * @return 字符串或数字的文字形式，空白或其他类型时为 null。
     */
    static @Nullable String text(@Nullable Object value) {
        String text = value instanceof String string ? string : value instanceof Number number ? String.valueOf(number) : null;
        return text == null || text.isBlank() ? null : text.strip();
    }
}
