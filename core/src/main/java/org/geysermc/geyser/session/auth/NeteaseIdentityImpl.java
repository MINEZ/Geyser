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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.connection.NeteaseIdentity;
import org.geysermc.geyser.util.NeteaseLoginChain;

import java.util.UUID;

/**
 * MINEZ：交给插件的网易身份，由 {@link NeteaseProfile} 去掉网易会话 ID 与 Java 端的名字后得到。
 * 单独一个类，而不是让 NeteaseProfile 直接实现 API，免得插件把它强制转换回去读出会话 ID。
 */
public record NeteaseIdentityImpl(
    @Nullable String uid,
    @Nullable String xuid,
    @NonNull String displayName,
    @NonNull UUID identityUuid,
    @Nullable String env,
    boolean officialRoot,
    boolean developer,
    @NonNull String rootKeyFingerprint,
    @Nullable String platform,
    @Nullable String osName,
    @Nullable String engineVersion,
    @Nullable String patchVersion,
    @Nullable String bit
) implements NeteaseIdentity {

    public static NeteaseIdentityImpl of(NeteaseProfile profile, UUID identityUuid) {
        return new NeteaseIdentityImpl(profile.uid(), profile.xuid(), profile.displayName(), identityUuid, profile.env(),
            profile.officialRoot() && !profile.developer(), profile.developer(), NeteaseLoginChain.fingerprintOf(profile.rootKey()),
            profile.platform(), profile.osName(), profile.engineVersion(), profile.patchVersion(), profile.bit());
    }
}
