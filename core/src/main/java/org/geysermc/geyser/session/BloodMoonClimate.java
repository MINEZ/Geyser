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
package org.geysermc.geyser.session;

import net.kyori.adventure.key.Key;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.level.block.Fluid;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.registry.java.JavaRegistries;
import org.geysermc.geyser.registry.java.JavaRegistryLookup;
import org.geysermc.geyser.session.cache.ChunkCache;
import org.geysermc.geyser.util.BlockUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 血月期间基岩版的雨雪由模组以粒子画出（Geyser 不再把下雨发给基岩版），模组须知道附近每一列是下雨、下雪还是不下，以及降水落到的高度。
 * 基岩版客户端取不到生物群系（网易的 GetBiomeName 经 Geyser 连接时一直返回 None），由这里按 Java 版的算法从区块缓存算出：
 * <ul>
 *   <li>高度：Java 版 MOTION_BLOCKING 高度图，即最高的有碰撞箱或含液体的方块之上一格（雨雪落到这里）。</li>
 *   <li>降水：取该列在 max(高度, 摄像机高度) 处的生物群系；没有降水的生物群系（沙漠、热带草原、恶地等）不下；
 *   否则按温度（海平面 + 17 格以上每升高 40 格降 0.05，与 Java 版 Biome#getHeightAdjustedTemperature 相同，不含噪声）低于 0.15 下雪，
 *   否则下雨。与 Java 版 WeatherEffectRenderer 逐列判断的做法相同，因而走进不下雨的生物群系时雨在边界上逐列消失，而不是在玩家周围整片消失。</li>
 * </ul>
 */
public final class BloodMoonClimate {
    /**
     * 主世界的海平面（Java 版 noise_settings 的 sea_level）。
     */
    private static final int SEA_LEVEL = 63;
    private static final float SNOW_TEMPERATURE = 0.15F;
    /**
     * 未知（自定义）生物群系的温度，按会下雨处理。
     */
    private static final float DEFAULT_TEMPERATURE = 0.8F;
    /**
     * Java 版原版生物群系的温度（data/minecraft/worldgen/biome/*.json 的 temperature）。冰冻海洋（frozen 温度修饰）取基础温度。
     */
    private static final Map<String, Float> TEMPERATURES = new HashMap<>();
    /**
     * 没有降水（has_precipitation 为 false）的原版生物群系。
     */
    private static final List<String> DRY = List.of("desert", "savanna", "savanna_plateau", "windswept_savanna", "badlands",
        "eroded_badlands", "wooded_badlands", "the_void");

    static {
        String[][] table = {
            {"snowy_plains", "0"}, {"ice_spikes", "0"}, {"frozen_river", "0"}, {"frozen_ocean", "0"}, {"snowy_beach", "0.05"},
            {"snowy_taiga", "-0.5"}, {"grove", "-0.2"}, {"snowy_slopes", "-0.3"}, {"frozen_peaks", "-0.7"}, {"jagged_peaks", "-0.7"},
            {"stony_peaks", "1"}, {"windswept_hills", "0.2"}, {"windswept_gravelly_hills", "0.2"}, {"windswept_forest", "0.2"},
            {"stony_shore", "0.2"}, {"taiga", "0.25"}, {"old_growth_spruce_taiga", "0.25"}, {"old_growth_pine_taiga", "0.3"},
            {"deep_frozen_ocean", "0.5"}, {"cold_ocean", "0.5"}, {"deep_cold_ocean", "0.5"}, {"ocean", "0.5"}, {"deep_ocean", "0.5"},
            {"lukewarm_ocean", "0.5"}, {"deep_lukewarm_ocean", "0.5"}, {"warm_ocean", "0.5"}, {"river", "0.5"}, {"meadow", "0.5"},
            {"cherry_grove", "0.5"}, {"lush_caves", "0.5"}, {"birch_forest", "0.6"}, {"old_growth_birch_forest", "0.6"},
            {"forest", "0.7"}, {"flower_forest", "0.7"}, {"dark_forest", "0.7"}, {"pale_garden", "0.7"}, {"plains", "0.8"},
            {"sunflower_plains", "0.8"}, {"swamp", "0.8"}, {"mangrove_swamp", "0.8"}, {"beach", "0.8"}, {"dripstone_caves", "0.8"},
            {"deep_dark", "0.8"}, {"mushroom_fields", "0.9"}, {"jungle", "0.95"}, {"sparse_jungle", "0.95"}, {"bamboo_jungle", "0.95"},
        };
        for (String[] entry : table) {
            TEMPERATURES.put(entry[0], Float.parseFloat(entry[1]));
        }
    }

    private BloodMoonClimate() {
    }

    /**
     * 算出一个区块各列的高度与降水。只能在会话的事件循环中调用（区块缓存不是线程安全的）。
     *
     * @param cameraY 摄像机所在的方块高度。
     * @return {"x", "z", "h": 256 个高度（下标为 z * 16 + x）, "c": 256 个字符（r 下雨、s 下雪、n 不下）}；区块不在缓存中时为空。
     */
    public static Optional<Map<String, Object>> chunk(GeyserSession session, int chunkX, int chunkZ, int cameraY) {
        ChunkCache cache = session.getChunkCache();
        if (!cache.hasChunk(chunkX, chunkZ)) {
            return Optional.empty();
        }
        JavaRegistryLookup<?> biomes = session.javaRegistries().registry(JavaRegistries.BIOME);
        int minY = cache.getChunkMinY() << 4;
        int maxY = minY + (cache.getChunkHeightY() << 4) - 1;
        List<Integer> heights = new ArrayList<>(256);
        StringBuilder climate = new StringBuilder(256);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int blockX = (chunkX << 4) + x;
                int blockZ = (chunkZ << 4) + z;
                int height = height(cache, blockX, blockZ, minY, maxY);
                heights.add(height);
                int y = Math.min(maxY, Math.max(height, cameraY));
                climate.append(precipitation(biomes, cache.javaBiomeAt(blockX, y, blockZ), y));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("x", chunkX);
        result.put("z", chunkZ);
        result.put("h", heights);
        result.put("c", climate.toString());
        return Optional.of(result);
    }

    /**
     * Java 版 MOTION_BLOCKING 高度图：最高的有碰撞箱或含液体的方块之上一格；整列都是空气时为最低高度。
     */
    private static int height(ChunkCache cache, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            int state = cache.getBlockAt(x, y, z);
            if (state == Block.JAVA_AIR_ID) {
                continue;
            }
            if (BlockUtils.getCollision(state) != null || BlockStateValues.getFluid(state) != Fluid.EMPTY) {
                return y + 1;
            }
        }
        return minY;
    }

    private static char precipitation(JavaRegistryLookup<?> biomes, int biomeId, int y) {
        String name = biomeId < 0 ? null : biomes.getKey(biomeId).map(Key::value).orElse(null);
        if (name != null && DRY.contains(name)) {
            return 'n';
        }
        float temperature = name == null ? DEFAULT_TEMPERATURE : TEMPERATURES.getOrDefault(name, DEFAULT_TEMPERATURE);
        if (y > SEA_LEVEL + 17) {
            temperature -= (y - SEA_LEVEL - 17) * 0.05F / 40.0F;
        }
        return temperature < SNOW_TEMPERATURE ? 's' : 'r';
    }
}
