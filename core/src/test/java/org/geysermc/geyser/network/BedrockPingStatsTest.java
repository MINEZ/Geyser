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

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPingStatsTest {

    @Test
    void summarisesAndResets() throws Exception {
        BedrockPingStats stats = new BedrockPingStats();
        assertNull(stats.drainSummary());
        InetAddress a = InetAddress.getByName("7.136.137.201");
        InetAddress b = InetAddress.getByName("10.0.0.2");
        stats.record(a, 3, 500);
        stats.record(b, 4, 500);
        stats.record(a, 5, 500);
        assertEquals("Bedrock pings in the last minute: 3 from 2 addresses, last reply 5/500 players; 7.136.137.201 ×2, 10.0.0.2 ×1",
            stats.drainSummary());
        assertNull(stats.drainSummary());
    }

    @Test
    void listsAtMostTenSources() throws Exception {
        BedrockPingStats stats = new BedrockPingStats();
        for (int i = 1; i <= 12; i++) {
            stats.record(InetAddress.getByName("10.0.0." + i), 0, 1);
        }
        String summary = stats.drainSummary();
        assertTrue(summary.startsWith("Bedrock pings in the last minute: 12 from 12 addresses, last reply 0/1 players; "));
        assertTrue(summary.endsWith(", …"));
    }

    @Test
    void unknownSourcesAreCounted() {
        BedrockPingStats stats = new BedrockPingStats();
        stats.record(null, 2, 10);
        assertEquals("Bedrock pings in the last minute: 1 from 0 addresses, last reply 2/10 players; address unknown ×1",
            stats.drainSummary());
        assertNull(stats.drainSummary());
    }
}
