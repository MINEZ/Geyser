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
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.connection.NeteaseIdentity;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * MINEZ：停在登录闸门中的一次登录，见 {@link LoginGate}。
 * <p>
 * 这时会话还没有连接 Java 端：{@link GeyserConnection#javaUsername()}、{@link GeyserConnection#javaUuid()} 等为 null。
 * 本接口的方法都可以在任意线程调用。
 */
public interface GatedLogin {

    /**
     * @return 这次登录的会话
     */
    @NonNull GeyserConnection connection();

    /**
     * 同 {@code connection().neteaseIdentity()}。
     *
     * @return 网易身份，不是网易中国版客户端时为 null
     */
    @Nullable NeteaseIdentity neteaseIdentity();

    /**
     * 没有闸门时 Geyser 连接 Java 端所用的名字，也是 {@link LoginGateResult#allow()} 所用的名字。
     * 网易中国版客户端为昵称（不合 Java 版的规则时为 {@code NE} 加 UID），开发者身份为 {@code DEV} 加昵称，
     * 其余为基岩版的玩家名。
     *
     * @return 默认的 Java 端名字
     */
    @NonNull String defaultUsername();

    /**
     * @return 是否仍在等待闸门的结果
     */
    boolean isPending();

    /**
     * 向玩家显示表单。第一次显示时，Geyser 先生成一个空白的等待世界，客户端加载完毕后才显示出来。
     * 闸门已经结束时不做任何事。闸门结束（放行、断开、超时或取消）时，仍然打开着的表单会被关掉，不调用它们的处理器。
     *
     * @param form 表单
     */
    void sendForm(@NonNull Form form);

    /**
     * 同 {@link #sendForm(Form)}。
     *
     * @param formBuilder 表单的构造器
     */
    void sendForm(@NonNull FormBuilder<?, ?, ?> formBuilder);

    /**
     * 关闭玩家正在看的表单，表单的关闭处理器照常被调用。
     */
    void closeForm();

    /**
     * 从现在起重新计算等待时限。
     *
     * @param timeout 新的等待时限，须为正数
     * @param message 超时断开时的提示，为 null 时用 Geyser 的默认提示
     */
    void timeout(@NonNull Duration timeout, @Nullable String message);

    /**
     * 登记闸门没有得到结果就结束时的处理器。已经这样结束时立即调用；闸门得到了结果时不会调用。
     * 处理器在结束的那个线程上调用。
     *
     * @param listener 处理器
     */
    void whenCancelled(@NonNull Consumer<CancelReason> listener);

    /**
     * 闸门没有得到结果就结束的原因。
     */
    enum CancelReason {
        /**
         * 玩家断开了连接，或者会话因其他原因（例如 Geyser 关闭）被关闭。
         */
        DISCONNECTED,
        /**
         * 等待超时，Geyser 已断开这名玩家。
         */
        TIMED_OUT,
        /**
         * 闸门被注销，Geyser 已断开这名玩家。
         */
        UNREGISTERED
    }
}
