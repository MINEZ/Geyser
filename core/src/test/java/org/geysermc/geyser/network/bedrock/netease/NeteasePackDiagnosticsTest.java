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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeteasePackDiagnosticsTest {
    @Test
    void syncUsingModRetainsNestedNamesUuidsAndVersions() throws Exception {
        NeteasePyRpc.Message message = NeteasePyRpc.decode(MsgPackWriter.packet(new Object[]{"SyncUsingMod",
            new Object[]{Map.of("MinezClient", "1.18.6", "packs", new Object[]{
                Map.of("uuid", "03529531-08fb-4f27-b402-01d1fe545883", "version", new Object[]{1, 18, 6})})}}));
        String result = NeteasePackDiagnostics.describe(message.data());
        assertTrue(result.contains("MinezClient"), result);
        assertTrue(result.contains("1.18.6"), result);
        assertTrue(result.contains("03529531-08fb-4f27-b402-01d1fe545883"), result);
        assertTrue(result.contains("[1,18,6]"), result);
    }

    @Test
    void nestedCredentialAndPlayerFieldsAreRedacted() {
        String result = NeteasePackDiagnostics.describe(Map.of("value", List.of(Map.of(
            "netease_sid", "session-secret", "accessToken", "token-secret", "XUID", "player-secret",
            "content_key", "key-secret", "name", "MinezClient"))));
        assertFalse(result.contains("-secret"), result);
        assertTrue(result.contains("[redacted]"), result);
        assertTrue(result.contains("MinezClient"), result);
    }

    @Test
    void clientStringsCannotInjectLogLinesOrFormatting() {
        String result = NeteasePackDiagnostics.describe("mod\nforged\r\t\u001b\u00a7\u2028\u2029\"\\");
        assertFalse(result.contains("\n"), result);
        assertFalse(result.contains("\r"), result);
        assertFalse(result.contains("\u001b"), result);
        assertFalse(result.contains("\u00a7"), result);
        assertFalse(result.contains("\u2028"), result);
        assertTrue(result.contains("\\n"), result);
        assertTrue(result.contains("\\\""), result);
    }

    @Test
    void largeAndDeepClientValuesStayBounded() {
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            items.add("mod".repeat(1000));
        }
        String result = NeteasePackDiagnostics.describe(items);
        assertTrue(result.length() <= NeteasePackDiagnostics.MAX_OUTPUT);
        assertTrue(result.contains("[truncated]"));
        Object deep = "unreachable";
        for (int i = 0; i < 20; i++) {
            deep = List.of(deep);
        }
        String nested = NeteasePackDiagnostics.describe(deep);
        assertFalse(nested.contains("unreachable"));
        assertTrue(nested.contains("[truncated]"));
    }

    @Test
    void emptyListRemainsDistinguishableFromMissingValue() {
        assertEquals("[]", NeteasePackDiagnostics.describe(List.of()));
        assertEquals("{}", NeteasePackDiagnostics.describe(Map.of()));
        assertEquals("null", NeteasePackDiagnostics.describe(null));
    }
}
