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
 * MINEZ：经网易中转进入的基岩版连接是否从 noa 查到了玩家的真实地址，每分钟汇总一次，见 {@link NeteaseRealAddress}。
 * 只有次数与原因，不写任何地址，所以不论是否开启 {@code log-player-ip-addresses} 都可以写进日志。
 */
public final class NeteaseRelayStats {
    /** 经名单中的中转、查到了玩家地址的连接。 */
    private final AtomicInteger matched = new AtomicInteger();
    /** 经名单中的中转、noa 查不到，用了同一中转地址:端口此前查到的玩家地址的连接（多为断开后很快重连）。 */
    private final AtomicInteger remembered = new AtomicInteger();
    /** 经名单中的中转、没有查到玩家地址的连接。 */
    private final AtomicInteger unmatched = new AtomicInteger();
    /** 不经名单中的中转的连接。 */
    private final AtomicInteger direct = new AtomicInteger();
    /** 最近一次没有查到的原因，不含地址。 */
    private volatile @Nullable String lastProblem;

    void relayedConnection(boolean found, @Nullable String problem) {
        (found ? matched : unmatched).incrementAndGet();
        if (!found && problem != null) {
            lastProblem = problem;
        }
    }

    void rememberedConnection() {
        remembered.incrementAndGet();
    }

    void directConnection() {
        direct.incrementAndGet();
    }

    /**
     * 取出这一分钟的汇总并清零。
     *
     * @return 汇总的一行文字，这一分钟没有基岩版连接时为 null
     */
    public @Nullable String drainSummary() {
        int matched = this.matched.getAndSet(0);
        int remembered = this.remembered.getAndSet(0);
        int unmatched = this.unmatched.getAndSet(0);
        int direct = this.direct.getAndSet(0);
        String problem = this.lastProblem;
        this.lastProblem = null;
        if (matched + remembered + unmatched + direct == 0) {
            return null;
        }
        StringBuilder line = new StringBuilder("NetEase relay in the last minute: Bedrock connections: ")
            .append(matched).append(" with the player's address from noa, ")
            .append(remembered).append(" with the address noa gave earlier for the same relay port, ")
            .append(unmatched).append(" via a relay without it");
        if (unmatched > 0 && problem != null) {
            line.append(" (last: ").append(problem).append(')');
        }
        line.append(", ").append(direct).append(" not via a listed relay");
        return line.toString();
    }
}
