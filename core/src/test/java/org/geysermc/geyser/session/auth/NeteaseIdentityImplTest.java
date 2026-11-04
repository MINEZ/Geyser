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

package org.geysermc.geyser.session.auth;

import org.geysermc.geyser.api.connection.NeteaseIdentity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.NeteaseLoginChain;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MINEZ：交给插件的网易身份不含网易会话 ID 与令牌，只在完成基岩版登录后才有。
 */
class NeteaseIdentityImplTest {
    private static final String SESSION_ID = "secret-session-id-not-for-logs";
    private static final UUID IDENTITY = UUID.fromString("6f8ad1fb-6c2b-3d4f-9a1e-7d4a1c2b3e4f");

    private static NeteaseProfile profile(boolean officialRoot, boolean developer) {
        return new NeteaseProfile(developer ? null : "2147483648", "测试玩家", "NE2147483648", developer ? null : "1234567890",
            "obt", "pc", "windows", "1.21.130", "3.10.5.301327", "64", NeteaseLoginChain.NETEASE_ROOT_KEY,
            developer ? null : SESSION_ID, officialRoot, developer);
    }

    @Test
    void carriesTheIdentityButNoSecrets() {
        NeteaseIdentity identity = NeteaseIdentityImpl.of(profile(true, false), IDENTITY);
        assertEquals("2147483648", identity.uid());
        assertEquals("1234567890", identity.xuid());
        assertEquals("测试玩家", identity.displayName());
        assertEquals(IDENTITY, identity.identityUuid());
        assertEquals("obt", identity.env());
        assertTrue(identity.officialRoot());
        assertFalse(identity.developer());
        assertEquals(NeteaseLoginChain.fingerprintOf(NeteaseLoginChain.NETEASE_ROOT_KEY), identity.rootKeyFingerprint());
        assertEquals("pc", identity.platform());
        assertEquals("3.10.5.301327", identity.patchVersion());

        assertFalse(identity.toString().contains(SESSION_ID));
        assertFalse(((Object) identity) instanceof NeteaseProfile);
        // 接口与实现都没有会话 ID、令牌之类的成员。
        for (Method method : NeteaseIdentity.class.getMethods()) {
            assertSecretFree(method.getName());
        }
        for (RecordComponent component : NeteaseIdentityImpl.class.getRecordComponents()) {
            assertSecretFree(component.getName());
        }
        assertFalse(Arrays.stream(NeteaseIdentityImpl.class.getDeclaredFields())
            .anyMatch(field -> field.getType() == NeteaseProfile.class), "must not keep the profile");
    }

    private static void assertSecretFree(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        assertFalse(lower.contains("session") || lower.contains("sid") || lower.contains("token") || lower.contains("javausername"), name);
    }

    @Test
    void developerIsMarkedAndNeverOfficial() {
        NeteaseIdentity identity = NeteaseIdentityImpl.of(profile(true, true), IDENTITY);
        assertTrue(identity.developer());
        assertFalse(identity.officialRoot());
        assertNull(identity.uid());
        assertNull(identity.xuid());
    }

    @Test
    void otherRootsAreNotOfficial() {
        assertFalse(NeteaseIdentityImpl.of(profile(false, false), IDENTITY).officialRoot());
    }
}
