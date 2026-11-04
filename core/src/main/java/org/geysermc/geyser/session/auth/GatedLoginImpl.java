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
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.connection.NeteaseIdentity;
import org.geysermc.geyser.api.login.GatedLogin;
import org.geysermc.geyser.api.login.LoginGate;
import org.geysermc.geyser.api.login.LoginGateResult;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * MINEZ：停在登录闸门中的一次登录，见 {@link LoginGate}。
 * <p>
 * 状态只由 {@link State#PENDING} 变为三种结束状态之一，以比较并交换完成，所以放行、断开、超时、玩家断开与注销
 * 无论在哪个线程同时发生，只有最先的那个生效。表单的发送与关闭、放行后连接 Java 端都放到会话的事件循环中执行。
 */
public final class GatedLoginImpl implements GatedLogin {
    /**
     * 闸门出错或被注销时断开的提示，与上游连接失败时相同（基岩版客户端自带的翻译键）。
     */
    static final String CANNOT_CONNECT = "disconnectionScreen.internalError.cantConnect";

    private enum State {
        PENDING,
        ALLOWED,
        DISCONNECTED,
        CANCELLED
    }

    private final GeyserSession session;
    private final LoginGate gate;
    private final String defaultUsername;
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private final List<Consumer<CancelReason>> cancelListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean joinEventPending = new AtomicBoolean();
    private volatile @Nullable CancelReason cancelReason;
    private volatile @Nullable CompletionStage<LoginGateResult> result;
    private volatile @Nullable String timeoutMessage;
    private @Nullable ScheduledFuture<?> timeoutTask;

    private GatedLoginImpl(GeyserSession session, LoginGate gate, String defaultUsername) {
        this.session = session;
        this.gate = gate;
        this.defaultUsername = defaultUsername;
    }

    /**
     * 让会话进入闸门：记到会话上，开始计时，调用闸门。
     *
     * @param defaultUsername 没有闸门时连接 Java 端所用的名字
     */
    public static GatedLoginImpl start(GeyserSession session, LoginGate gate, String defaultUsername) {
        GatedLoginImpl login = new GatedLoginImpl(session, gate, defaultUsername);
        session.setGatedLogin(login);
        login.begin();
        return login;
    }

    private void begin() {
        GeyserLogger logger = session.getGeyser().getLogger();
        Duration timeout;
        try {
            timeout = gate.timeout();
        } catch (Throwable t) {
            logger.error("The login gate " + gate.getClass().getName() + " failed to give a timeout, using " + LoginGate.DEFAULT_TIMEOUT, t);
            timeout = null;
        }
        scheduleTimeout(timeout == null || timeout.isNegative() || timeout.isZero() ? LoginGate.DEFAULT_TIMEOUT : timeout);
        logger.debug(session.bedrockUsername() + " is waiting in the login gate");

        CompletionStage<LoginGateResult> stage;
        try {
            stage = gate.onLogin(this);
        } catch (Throwable t) {
            fail(t);
            return;
        }
        if (stage == null) {
            fail(new NullPointerException("The login gate returned null"));
            return;
        }
        this.result = stage;
        if (state.get() != State.PENDING) {
            // 在 onLogin 期间就已结束（例如玩家断开）
            cancelStage(stage);
            return;
        }
        stage.whenComplete(this::complete);
    }

    private void complete(@Nullable LoginGateResult result, @Nullable Throwable error) {
        if (state.get() != State.PENDING) {
            // 已经结束，例如被我们取消
            return;
        }
        if (error != null) {
            fail(error);
        } else if (result instanceof LoginGateResult.Allow allow) {
            allow(allow);
        } else if (result instanceof LoginGateResult.Disconnect disconnect) {
            disconnect(disconnect.reason());
        } else {
            fail(new NullPointerException("The login gate completed with null"));
        }
    }

    private void allow(LoginGateResult.Allow allow) {
        if (!state.compareAndSet(State.PENDING, State.ALLOWED)) {
            return;
        }
        cancelTimeout();
        String username = allow.javaUsername() != null ? allow.javaUsername() : defaultUsername;
        LoginGateResult.Allow effective = new LoginGateResult.Allow(username, allow.attachment());
        session.ensureInEventLoop(() -> {
            if (session.isClosed()) {
                return;
            }
            // 先记下结果再连接，代理在登录事件中按会话 ID 查到的会话已带着它
            session.setLoginGateResult(effective);
            session.getFormCache().discardForms();
            session.getGeyser().getLogger().info(session.bedrockUsername() + " passed the login gate and joins the Java server as " + username);
            session.authenticate(username);
        });
    }

    private void disconnect(String reason) {
        if (!state.compareAndSet(State.PENDING, State.DISCONNECTED)) {
            return;
        }
        cancelTimeout();
        session.getGeyser().getLogger().debug("The login gate disconnected " + session.bedrockUsername() + ": " + reason);
        session.ensureInEventLoop(() -> {
            session.getFormCache().discardForms();
            session.disconnect(reason);
        });
    }

    private void fail(Throwable error) {
        if (!state.compareAndSet(State.PENDING, State.DISCONNECTED)) {
            return;
        }
        cancelTimeout();
        session.getGeyser().getLogger().error("The login gate " + gate.getClass().getName() + " failed for " + session.bedrockUsername()
            + ", disconnecting them", error);
        session.ensureInEventLoop(() -> {
            session.getFormCache().discardForms();
            session.disconnect(CANNOT_CONNECT);
        });
    }

    /**
     * 没有结果就结束：通知插件、取消插件的结果。
     *
     * @return 是否由这次调用结束
     */
    private boolean cancel(CancelReason reason) {
        if (!state.compareAndSet(State.PENDING, State.CANCELLED)) {
            return false;
        }
        cancelReason = reason;
        cancelTimeout();
        CompletionStage<LoginGateResult> stage = this.result;
        if (stage != null) {
            cancelStage(stage);
        }
        for (Consumer<CancelReason> listener : cancelListeners) {
            if (cancelListeners.remove(listener)) {
                notify(listener, reason);
            }
        }
        session.getGeyser().getLogger().debug("The login gate of " + session.bedrockUsername() + " ended without a result: " + reason);
        return true;
    }

    private void notify(Consumer<CancelReason> listener, CancelReason reason) {
        try {
            listener.accept(reason);
        } catch (Throwable t) {
            session.getGeyser().getLogger().error("A cancel listener of the login gate failed", t);
        }
    }

    private static void cancelStage(CompletionStage<LoginGateResult> stage) {
        if (stage instanceof CompletableFuture<LoginGateResult> future) {
            future.cancel(false);
        }
    }

    /**
     * 会话关闭时由 {@link GeyserSession#disconnect} 调用。
     */
    public void sessionClosed() {
        cancel(CancelReason.DISCONNECTED);
    }

    /**
     * 闸门被注销时调用：结束并断开玩家。
     */
    public void unregistered() {
        if (cancel(CancelReason.UNREGISTERED)) {
            session.ensureInEventLoop(() -> {
                session.getFormCache().discardForms();
                session.disconnect(CANNOT_CONNECT);
            });
        }
    }

    private void timedOut() {
        if (cancel(CancelReason.TIMED_OUT)) {
            String message = timeoutMessage;
            session.getGeyser().getLogger().info(session.bedrockUsername() + " waited in the login gate for too long and was disconnected");
            session.getFormCache().discardForms();
            session.disconnect(message != null ? message
                : GeyserLocale.getPlayerLocaleString("geyser.network.disconnect.timed_out", session.locale()));
        }
    }

    private synchronized void scheduleTimeout(Duration timeout) {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
        // 在会话的事件循环中执行；会话已关闭时不执行
        timeoutTask = session.scheduleInEventLoop(this::timedOut, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
    }

    /**
     * @return 是否为这个闸门的登录
     */
    public boolean isOf(LoginGate gate) {
        return this.gate == gate;
    }

    /**
     * 由 GeyserSession 在闸门中为显示表单而生成等待世界时调用。
     */
    public void waitingWorldShown() {
        joinEventPending.set(true);
    }

    /**
     * 闸门显示过表单时，Geyser 在连接 Java 端之前就已生成等待世界，客户端初始化时还没有登录 Java 端，
     * 照常在那时触发的 SessionJoinEvent 没有触发。第一次登录 Java 端时由 JavaLoginTranslator 补上。
     *
     * @return 是否需要补发 SessionJoinEvent，只返回一次 true
     */
    public boolean takePendingJoinEvent() {
        return joinEventPending.compareAndSet(true, false);
    }

    @Override
    public @NonNull GeyserConnection connection() {
        return session;
    }

    @Override
    public @Nullable NeteaseIdentity neteaseIdentity() {
        return session.neteaseIdentity();
    }

    @Override
    public @NonNull String defaultUsername() {
        return defaultUsername;
    }

    @Override
    public boolean isPending() {
        return state.get() == State.PENDING;
    }

    @Override
    public void sendForm(@NonNull Form form) {
        Objects.requireNonNull(form, "form");
        session.ensureInEventLoop(() -> {
            if (!isPending() || session.isClosed()) {
                return;
            }
            // 下游还不存在时，GeyserSession.sendForm 先生成等待世界
            session.sendForm(form);
        });
    }

    @Override
    public void sendForm(@NonNull FormBuilder<?, ?, ?> formBuilder) {
        sendForm(formBuilder.build());
    }

    @Override
    public void closeForm() {
        session.ensureInEventLoop(() -> {
            if (isPending() && !session.isClosed()) {
                session.closeForm();
            }
        });
    }

    @Override
    public void timeout(@NonNull Duration timeout, @Nullable String message) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("The timeout must be positive: " + timeout);
        }
        if (!isPending()) {
            return;
        }
        this.timeoutMessage = message;
        scheduleTimeout(timeout);
        if (!isPending()) {
            // 与结束同时发生
            cancelTimeout();
        }
    }

    @Override
    public void whenCancelled(@NonNull Consumer<CancelReason> listener) {
        Objects.requireNonNull(listener, "listener");
        cancelListeners.add(listener);
        CancelReason reason = cancelReason;
        if (reason != null && cancelListeners.remove(listener)) {
            notify(listener, reason);
        }
    }

    @Override
    public String toString() {
        return "GatedLogin[" + session.bedrockUsername() + ", " + state.get() + "]";
    }
}
