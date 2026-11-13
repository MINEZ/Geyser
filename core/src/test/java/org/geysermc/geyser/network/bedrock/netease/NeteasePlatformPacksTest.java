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

import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.geysermc.geyser.session.auth.NeteaseProfile;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NeteasePlatformPacksTest {
    private static final String MOD = "a858dec6-447c-46c8-ae1f-dd384ae6dd27";
    private static final String BEHAVIOR = "03529531-08fb-4f27-b402-01d1fe545883";
    private static final String INTEGRATED = "2254393d-8430-45b0-838a-bd397828c765";
    private static NeteasePlatformPacks packs() {
        return NeteasePlatformPacks.parse(List.of(MOD + "_1.18.6"), List.of(BEHAVIOR + "_1.18.6"));
    }

    @Test
    void replacesOldVersionAndAddsBehaviorWithoutDuplicatingOrRemovingOtherPacks() {
        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.getResourcePacks().add(new ResourcePackStackPacket.Entry(MOD, "1.18.5", ""));
        packet.getResourcePacks().add(new ResourcePackStackPacket.Entry(INTEGRATED, "1.1.5", ""));
        packs().applyStack(packet);
        packs().applyStack(packet);
        assertEquals(List.of(new ResourcePackStackPacket.Entry(MOD, "1.18.6", ""),
            new ResourcePackStackPacket.Entry(INTEGRATED, "1.1.5", "")), packet.getResourcePacks());
        assertEquals(List.of(new ResourcePackStackPacket.Entry(BEHAVIOR, "1.18.6", "")), packet.getBehaviorPacks());
    }

    @Test
    void infoEnablesScriptsButNeverAdvertisesPlatformDownloadEntries() {
        ResourcePacksInfoPacket packet = new ResourcePacksInfoPacket();
        packs().applyInfoFlags(packet);
        assertTrue(packet.isScriptingEnabled());
        assertTrue(packet.isHasAddonPacks());
        assertTrue(packet.getResourcePackInfos().isEmpty());
        assertTrue(packs().isPlatformRequest(MOD + "_1.18.5"));
        assertTrue(packs().isPlatformRequest(BEHAVIOR + "_1.18.6"));
        assertFalse(packs().isPlatformRequest(INTEGRATED + "_1.1.5"));
    }

    @Test
    void platformVersionStillRequiresRealClientReadyEvenWithoutServerModPack() {
        assertEquals(new MinezClientMod.Version(1, 18, 6), packs().requiredClientMod(null));
        assertEquals(new MinezClientMod.Version(1, 18, 6), packs().requiredClientMod(new MinezClientMod.Version(1, 18, 5)));
        var legacy = new MinezClientMod.Version(1, 18, 5);
        assertSame(legacy, NeteasePlatformPacks.EMPTY.requiredClientMod(legacy));
    }

    @Test
    void defaultsLeaveFlagsAndStackUnchanged() {
        ResourcePacksInfoPacket info = new ResourcePacksInfoPacket();
        NeteasePlatformPacks.EMPTY.applyInfoFlags(info);
        assertFalse(info.isScriptingEnabled());
        assertFalse(info.isHasAddonPacks());
        ResourcePackStackPacket stack = new ResourcePackStackPacket();
        var original = new ResourcePackStackPacket.Entry(MOD, "1.18.5", "");
        stack.getResourcePacks().add(original);
        NeteasePlatformPacks.EMPTY.applyStack(stack);
        assertEquals(List.of(original), stack.getResourcePacks());
        assertTrue(stack.getBehaviorPacks().isEmpty());
    }

    @Test
    void probeHasNoDownloadSourceOrBehaviorEntryAndReplacesOldResourceMetadata() {
        ResourcePacksInfoPacket info = new ResourcePacksInfoPacket();
        info.getResourcePackInfos().add(new ResourcePacksInfoPacket.Entry(java.util.UUID.fromString(MOD),
            "1.18.5", 123, "old-key", "old-subpack", "old-content", true, false, false, "https://example.invalid/old"));
        packs().addResourceProbeEntries(info);
        packs().addResourceProbeEntries(info);
        assertEquals(1, info.getResourcePackInfos().size());
        var entry = info.getResourcePackInfos().getFirst();
        assertEquals(MOD, entry.getPackId().toString());
        assertEquals("1.18.6", entry.getPackVersion());
        assertEquals(0, entry.getPackSize());
        assertEquals("", entry.getContentKey());
        assertEquals("", entry.getSubPackName());
        assertEquals(MOD, entry.getContentId());
        assertEquals("", entry.getCdnUrl());
        assertFalse(entry.isScripting());
        assertFalse(entry.isAddonPack());
    }

    @Test
    void probeRequiresAnExactOfficialPlayerMatchAndResourceDeclarations() {
        var target = new NeteaseProfile("2230082046", "test", "test", null, "obt", null, null, null,
            null, null, "", null, true, false);
        assertTrue(packs().probes(target, "2230082046"));
        assertFalse(packs().probes(target, "2230082047"));
        assertFalse(packs().probes(target, ""));
        assertFalse(packs().probes(target, null));
        assertFalse(packs().probes(null, "2230082046"));
        assertFalse(NeteasePlatformPacks.EMPTY.probes(target, "2230082046"));
        assertFalse(packs().probes(profile("expr1", false), "2230082046"));
        assertFalse(packs().probes(profile("obt", true), "2230082046"));
    }

    @Test
    void malformedAndDuplicateDeclarationsAreRejected() {
        for (String bad : List.of("", "bad_1.18.6", MOD + "_1.18", MOD + "_-1.18.6", MOD + "_1.18.6_extra")) {
            assertThrows(IllegalArgumentException.class, () -> NeteasePlatformPacks.parse(List.of(bad), List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> NeteasePlatformPacks.parse(List.of(MOD + "_1.18.6"), List.of(MOD + "_1.18.5")));
        assertThrows(IllegalArgumentException.class, () -> NeteasePlatformPacks.parse(Collections.nCopies(65, MOD + "_1.18.6"), List.of()));
    }

    @Test
    void appliesOnlyToReleaseNeteaseClients() {
        assertFalse(NeteasePlatformPacks.appliesTo(null));
        assertTrue(NeteasePlatformPacks.appliesTo(profile("obt", false)));
        assertFalse(NeteasePlatformPacks.appliesTo(profile("expr1", false)));
        assertFalse(NeteasePlatformPacks.appliesTo(profile("obt", true)));
    }

    private static NeteaseProfile profile(String env, boolean developer) {
        return new NeteaseProfile(null, "test", "test", null, env, null, null, null, null, null, "", null, !developer, developer);
    }
}
