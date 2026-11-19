/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.network.bedrock;

import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * MINEZ：发给网易 1.21.130 的实体列表中，硫方怪以史莱姆为基础实体，其他实体不变。
 */
class LegacyEntityIdentifiersTest {
    @Test
    void sulfurCubeIsBasedOnSlimeForLegacyClients() throws Exception {
        NbtMap identifiers;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("bedrock/entity_identifiers.dat");
             NBTInputStream nbt = NbtUtils.createNetworkReader(stream, true, true)) {
            identifiers = (NbtMap) nbt.readTag();
        }
        NbtMap legacy = LegacyEntityIdentifiers.forProtocol(Bedrock_v898.CODEC.getProtocolVersion(), identifiers);
        List<NbtMap> before = identifiers.getList("idlist", NbtType.COMPOUND);
        List<NbtMap> after = legacy.getList("idlist", NbtType.COMPOUND);
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            String id = before.get(i).getString("id");
            String expected = id.equals("minecraft:sulfur_cube") ? "minecraft:slime" : before.get(i).getString("bid");
            assertEquals(expected, after.get(i).getString("bid"), id);
            assertEquals(before.get(i).getInt("rid"), after.get(i).getInt("rid"), id);
        }
        assertEquals(1, after.stream().filter(entry -> entry.getString("id").equals("minecraft:sulfur_cube")).count());
        // 26.40 起原样发
        assertSame(identifiers, LegacyEntityIdentifiers.forProtocol(GameProtocol.DEFAULT_BEDROCK_PROTOCOL, identifiers));
    }
}
