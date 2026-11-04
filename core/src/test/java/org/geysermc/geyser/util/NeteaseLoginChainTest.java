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

import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：网易登录链的校验。
 */
class NeteaseLoginChainTest {
    private final NeteaseTestChains chains = new NeteaseTestChains();

    private Set<String> trusting(String... extra) {
        return NeteaseLoginChain.parseTrustedRoots(List.of(extra), warning -> {
            throw new AssertionError(warning);
        });
    }

    private NeteaseLoginChain.Result validate(List<String> chain, Set<String> roots) {
        return NeteaseLoginChain.validate(new CertificateChainPayload(chain, AuthType.FULL), roots, chains.now);
    }

    @Test
    void chainSignedByATrustedRootPasses() {
        NeteaseLoginChain.Result result = validate(chains.chain(), trusting(NeteaseTestChains.encoded(chains.root)));
        assertNotNull(result.verified(), result.reason());
        assertEquals("测试玩家", result.verified().extraData().get("displayName"));
        assertEquals(NeteaseTestChains.encoded(chains.client), Base64.getEncoder().encodeToString(result.verified().identityPublicKey().getEncoded()));
        assertEquals(NeteaseTestChains.encoded(chains.root), result.verified().rootKey());
    }

    @Test
    void neteaseRootIsAlwaysTrusted() {
        Set<String> roots = trusting();
        assertTrue(roots.contains(NeteaseLoginChain.NETEASE_ROOT_KEY));
        assertEquals(1, roots.size());
    }

    @Test
    void chainOfAnUntrustedRootIsRefused() {
        // 例如国际版客户端：第二段由微软的根公钥签发。
        NeteaseLoginChain.Result result = validate(chains.chain(), trusting());
        assertNull(result.verified());
        assertTrue(result.reason().contains("not signed by a trusted NetEase root key"), result.reason());
        assertTrue(result.reason().contains(NeteaseLoginChain.fingerprintOf(NeteaseTestChains.encoded(chains.root))), result.reason());
    }

    @Test
    void extraTrustedRootKeyIsAccepted() {
        // 配置中的写法可以带空白，也可以是另一种 Base64 排版，规范化后比较。
        String key = "  " + NeteaseTestChains.encoded(chains.root) + "\n";
        assertNotNull(validate(chains.chain(), trusting(key)).verified());
    }

