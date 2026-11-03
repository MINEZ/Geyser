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

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * MINEZ：网易中国版客户端登录链中的身份信息，经网易的根公钥（或配置中额外信任的根公钥）验证过。
 * <p>
 * 先保存在会话上，供以后的账号方案使用。Java 端的身份暂时沿用 auth-type 的现有机制，用的是 {@link #javaUsername()}。
 *
 * @param uid           网易用户的数字 ID
 * @param displayName   登录链中的玩家名，可能含中文等 Java 版不允许的字符
 * @param javaUsername  进入 Java 服时用的玩家名，见 NeteaseLogin
 * @param xuid          登录链中的 XUID，不是 Xbox 账号的 ID
 * @param env           网易的环境标识，正式环境为 obt
 * @param platform      平台
 * @param osName        操作系统
 * @param engineVersion 引擎版本
 * @param patchVersion  补丁版本
 * @param bit           客户端位数
 * @param rootKey       签发登录链的根公钥（Base64 编码的 X.509 公钥）
 * @param sessionId     网易会话 ID（netease_sid）。可用于向网易核验身份，不要写进日志
 */
public record NeteaseProfile(
    String uid,
    String displayName,
    String javaUsername,
    @Nullable String xuid,
    @Nullable String env,
    @Nullable String platform,
    @Nullable String osName,
    @Nullable String engineVersion,
    @Nullable String patchVersion,
    @Nullable String bit,
    String rootKey,
    @Nullable String sessionId
) {
    /**
     * 不含会话 ID，以免被写进日志。
     */
    @Override
    public String toString() {
        return "NeteaseProfile[uid=" + uid + ", displayName=" + displayName + ", javaUsername=" + javaUsername + ", xuid=" + xuid
            + ", env=" + env + ", platform=" + platform + ", osName=" + osName + ", engineVersion=" + engineVersion
            + ", patchVersion=" + patchVersion + ", bit=" + bit + "]";
    }
}
