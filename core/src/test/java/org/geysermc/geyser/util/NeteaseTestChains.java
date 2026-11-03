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

import org.jose4j.json.JsonUtil;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MINEZ：测试用的网易风格登录链，由测试自己生成的根密钥签发。
 */
final class NeteaseTestChains {
    final KeyPair root = keyPair();
    final KeyPair intermediate = keyPair();
    final KeyPair client = keyPair();
    long now = System.currentTimeMillis() / 1000;
    Long expiry = now + 3600;
    Map<String, Object> extraData = new LinkedHashMap<>();

    NeteaseTestChains() {
        extraData.put("displayName", "测试玩家");
        extraData.put("identity", "6f8ad1fb-6c2b-3d4f-9a1e-7d4a1c2b3e4f");
        extraData.put("XUID", "1234567890");
        extraData.put("uid", 2147483648L);
        extraData.put("netease_sid", "secret-session-id-not-for-logs");
        extraData.put("platform", "pc");
        extraData.put("os_name", "windows");
        extraData.put("env", "development");
        extraData.put("engineVersion", "1.21.130");
        extraData.put("patchVersion", "3.10.5.301327");
        extraData.put("bit", "64");
    }

    static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp384r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static String encoded(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * 三段链：客户端自签（指向根公钥）、根签发（指向中间密钥）、中间密钥签发（带身份数据，指向客户端公钥）。
     */
    List<String> chain() {
        List<String> chain = new ArrayList<>();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("certificateAuthority", true);
        first.put("exp", now + 3600);
        first.put("identityPublicKey", encoded(root));
        first.put("nbf", now - 60);
        chain.add(sign(client, first));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("certificateAuthority", true);
        if (expiry != null) {
            second.put("exp", expiry);
        }
        second.put("identityPublicKey", encoded(intermediate));
        second.put("iss", "NetEase");
        second.put("nbf", now - 60);
        chain.add(sign(root, second));

        Map<String, Object> third = new LinkedHashMap<>();
        if (expiry != null) {
            third.put("exp", expiry);
        }
        third.put("extraData", extraData);
        third.put("iat", now);
        third.put("identityPublicKey", encoded(client));
        third.put("iss", "NetEase");
        third.put("nbf", now - 60);
        chain.add(sign(intermediate, third));
        return chain;
    }

    /**
     * 客户端数据 JWT，由客户端的密钥签名。
     */
    String clientData(Map<String, Object> claims) {
        return sign(client, claims);
    }

    static String sign(KeyPair signer, Map<String, Object> claims) {
        try {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);
            jws.setHeader("x5u", encoded(signer));
            jws.setPayload(JsonUtil.toJson(claims));
            jws.setKey(signer.getPrivate());
            return jws.getCompactSerialization();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
