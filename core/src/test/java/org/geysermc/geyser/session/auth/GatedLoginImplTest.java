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

import org.geysermc.cumulus.form.Form;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.login.GatedLogin;
import org.geysermc.geyser.api.login.LoginGate;
import org.geysermc.geyser.api.login.LoginGateResult;
import org.geysermc.geyser.network.bedrock.netease.MinezClientMod;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.FormCache;
import org.geysermc.geyser.session.cache.WorldCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MINEZ：登录闸门的放行、断开、超时、玩家中途退出与注销。
 */
class GatedLoginImplTest {
    private final GeyserSession session = mock(GeyserSession.class);
    private final FormCache formCache = mock(FormCache.class);
    private final GeyserLogger logger = mock(GeyserLogger.class);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<LoginGateResult.Allow> recorded = new AtomicReference<>();
    private final List<Runnable> timeouts = new ArrayList<>();
    private final List<ScheduledFuture<?>> timeoutFutures = new ArrayList<>();
    private final List<Long> timeoutMillis = new ArrayList<>();
    private final List<GatedLogin.CancelReason> cancelled = new ArrayList<>();

    GatedLoginImplTest() {
        GeyserImpl geyser = mock(GeyserImpl.class);
        when(geyser.getLogger()).thenReturn(logger);
        when(session.getGeyser()).thenReturn(geyser);
        when(session.getFormCache()).thenReturn(formCache);
        when(session.getWorldCache()).thenReturn(mock(WorldCache.class));
        when(session.bedrockUsername()).thenReturn("测试玩家");
        when(session.isClosed()).thenAnswer(call -> closed.get());
        // 会话的事件循环：在测试中立即执行。
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(session).ensureInEventLoop(any());
        doAnswer(call -> {
            timeouts.add(call.getArgument(0));
            timeoutMillis.add(call.getArgument(1));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            timeoutFutures.add(future);
            return future;
        }).when(session).scheduleInEventLoop(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        doAnswer(call -> {
            recorded.set(call.getArgument(0));
            return null;
        }).when(session).setLoginGateResult(any());
        // 与 GeyserSession.disconnect 相同：先结束闸门，再关闭。
        doAnswer(call -> {
            GatedLoginImpl gated = session.getGatedLogin();
            if (gated != null && !closed.get()) {
                gated.sessionClosed();
            }
            closed.set(true);
            return null;
        }).when(session).disconnect(anyString());
    }

    private GatedLoginImpl start(LoginGate gate) {
        return start(gate, null);
    }

    private GatedLoginImpl start(LoginGate gate, MinezClientMod.Version requiredMod) {
        AtomicReference<GatedLoginImpl> holder = new AtomicReference<>();
        doAnswer(call -> {
            holder.set(call.getArgument(0));
            return null;
        }).when(session).setGatedLogin(any());
        when(session.getGatedLogin()).thenAnswer(call -> holder.get());
        GatedLoginImpl login = GatedLoginImpl.start(session, gate, "NE2147483648", requiredMod);
        assertSame(login, holder.get());
        return login;
    }

    /**
     * 返回一个由测试完成的结果，并记下取消的原因。
     */
    private LoginGate pendingGate(AtomicReference<CompletableFuture<LoginGateResult>> future, AtomicReference<GatedLogin> login) {
        return gated -> {
            login.set(gated);
            gated.whenCancelled(cancelled::add);
            CompletableFuture<LoginGateResult> result = new CompletableFuture<>();
            future.set(result);
            return result;
        };
    }

    @Test
    void allowsWithTheGivenUsername() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        GatedLoginImpl gated = start(pendingGate(future, login));

        assertTrue(gated.isPending());
        assertEquals("NE2147483648", login.get().defaultUsername());
        verify(session, never()).authenticate(anyString());

        Object attachment = new Object();
        future.get().complete(LoginGateResult.allow("张三", attachment));

        assertFalse(gated.isPending());
        verify(session).authenticate("张三");
        assertEquals("张三", recorded.get().javaUsername());
        assertSame(attachment, recorded.get().attachment());
        verify(formCache).discardForms();
        verify(timeoutFutures.get(0)).cancel(false);
        verify(session, never()).disconnect(anyString());
        assertTrue(cancelled.isEmpty());
    }

    @Test
    void allowsWithTheDefaultUsername() {
        start(gated -> CompletableFuture.completedFuture(LoginGateResult.allow()));

        verify(session).authenticate("NE2147483648");
        assertEquals("NE2147483648", recorded.get().javaUsername());
        assertNull(recorded.get().attachment());
    }

