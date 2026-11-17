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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MINEZ：基岩版端口收到的状态查询（ping）的统计，用于排查网易“网络游戏”专区的在线人数。
 * <p>
 * 网易专区的人数为 0，而 Java 版端口的查询在 Velocity 的日志中可以看到网易的中转定时来查，基岩版端口的查询却没有任何记录，
 * 无从知道网易是否来查、查到的人数是多少。这里按来源地址记下查询的次数与最后一次回复的人数，每分钟在日志中写一行汇总，
 * 没有查询的那一分钟不写。
 */
public final class BedrockPingStats {
    /** 每分钟的汇总中最多列出的来源，其余只计入总数。 */
    static final int MAX_SOURCES = 10;

    private final Map<InetAddress, AtomicInteger> counts = new ConcurrentHashMap<>();
    /** 来源未知（或不记录 IP）的查询次数。 */
    private final AtomicInteger unknown = new AtomicInteger();
    private volatile int lastPlayerCount = -1;
    private volatile int lastMaxPlayers = -1;

    /**
     * 记下一次查询。
     *
     * @param source      查询的来源地址，未知或不记录 IP 时为 null（只计次数）
     * @param playerCount 回复的在线人数
     * @param maxPlayers  回复的最大人数
     */
    public void record(InetAddress source, int playerCount, int maxPlayers) {
        if (source != null) {
            counts.computeIfAbsent(source, ignored -> new AtomicInteger()).incrementAndGet();
        } else {
            unknown.incrementAndGet();
        }
        lastPlayerCount = playerCount;
        lastMaxPlayers = maxPlayers;
    }

    /**
     * 取出这一分钟的汇总并清零。
     *
     * @return 汇总的一行文字，没有查询时为 null
     */
    public String drainSummary() {
        List<Map.Entry<InetAddress, Integer>> entries = new ArrayList<>();
        for (InetAddress address : new ArrayList<>(counts.keySet())) {
            AtomicInteger count = counts.remove(address);
            if (count != null && count.get() > 0) {
                entries.add(Map.entry(address, count.get()));
            }
        }
        int unknownCount = unknown.getAndSet(0);
        if (entries.isEmpty() && unknownCount == 0) {
            return null;
        }
        entries.sort(Map.Entry.<InetAddress, Integer>comparingByValue(Comparator.reverseOrder())
            .thenComparing(e -> e.getKey().getHostAddress()));
        int total = entries.stream().mapToInt(Map.Entry::getValue).sum() + unknownCount;
        StringBuilder line = new StringBuilder("Bedrock pings in the last minute: ").append(total)
            .append(" from ").append(entries.size()).append(entries.size() == 1 ? " address" : " addresses")
            .append(", last reply ").append(lastPlayerCount).append('/').append(lastMaxPlayers).append(" players");
        if (!entries.isEmpty()) {
            line.append("; ");
        }
        for (int i = 0; i < Math.min(MAX_SOURCES, entries.size()); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(entries.get(i).getKey().getHostAddress()).append(" ×").append(entries.get(i).getValue());
        }
        if (entries.size() > MAX_SOURCES) {
            line.append(", …");
        }
        if (unknownCount > 0) {
            line.append(entries.isEmpty() ? "; " : ", ").append("address unknown ×").append(unknownCount);
        }
        return line.toString();
    }
}
