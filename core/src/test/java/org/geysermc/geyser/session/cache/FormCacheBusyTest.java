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

package org.geysermc.geyser.session.cache;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.data.ModalFormCancelReason;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.UpstreamSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MINEZ：客户端以“正忙”拒绝表单时，一直重新发送，直到忙了 10 分钟才照关闭处理。
 */
class FormCacheBusyTest {
    private final GeyserSession session = mock(GeyserSession.class);
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private final AtomicInteger sent = new AtomicInteger();
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final FormCache cache = new FormCache(session);

    FormCacheBusyTest() {
        GeyserImpl geyser = mock(GeyserImpl.class);
        when(geyser.getLogger()).thenReturn(mock(GeyserLogger.class));
        when(session.getGeyser()).thenReturn(geyser);
        when(session.bedrockUsername()).thenReturn("测试玩家");
        UpstreamSession upstream = mock(UpstreamSession.class);
        when(upstream.isInitialized()).thenReturn(true);
        when(session.getUpstream()).thenReturn(upstream);
        doAnswer(call -> {
            BedrockPacket packet = call.getArgument(0);
            if (packet instanceof ModalFormRequestPacket) {
                sent.incrementAndGet();
            }
            return null;
        }).when(session).sendUpstreamPacket(any());
        doAnswer(call -> {
            scheduled.add(call.getArgument(0));
            delays.add(call.getArgument(1));
            return null;
        }).when(session).scheduleInEventLoop(any(), anyLong(), eq(TimeUnit.MILLISECONDS));
        cache.setClock(now::get);
    }

    private static ModalFormResponsePacket busy(int formId) {
        ModalFormResponsePacket response = new ModalFormResponsePacket();
        response.setFormId(formId);
        response.setCancelReason(Optional.of(ModalFormCancelReason.USER_BUSY));
        return response;
    }

    /**
     * 客户端回复正忙，并执行 Geyser 排下的重新发送。
     */
    private void rejectAndResend() {
        cache.handleResponse(busy(0));
        scheduled.removeLast().run();
    }

    @Test
    void keepsResendingWhileTheClientIsBusy() {
        AtomicInteger closed = new AtomicInteger();
        cache.showForm(ModalForm.builder().title("创建角色").content("").button1("是").button2("否")
            .closedOrInvalidResultHandler(closed::incrementAndGet).build());
        assertEquals(1, sent.get());

        // 前 20 次每 250 毫秒重新发送一次，此后每秒一次；忙了 6 秒以上（审核账号第一次进入时）远不到上限。
        for (int i = 0; i < 30; i++) {
            now.addAndGet(300);
            rejectAndResend();
        }
        assertEquals(31, sent.get());
        assertEquals(250L, delays.get(0));
        assertEquals(250L, delays.get(19));
        assertEquals(1000L, delays.get(20));
        assertEquals(0, closed.get());
        assertTrue(cache.hasFormOpen());

        // 忙到第一次被拒后 10 分钟，才照关闭处理。
        now.addAndGet(TimeUnit.MINUTES.toMillis(10));
        cache.handleResponse(busy(0));
        assertEquals(1, closed.get());
        assertFalse(cache.hasFormOpen());
    }

    @Test
    void discardingTheFormsStopsTheResending() {
        cache.showForm(ModalForm.builder().title("创建角色").content("").button1("是").button2("否").build());
        cache.handleResponse(busy(0));
        cache.discardForms();
        // 闸门结束、表单已丢弃：排下的重新发送不再发出。
        scheduled.removeLast().run();
        assertEquals(1, sent.get());
    }
}
