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
import org.geysermc.geyser.api.GeyserApi;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * MINEZ：登录闸门。由插件以 {@link GeyserApi#registerLoginGate(LoginGate)} 注册，同时至多一个。
 * <p>
 * 基岩版玩家通过 Geyser 的验证（网易的登录链或微软的登录链、客户端数据与加密握手）、资源包下发完成之后，
 * Geyser 在连接 Java 端之前把会话交给闸门，等它给出结果：
 * <ul>
 *     <li>{@link LoginGateResult#allow(String, Object)}：放行，以给定的名字连接 Java 端；</li>
 *     <li>{@link LoginGateResult#disconnect(String)}：以给定的提示断开。</li>
 * </ul>
 * 等待期间可以以 {@link GatedLogin#sendForm} 向玩家显示 Cumulus 表单，Geyser 会先生成一个空白的等待世界。
 * 等待超过 {@link #timeout()}（可由 {@link GatedLogin#timeout(Duration, String)} 改变）时断开；
 * 玩家中途断开、超时或闸门被注销时，以 {@link GatedLogin#whenCancelled} 通知插件。
 * <p>
 * 没有注册闸门时，Geyser 照旧直接连接 Java 端。{@code auth-type} 为 {@code online} 时 Java 端的身份来自微软账号，
 * 不经过闸门。在只接受网易客户端时被拒绝的国际版客户端、没有通过验证的登录都不会进入闸门。
 */
@FunctionalInterface
public interface LoginGate {
    /**
     * 默认的等待时限，10 分钟。
     */
    Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    /**
     * 每个会话调用一次，在 Geyser 的线程上调用，不要在其中阻塞。
     * <p>
     * 返回的结果可以在任意线程完成；完成之前会话一直停在闸门中。抛出异常、返回 null 或以异常完成时，
     * Geyser 记下错误并断开这名玩家。闸门因玩家断开、超时或注销而结束时，返回的结果若是
     * {@link java.util.concurrent.CompletableFuture}，Geyser 会将它取消；之后再完成它不起作用。
     *
     * @param login 等待中的登录
     * @return 闸门的结果
     */
    @NonNull CompletionStage<LoginGateResult> onLogin(@NonNull GatedLogin login);

    /**
     * 每次登录的等待时限，从进入闸门时算起，默认 10 分钟。
     *
     * @return 等待时限，须为正数
     */
    default @NonNull Duration timeout() {
        return DEFAULT_TIMEOUT;
    }
}
