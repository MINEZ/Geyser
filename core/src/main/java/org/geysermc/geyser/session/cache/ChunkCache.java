/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.chunk.GeyserChunk;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;

public class ChunkCache {
    @Getter
    private final boolean cache;
    private final Long2ObjectMap<GeyserChunk> chunks;
    /**
     * MINEZ：各区块的 Java 版生物群系（每个区段一个调色板），见 {@link #addToCache(int, int, DataPalette[], DataPalette[])}。
     */
    private final Long2ObjectMap<DataPalette[]> biomes;

    @Setter
    private int minY;
    @Setter
    private int heightY;
    /**
     * MINEZ：杨树树叶、烈性硫黄等由 Java 版客户端自己产生的效果，见 {@link AmbientBlockEffects}。
     */
    @Getter
    private final AmbientBlockEffects ambientEffects;

    public ChunkCache(GeyserSession session) {
        this.cache = !session.getGeyser().getWorldManager().hasOwnChunkCache(); // To prevent Spigot from initializing
        chunks = cache ? new Long2ObjectOpenHashMap<>() : null;
        biomes = cache ? new Long2ObjectOpenHashMap<>() : null;
        this.ambientEffects = new AmbientBlockEffects(session, this);
    }

    public void addToCache(int x, int z, DataPalette[] chunks) {
        addToCache(x, z, chunks, null);
    }

    /**
     * MINEZ：同时记下各区段的 Java 版生物群系（4×4×4 一格），血月期间模组画雨雪时按它判断各处的降水，见 {@link #javaBiomeAt}。
     */
    public void addToCache(int x, int z, DataPalette[] chunks, DataPalette[] biomes) {
        if (!cache) {
            return;
        }

        long chunkPosition = MathUtils.chunkPositionToLong(x, z);
        GeyserChunk geyserChunk = GeyserChunk.from(chunks);
        this.chunks.put(chunkPosition, geyserChunk);
        if (biomes != null) {
            this.biomes.put(chunkPosition, biomes);
        } else {
            this.biomes.remove(chunkPosition);
        }
        ambientEffects.onChunk(x, z, chunks, getChunkMinY());
    }

    /**
     * MINEZ：某一位置的 Java 版生物群系的网络编号（区块不在缓存中、或没有记下生物群系时为 -1）。只能在会话的事件循环中调用。
     */
    public int javaBiomeAt(int x, int y, int z) {
        if (!cache) {
            return -1;
        }
        DataPalette[] sections = biomes.get(MathUtils.chunkPositionToLong(x >> 4, z >> 4));
        if (sections == null || sections.length == 0) {
            return -1;
        }
        int sectionY = Math.max(0, Math.min(sections.length - 1, (y >> 4) - getChunkMinY()));
        DataPalette palette = sections[sectionY];
        if (palette == null) {
            return -1;
        }
        // 生物群系以 4×4×4 为一格，下标为 (y << 4) | (z << 2) | x（与 BiomeTranslator 相同）。
        int index = (((y >> 2) & 3) << 4) | (((z >> 2) & 3) << 2) | ((x >> 2) & 3);
        if (palette.getStorage() == null) {
            // 单值调色板（整个区段同一个生物群系）没有存储。
            return palette.getPalette().idToState(0);
        }
        return palette.getPalette().idToState(palette.getStorage().get(index));
    }

    /**
     * MINEZ：区块是否在缓存中。
     */
    public boolean hasChunk(int chunkX, int chunkZ) {
        return cache && chunks.containsKey(MathUtils.chunkPositionToLong(chunkX, chunkZ));
    }

    /**
     * Doesn't check for cache enabled, so don't use this without checking that first!
     */
    private GeyserChunk getChunk(int chunkX, int chunkZ) {
        long chunkPosition = MathUtils.chunkPositionToLong(chunkX, chunkZ);
        return chunks.getOrDefault(chunkPosition, null);
    }

    /**
     * Doesn't check for cache enabled, so don't use this without checking that first!
     */
    @Deprecated
    public DataPalette getChunkSection(int chunkX, int chunkY, int chunkZ, boolean createIfAbsent) {
        GeyserChunk chunk = this.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        if (chunkY < getChunkMinY() || chunkY - getChunkMinY() > chunk.sections().length - 1) {
            return null;
        }

        DataPalette palette = chunk.sections()[chunkY - getChunkMinY()];
        if (createIfAbsent && palette == null) {
            palette = DataPalette.createForBlockState(Block.JAVA_AIR_ID, BlockRegistries.BLOCK_STATES.get().size());
            chunk.sections()[chunkY - getChunkMinY()] = palette;
        }

        return palette;
    }

    public void updateBlock(int x, int y, int z, int block) {
        if (!cache) {
            return;
        }

        DataPalette palette = this.getChunkSection(x >> 4, y >> 4, z >> 4, true);
        if (palette == null) {
            return;
        }

        palette.set(x & 0xF, y & 0xF, z & 0xF, block);
        ambientEffects.onBlockUpdate(x, y, z, block);
    }

    public int getBlockAt(int x, int y, int z) {
        if (!cache) {
            return Block.JAVA_AIR_ID;
        }

        DataPalette palette = this.getChunkSection(x >> 4, y >> 4, z >> 4, false);
        if (palette == null) {
            return Block.JAVA_AIR_ID;
        }

        return palette.get(x & 0xF, y & 0xF, z & 0xF);
    }

    public void removeChunk(int chunkX, int chunkZ) {
        if (!cache) {
            return;
        }

        long chunkPosition = MathUtils.chunkPositionToLong(chunkX, chunkZ);
        chunks.remove(chunkPosition);
        biomes.remove(chunkPosition);
        ambientEffects.onChunkRemoved(chunkX, chunkZ);
    }

    /**
     * Manually clears all entries in the chunk cache.
     * The server is responsible for clearing chunk entries if out of render distance (for example) or switching dimensions,
     * but it is the client that must clear sections in the event of proxy switches.
     */
    public void clear() {
        if (!cache) {
            return;
        }

        chunks.clear();
        biomes.clear();
        ambientEffects.clear();
    }

    public int getChunkMinY() {
        return minY >> 4;
    }

    public int getChunkHeightY() {
        return heightY >> 4;
    }
}