    @Test
    void disconnectsWithTheGivenReason() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        GatedLoginImpl gated = start(pendingGate(future, login));

        future.get().complete(LoginGateResult.disconnect("名字已被占用"));

        assertFalse(gated.isPending());
        verify(session).disconnect("名字已被占用");
        verify(session, never()).authenticate(anyString());
        assertNull(recorded.get());
        // 闸门自己给出的结果，不算取消。
        assertTrue(cancelled.isEmpty());
    }

    @Test
    void timesOutAfterTenMinutesByDefault() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        GatedLoginImpl gated = start(pendingGate(future, login));
        login.get().timeout(Duration.ofMinutes(10), "等待超时");

        assertEquals(List.of(600_000L, 600_000L), timeoutMillis);
        verify(timeoutFutures.get(0)).cancel(false);

        timeouts.get(1).run();

        assertFalse(gated.isPending());
        assertEquals(List.of(GatedLogin.CancelReason.TIMED_OUT), cancelled);
        assertTrue(future.get().isCancelled());
        verify(session).disconnect("等待超时");
        verify(formCache).discardForms();

        // 超时之后插件再给出结果也不起作用。
        future.get().complete(LoginGateResult.allow("张三"));
        verify(session, never()).authenticate(anyString());
    }

    @Test
    void usesTheTimeoutOfTheGate() {
        start(new LoginGate() {
            @Override
            public CompletionStage<LoginGateResult> onLogin(GatedLogin login) {
                return new CompletableFuture<>();
            }

            @Override
            public Duration timeout() {
                return Duration.ofSeconds(30);
            }
        });
        assertEquals(List.of(30_000L), timeoutMillis);
    }

    @Test
    void playerLeavingCancelsTheGate() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        GatedLoginImpl gated = start(pendingGate(future, login));

        // 玩家断开时，GeyserSession.disconnect 调用 sessionClosed。
        session.disconnect("Bedrock client disconnected");

        assertFalse(gated.isPending());
        assertEquals(List.of(GatedLogin.CancelReason.DISCONNECTED), cancelled);
        assertTrue(future.get().isCancelled());
        verify(timeoutFutures.get(0)).cancel(false);

        // 断开之后的表单与结果都不起作用。
        login.get().sendForm(mock(Form.class));
        verify(session, never()).sendForm(any(Form.class));
        future.get().obtrudeValue(LoginGateResult.allow("张三"));
        verify(session, never()).authenticate(anyString());
        verify(session, times(1)).disconnect(anyString());
    }

    @Test
    void listenerAddedAfterCancellationRunsAtOnce() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        start(pendingGate(future, login));
        session.disconnect("bye");

        List<GatedLogin.CancelReason> late = new ArrayList<>();
        login.get().whenCancelled(late::add);
        assertEquals(List.of(GatedLogin.CancelReason.DISCONNECTED), late);
        assertEquals(List.of(GatedLogin.CancelReason.DISCONNECTED), cancelled);
    }

    @Test
    void unregisteringDisconnectsWaitingPlayers() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        GatedLoginImpl gated = start(pendingGate(future, login));

        gated.unregistered();

        assertEquals(List.of(GatedLogin.CancelReason.UNREGISTERED), cancelled);
        verify(session).disconnect(GatedLoginImpl.CANNOT_CONNECT);
    }

    @Test
    void failingGateDisconnects() {
        start(gated -> {
            throw new IllegalStateException("boom");
        });
        verify(session).disconnect(GatedLoginImpl.CANNOT_CONNECT);
        verify(session, never()).authenticate(anyString());
    }

    @Test
    void exceptionalResultDisconnects() {
        start(gated -> CompletableFuture.failedFuture(new IllegalStateException("redis down")));
        verify(session).disconnect(GatedLoginImpl.CANNOT_CONNECT);
        verify(session, never()).authenticate(anyString());
    }

    @Test
    void nullResultDisconnects() {
        start(gated -> null);
        verify(session).disconnect(GatedLoginImpl.CANNOT_CONNECT);
    }

    @Test
    void formsAreSentOnlyWhilePending() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        start(pendingGate(future, login));

        Form form = mock(Form.class);
        login.get().sendForm(form);
        verify(session).sendForm(form);

        future.get().complete(LoginGateResult.allow());
        login.get().sendForm(form);
        verify(session, times(1)).sendForm(form);
    }

    @Test
    void usernamesAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> LoginGateResult.allow(""));
        assertThrows(IllegalArgumentException.class, () -> LoginGateResult.allow("12345678901234567"));
        assertThrows(IllegalArgumentException.class, () -> LoginGateResult.allow("有 空格"));
        assertThrows(IllegalArgumentException.class, () -> LoginGateResult.allow("tab\tname"));
        assertThrows(NullPointerException.class, () -> LoginGateResult.disconnect(null));
        assertEquals("测试玩家十六个字符的名字一二三", LoginGateResult.allow("测试玩家十六个字符的名字一二三").javaUsername());
        assertNull(LoginGateResult.allow(null, null).javaUsername());
    }

    @Test
    void timeoutMustBePositive() {
        AtomicReference<CompletableFuture<LoginGateResult>> future = new AtomicReference<>();
        AtomicReference<GatedLogin> login = new AtomicReference<>();
        start(pendingGate(future, login));
        assertThrows(IllegalArgumentException.class, () -> login.get().timeout(Duration.ZERO, null));
    }

    private static final MinezClientMod.Version MOD = new MinezClientMod.Version(1, 3, 0);

    /**
     * 记下被调用了几次、总是放行的闸门。
     */
    private static LoginGate countingGate(AtomicInteger calls) {
        return gated -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(LoginGateResult.allow());
        };
    }

    @Test
    void waitsForTheClientModBeforeCallingTheGate() {
        AtomicInteger calls = new AtomicInteger();
        GatedLoginImpl gated = start(countingGate(calls), MOD);

        // 先生成等待世界、开始等模组：闸门的时限与模组的时限各一个。
        assertEquals(0, calls.get());
        assertTrue(gated.isPending());
        verify(session).prepareForConfigurationForm();
        assertEquals(List.of(600_000L, MinezClientMod.READY_TIMEOUT.toMillis()), timeoutMillis);

        gated.clientModReady("1.3.0");

        assertEquals(1, calls.get());
        verify(timeoutFutures.get(1)).cancel(false);
        verify(session).authenticate("NE2147483648");
        // 等待世界在连接 Java 端之前就已生成，进入 Java 端时要补发 SessionJoinEvent。
        assertTrue(gated.takePendingJoinEvent());
        verify(session, never()).disconnect(anyString());

        // 重复的不起作用。
        gated.clientModReady("1.3.0");
        assertEquals(1, calls.get());
    }

    @Test
    void aNewerClientModPasses() {
        AtomicInteger calls = new AtomicInteger();
        GatedLoginImpl gated = start(countingGate(calls), MOD);
        gated.clientModReady("1.10.0");
        assertEquals(1, calls.get());
    }

    @Test
    void anOlderOrUnreadableClientModIsDisconnected() {
        AtomicInteger calls = new AtomicInteger();
        GatedLoginImpl gated = start(countingGate(calls), MOD);
        gated.clientModReady("1.2.9");

        assertFalse(gated.isPending());
        assertEquals(0, calls.get());
        verify(session).disconnect(MinezClientMod.OUTDATED);
        verify(timeoutFutures.get(1)).cancel(false);

        closed.set(false);
        GatedLoginImpl other = start(countingGate(calls), MOD);
        other.clientModReady(null);
        assertEquals(0, calls.get());
        verify(session, times(2)).disconnect(MinezClientMod.OUTDATED);
    }

    @Test
    void noClientModWithinTheTimeLimitIsDisconnected() {
        AtomicInteger calls = new AtomicInteger();
        GatedLoginImpl gated = start(countingGate(calls), MOD);

        timeouts.get(1).run();

        assertFalse(gated.isPending());
        verify(session).disconnect(MinezClientMod.MISSING);
        verify(timeoutFutures.get(0)).cancel(false);
        // 之后才到的不再调用闸门。
        gated.clientModReady("1.3.0");
        assertEquals(0, calls.get());
    }

    @Test
    void leavingWhileWaitingForTheClientModEndsTheWait() {
        AtomicInteger calls = new AtomicInteger();
        GatedLoginImpl gated = start(countingGate(calls), MOD);

        gated.sessionClosed();

        assertFalse(gated.isPending());
        verify(timeoutFutures.get(1)).cancel(false);
        gated.clientModReady("1.3.0");
        assertEquals(0, calls.get());
        // 断开已由会话处理，闸门不再断开一次。
        verify(session, never()).disconnect(anyString());
    }

    @Test
    void withoutARequirementTheClientModIsNotWaitedFor() {
        AtomicInteger calls = new AtomicInteger();
        GatedLoginImpl gated = start(countingGate(calls));

        assertEquals(1, calls.get());
        verify(session, never()).prepareForConfigurationForm();
        gated.clientModReady("0.0.1");
        verify(session, never()).disconnect(anyString());
        assertEquals(1, calls.get());
    }
}
