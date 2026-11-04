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

package org.geysermc.geyser.api.login;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;

/**
 * MINEZ：登录闸门的结果，见 {@link LoginGate}。
 */
public sealed interface LoginGateResult permits LoginGateResult.Allow, LoginGateResult.Disconnect {

    /**
     * 放行，以 {@link GatedLogin#defaultUsername()} 连接 Java 端。
     *
     * @return 结果。
     */
    static @NonNull Allow allow() {
        return new Allow(null, null);
    }

    /**
     * 放行，以给定的名字连接 Java 端。
     *
     * @param javaUsername Java 端的名字，见 {@link Allow}。
     * @return 结果。
     */
    static @NonNull Allow allow(@NonNull String javaUsername) {
        return new Allow(Objects.requireNonNull(javaUsername, "javaUsername"), null);
    }

    /**
     * 放行，以给定的名字连接 Java 端，并在会话上记下附加信息，之后由 {@link org.geysermc.geyser.api.connection.GeyserConnection#loginGateResult()} 取得。
     *
     * @param javaUsername Java 端的名字，为 null 时用 {@link GatedLogin#defaultUsername()}。
     * @param attachment   附加信息，由插件自己定义，Geyser 不读取，也不发给 Java 端。
     * @return 结果。
     */
    static @NonNull Allow allow(@Nullable String javaUsername, @Nullable Object attachment) {
        return new Allow(javaUsername, attachment);
    }

    /**
     * 以给定的提示断开。
     *
     * @param reason 断开时显示给玩家的提示。
     * @return 结果。
     */
    static @NonNull Disconnect disconnect(@NonNull String reason) {
        return new Disconnect(reason);
    }

    /**
     * 放行。
     * <p>
     * Java 端的名字原样放进登录包（离线模式下 Velocity、Paper 等由它生成档案），所以须为 1~16 个字符，
     * 不含空白与控制字符；字符集合不再限制（例如中文），是否接受由 Java 端决定。
     * 由 {@link org.geysermc.geyser.api.connection.GeyserConnection#loginGateResult()} 取得的结果中，名字总是实际使用的名字。
     *
     * @param javaUsername Java 端的名字，为 null 时用 {@link GatedLogin#defaultUsername()}。
     * @param attachment   附加信息，由插件自己定义。
     */
    record Allow(@Nullable String javaUsername, @Nullable Object attachment) implements LoginGateResult {
        /**
         * Java 端名字的最大长度，与 Java 版登录包的限制相同。
         */
        public static final int MAX_USERNAME_LENGTH = 16;

        public Allow {
            if (javaUsername != null) {
                if (javaUsername.isEmpty() || javaUsername.length() > MAX_USERNAME_LENGTH) {
                    throw new IllegalArgumentException("The Java username must be 1 to " + MAX_USERNAME_LENGTH + " characters long: " + javaUsername);
                }
                for (int i = 0; i < javaUsername.length(); i++) {
                    char c = javaUsername.charAt(i);
                    if (Character.isWhitespace(c) || Character.isISOControl(c) || Character.isSpaceChar(c)) {
                        throw new IllegalArgumentException("The Java username must not contain whitespace or control characters: " + javaUsername);
                    }
                }
            }
        }
    }

    /**
     * 以给定的提示断开。
     *
     * @param reason 断开时显示给玩家的提示。
     */
    record Disconnect(@NonNull String reason) implements LoginGateResult {
        public Disconnect {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