    @Test
    void unreadableConfiguredKeysAreSkippedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        Set<String> roots = NeteaseLoginChain.parseTrustedRoots(List.of("not a key", ""), warnings::add);
        assertEquals(Set.of(NeteaseLoginChain.NETEASE_ROOT_KEY), roots);
        assertEquals(1, warnings.size());
    }

    @Test
    void tamperedEntryIsRefused() {
        List<String> chain = new ArrayList<>(chains.chain());
        String identity = chain.get(2);
        String[] parts = identity.split("\\.");
        // 换一段载荷，签名对不上。
        String other = NeteaseTestChains.sign(chains.intermediate, Map.of("identityPublicKey", NeteaseTestChains.encoded(chains.client),
            "extraData", Map.of("displayName", "Other", "uid", 1)));
        chain.set(2, parts[0] + "." + other.split("\\.")[1] + "." + parts[2]);
        NeteaseLoginChain.Result result = validate(chain, trusting(NeteaseTestChains.encoded(chains.root)));
        assertNull(result.verified());
        assertTrue(result.reason().contains("signature of entry 2"), result.reason());
    }

    @Test
    void entrySignedByAnotherKeyIsRefused() {
        List<String> chain = new ArrayList<>(chains.chain());
        // 第三段改由不相干的密钥签发：头部的 x5u 与上一段指向的密钥不一致。
        chain.set(2, NeteaseTestChains.sign(NeteaseTestChains.keyPair(), Map.of("identityPublicKey", NeteaseTestChains.encoded(chains.client),
            "extraData", Map.of("displayName", "Other", "uid", 1))));
        NeteaseLoginChain.Result result = validate(chain, trusting(NeteaseTestChains.encoded(chains.root)));
        assertNull(result.verified());
        assertTrue(result.reason().contains("entry 2"), result.reason());
    }

    @Test
    void expiredChainIsRefused() {
        chains.expiry = chains.now - NeteaseLoginChain.ALLOWED_CLOCK_SKEW_SECONDS - 1;
        NeteaseLoginChain.Result result = validate(chains.chain(), trusting(NeteaseTestChains.encoded(chains.root)));
        assertNull(result.verified());
        assertTrue(result.reason().contains("expired"), result.reason());
    }

    @Test
    void chainWithoutExpiryIsAccepted() {
        chains.expiry = null;
        assertNotNull(validate(chains.chain(), trusting(NeteaseTestChains.encoded(chains.root))).verified());
    }

    @Test
    void otherPayloadsAreNotNetease() {
        assertNull(NeteaseLoginChain.validate(new TokenPayload("token", AuthType.FULL), trusting(), chains.now).verified());
        assertNull(validate(List.of(chains.chain().get(2)), trusting(NeteaseTestChains.encoded(chains.root))).verified());
        assertNull(validate(List.of("a", "b", "c"), trusting()).verified());
    }

    @Test
    void identityWithoutAnyIdIsRefused() {
        chains.extraData.remove("uid");
        chains.extraData.remove("XUID");
        assertNull(validate(chains.chain(), trusting(NeteaseTestChains.encoded(chains.root))).verified());
    }

    @Test
    void descriptionWithholdsTheSessionId() {
        List<String> chain = chains.chain();
        CertificateChainPayload payload = new CertificateChainPayload(chain, AuthType.FULL);
        assertTrue(NeteaseLoginChain.looksLikeNetease(payload));
        String description = String.join("\n", NeteaseLoginChain.describe(payload));
        assertFalse(description.contains("secret-session-id-not-for-logs"), description);
        assertTrue(description.contains("netease_sid=<30 characters, withheld>"), description);
        assertTrue(description.contains("env=development"), description);
        assertTrue(description.contains("patchVersion=3.10.5.301327"), description);
        assertTrue(description.contains("root key (x5u of entry 1): " + NeteaseTestChains.encoded(chains.root)), description);
        // 不写出任何一段完整的令牌。
        for (String entry : chain) {
            assertFalse(description.contains(entry.split("\\.")[2]), description);
        }
    }
    @Test
    void selfSignedNeteaseLoginIsRecognised() {
        NeteaseLoginChain.SelfSigned selfSigned = NeteaseLoginChain.selfSigned(
            new CertificateChainPayload(chains.selfSignedChain(" ev "), AuthType.SELF_SIGNED));
        assertNotNull(selfSigned);
        assertEquals("ev", selfSigned.displayName());
        assertEquals(NeteaseTestChains.encoded(chains.client), Base64.getEncoder().encodeToString(selfSigned.identityPublicKey().getEncoded()));
        // 一段链不是网易签发的正式登录。
        assertNull(validate(chains.selfSignedChain("ev"), trusting(NeteaseTestChains.encoded(chains.root))).verified());
    }

    @Test
    void selfSignedLoginWithoutNeteaseFieldsIsNotNetease() {
        // 国际版客户端离线登录时的自签名链。
        Map<String, Object> extra = Map.of("XUID", "", "displayName", "Steve", "identity", "7724e383-c82b-359f-822e-021d6db84e6c");
        assertNull(NeteaseLoginChain.selfSigned(new CertificateChainPayload(chains.selfSignedChain(extra), AuthType.SELF_SIGNED)));
        assertNull(NeteaseLoginChain.selfSigned(new CertificateChainPayload(
            chains.selfSignedChain(Map.of("displayName", " ", "netease_uid", 1L)), AuthType.SELF_SIGNED)));
    }

    @Test
    void selfSignedLoginMustBeSignedByItsOwnKey() {
        // 由另一个密钥签名、却声称是客户端自己的公钥。
        Map<String, Object> claims = Map.of("extraData", Map.of("displayName", "ev", "netease_uid", 1L),
            "identityPublicKey", NeteaseTestChains.encoded(chains.client));
        assertNull(NeteaseLoginChain.selfSigned(new CertificateChainPayload(
            List.of(NeteaseTestChains.sign(chains.root, claims)), AuthType.SELF_SIGNED)));

        List<String> tampered = new ArrayList<>(chains.selfSignedChain("ev"));
        String[] parts = tampered.getFirst().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1])).replace("\"ev\"", "\"xx\"");
        tampered.set(0, parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + parts[2]);
        assertNull(NeteaseLoginChain.selfSigned(new CertificateChainPayload(tampered, AuthType.SELF_SIGNED)));

        // 三段链、令牌都不是自签名登录。
        assertNull(NeteaseLoginChain.selfSigned(new CertificateChainPayload(chains.chain(), AuthType.FULL)));
        assertNull(NeteaseLoginChain.selfSigned(new TokenPayload("header.payload.signature", AuthType.FULL)));
    }

    @Test
    void describeShowsTheModPcFields() {
        List<String> lines = NeteaseLoginChain.describe(new CertificateChainPayload(chains.selfSignedChain("ev"), AuthType.SELF_SIGNED));
        String text = String.join("\n", lines);
        assertTrue(text.contains("netease_uid=2147483648"), text);
        assertTrue(text.contains("version=3.10"), text);
        assertTrue(text.contains("netease_sid=<0 characters, withheld>"), text);
    }
}
