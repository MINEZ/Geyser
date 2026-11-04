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

package org.geysermc.geyser.api.connection;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.UUID;

/**
 * MINEZ：经 Geyser 验证的网易中国版客户端身份，由 {@link GeyserConnection#neteaseIdentity()} 取得。
 * <p>
 * 只有登录链由受信任的根公钥签发（网易内置的根公钥，或配置 {@code bedrock.netease.trusted-root-keys} 中的根公钥）、
 * 并完成了加密握手的会话才有身份；配置 {@code bedrock.netease.allow-self-signed-logins} 放行的自签名登录（例如 Mod PC 开发包）
 * 也有一份，但标为 {@link #developer()}，其中的内容都无法核实。
 * <p>
 * 这里不含网易会话 ID（{@code netease_sid}）与任何令牌，可以写进日志。
 */
public interface NeteaseIdentity {

    /**
     * 网易账号的 UID，即登录链 {@code extraData} 中的 {@code uid}，原样转为字符串。
     * 登录链中没有 {@code uid} 时为 null，不以 XUID 代替；开发者身份总为 null。
     *
     * @return 网易 UID，可能为 null
     */
    @Nullable String uid();

    /**
     * 登录链中的 XUID。网易的 XUID 不是 Xbox 账号的 ID，同一网易账号在 PC 与手机上也不同；开发者身份为 null。
     *
     * @return XUID，可能为 null
     */
    @Nullable String xuid();

    /**
     * 网易昵称，即登录链中的 {@code displayName}，可能含中文等 Java 版不允许的字符。
     *
     * @return 网易昵称
     */
    @NonNull String displayName();

    /**
     * Geyser 为这次登录确定的基岩版身份 UUID：网易签发的登录为登录链中的 {@code identity}（没有时由 UID 算出），
     * 开发者身份为由开发者名字算出的 UUID，与客户端自报的 {@code identity} 无关。
     *
     * @return 基岩版身份 UUID
     */
    @NonNull UUID identityUuid();

    /**
     * 网易的环境标识（{@code env}），正式环境为 {@code obt}。
     *
     * @return 环境标识，登录链中没有时为 null
     */
    @Nullable String env();

    /**
     * 登录链是否由 Geyser 内置的网易正式环境根公钥签发。由 {@code trusted-root-keys} 中的其他根公钥签发的登录、
     * 开发者身份都为 false。
     *
     * @return 是否由内置的网易根公钥签发
     */
    boolean officialRoot();

    /**
     * 是否为自签名登录的隔离身份（开发者身份）。这种登录没有网易的签名，昵称、UID 等都由客户端自己填写，
     * 只在开启 {@code allow-self-signed-logins} 时出现。
     *
     * @return 是否为开发者身份
     */
    boolean developer();

    /**
     * 签发登录链的根公钥的指纹，形如 {@code SHA-256:0123456789abcdef}，用于日志；开发者身份为客户端自己的公钥的指纹。
     *
     * @return 根公钥的指纹
     */
    @NonNull String rootKeyFingerprint();

    /**
     * 登录链中的平台（{@code platform}），例如 {@code pc}。
     *
     * @return 平台，没有时为 null
     */
    @Nullable String platform();

    /**
     * 登录链中的操作系统（{@code os_name}）。
     *
     * @return 操作系统，没有时为 null
     */
    @Nullable String osName();

    /**
     * 登录链中的引擎版本（{@code engineVersion}）。
     *
     * @return 引擎版本，没有时为 null
     */
    @Nullable String engineVersion();

    /**
     * 登录链中的补丁版本（{@code patchVersion}）。
     *
     * @return 补丁版本，没有时为 null
     */
    @Nullable String patchVersion();

    /**
     * 登录链中的客户端位数（{@code bit}）。
     *
     * @return 客户端位数，没有时为 null
     */
    @Nullable String bit();
}
