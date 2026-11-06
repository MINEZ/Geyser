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

import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MINEZ：MINEZ 客户端模组的版本与要求的版本。
 */
class MinezClientModTest {
    private static ResourcePack pack(UUID uuid, int major, int minor, int patch) {
        ResourcePackManifest.Version version = mock(ResourcePackManifest.Version.class);
        when(version.major()).thenReturn(major);
        when(version.minor()).thenReturn(minor);
        when(version.patch()).thenReturn(patch);
        ResourcePackManifest.Header header = mock(ResourcePackManifest.Header.class);
        when(header.uuid()).thenReturn(uuid);
        when(header.version()).thenReturn(version);
        ResourcePackManifest manifest = mock(ResourcePackManifest.class);
        when(manifest.header()).thenReturn(header);
        ResourcePack pack = mock(ResourcePack.class);
        when(pack.manifest()).thenReturn(manifest);
        return pack;
    }

    @Test
    void parsesThreeNumbers() {
        assertEquals(new MinezClientMod.Version(1, 3, 0), MinezClientMod.Version.parse("1.3.0"));
        assertEquals(new MinezClientMod.Version(10, 0, 12), MinezClientMod.Version.parse("10.0.12"));
        assertEquals("1.3.0", MinezClientMod.Version.parse("1.3.0").toString());
        assertNull(MinezClientMod.Version.parse(null));
        assertNull(MinezClientMod.Version.parse(130));
        assertNull(MinezClientMod.Version.parse("1.3"));
        assertNull(MinezClientMod.Version.parse("1.3.0-beta"));
        assertNull(MinezClientMod.Version.parse(" 1.3.0"));
        assertNull(MinezClientMod.Version.parse("1.3.99999999999"));
    }

    @Test
    void comparesByNumbers() {
        MinezClientMod.Version required = new MinezClientMod.Version(1, 3, 0);
        assertTrue(MinezClientMod.Version.parse("1.3.0").compareTo(required) == 0);
        assertTrue(MinezClientMod.Version.parse("1.10.0").compareTo(required) > 0);
        assertTrue(MinezClientMod.Version.parse("2.0.0").compareTo(required) > 0);
        assertTrue(MinezClientMod.Version.parse("1.2.9").compareTo(required) < 0);
        assertTrue(MinezClientMod.Version.parse("0.9.9").compareTo(required) < 0);
    }

    @Test
    void theRequiredVersionIsThatOfTheSentPack() {
        ResourcePack other = pack(UUID.randomUUID(), 9, 9, 9);
        assertEquals(new MinezClientMod.Version(1, 3, 0),
            MinezClientMod.required(List.of(other, pack(MinezClientMod.PACK_UUID, 1, 3, 0))));
        // 没有下发本模组时不检查。
        assertNull(MinezClientMod.required(List.of(other)));
        assertNull(MinezClientMod.required(List.of()));
    }
}
