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

package org.geysermc.geyser.network;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.PlatformDependent;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * MINEZ：Netty 直接内存的用量。直接内存用完后连接收发不了数据（2026-11-18 正式服 15:07 起，基岩版玩家都因载入不了客户端模组被断开），
 * 而此前日志中什么都看不出。每 10 分钟写一行用量，超过上限的 80% 时改为警告，便于看出是在持续增长（泄漏）还是随人数起落。
 */
public final class NettyMemoryLog {
    /** 超过上限的这一比例时写警告。 */
    static final double WARNING_RATIO = 0.8;

    private NettyMemoryLog() {
    }

    /**
     * @return 启动时写一次的一行：所用的分配器与直接内存的上限。
     */
    public static String startupLine() {
        return "Netty allocator: " + ByteBufAllocator.DEFAULT.getClass().getSimpleName()
            + " (io.netty.allocator.type=" + System.getProperty("io.netty.allocator.type", "default") + ")"
            + ", direct memory limit " + mebibytes(PlatformDependent.maxDirectMemory()) + " MiB";
    }

    /**
     * @param sessions 当前的基岩版会话数（含尚未登录的）
     * @return 一行用量；Netty 不统计直接内存时为 null
     */
    public static @Nullable String usageLine(int sessions) {
        return line(PlatformDependent.usedDirectMemory(), PlatformDependent.maxDirectMemory(), sessions);
    }

    static @Nullable String line(long used, long max, int sessions) {
        if (used < 0 || max <= 0) {
            return null;
        }
        return "Netty direct memory: " + mebibytes(used) + " of " + mebibytes(max) + " MiB in use ("
            + Math.round(used * 100.0 / max) + "%), " + sessions + " Bedrock sessions";
    }

    /**
     * @return 用量是否已超过上限的 {@link #WARNING_RATIO}
     */
    public static boolean nearLimit() {
        long used = PlatformDependent.usedDirectMemory();
        long max = PlatformDependent.maxDirectMemory();
        return used >= 0 && max > 0 && used >= max * WARNING_RATIO;
    }

    private static long mebibytes(long bytes) {
        return bytes / (1024 * 1024);
    }
}
