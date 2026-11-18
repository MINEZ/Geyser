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

package org.geysermc.geyser.network.bedrock.netease;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * MINEZ：网易中转的地址包与基岩版连接的每分钟汇总，用来判断正式服是否收到地址包、玩家的连接是否取到了真实地址。
 * 只有次数与原因，不写任何地址，所以不论是否开启 {@code log-player-ip-addresses} 都可以写进日志。
 */
public final class NeteaseRelayStats {
    /** 名单中的中转发来、读出了玩家地址的地址包（含每 2 秒一次的健康检查）。 */
    private final AtomicInteger read = new AtomicInteger();
    /** 名单中的中转发来、但格式不对的 0x7f 包。 */
    private final AtomicInteger unreadable = new AtomicInteger();
    /** 不在名单中的地址发来的 0x7f 包，交给 RakNet 丢弃。 */
    private final AtomicInteger unlisted = new AtomicInteger();
    /** 名单中的中转转发的基岩版连接：取到了玩家地址的。 */
    private final AtomicInteger matched = new AtomicInteger();
    /** 名单中的中转转发的基岩版连接：没有取到玩家地址的。 */
    private final AtomicInteger unmatched = new AtomicInteger();
    /** 其中同一中转的另一端口在 30 秒内发过地址包的，说明中转从别的端口发地址包。 */
    private final AtomicInteger otherPort = new AtomicInteger();
    /** 不经名单中的中转的基岩版连接。 */
    private final AtomicInteger direct = new AtomicInteger();
    /** 最近一个格式不对的包为什么读不了，不含地址。 */
    private volatile @Nullable String lastUnreadable;

    void addressPacketRead() {
        read.incrementAndGet();
    }

    void addressPacketUnreadable(String reason) {
        unreadable.incrementAndGet();
        lastUnreadable = reason;
    }

    void addressPacketFromUnlisted() {
        unlisted.incrementAndGet();
    }

    void relayedConnection(boolean found, boolean addressFromOtherPort) {
        (found ? matched : unmatched).incrementAndGet();
        if (!found && addressFromOtherPort) {
            otherPort.incrementAndGet();
        }
    }

    void directConnection() {
        direct.incrementAndGet();
    }

    /**
     * 取出这一分钟的汇总并清零。
     *
     * @return 汇总的一行文字，这一分钟什么都没有时为 null
     */
    public @Nullable String drainSummary() {
        int read = this.read.getAndSet(0);
        int unreadable = this.unreadable.getAndSet(0);
        int unlisted = this.unlisted.getAndSet(0);
        int matched = this.matched.getAndSet(0);
        int unmatched = this.unmatched.getAndSet(0);
        int otherPort = this.otherPort.getAndSet(0);
        int direct = this.direct.getAndSet(0);
        String reason = this.lastUnreadable;
        this.lastUnreadable = null;
        if (read + unreadable + unlisted + matched + unmatched + direct == 0) {
            return null;
        }
        StringBuilder line = new StringBuilder("NetEase relay in the last minute: ")
            .append(read).append(" address packets read, ")
            .append(unreadable).append(" unreadable");
        if (reason != null) {
            line.append(" (last: ").append(reason).append(')');
        }
        line.append(", ").append(unlisted).append(" from unlisted addresses; Bedrock connections: ")
            .append(matched).append(" with the player's address, ")
            .append(unmatched).append(" via a relay without it");
        if (otherPort > 0) {
            line.append(" (").append(otherPort).append(" of them with an address packet from the same relay on another port)");
        }
        line.append(", ").append(direct).append(" not via a listed relay");
        return line.toString();
    }
}
