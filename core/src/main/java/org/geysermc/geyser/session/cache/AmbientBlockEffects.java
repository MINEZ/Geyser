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
 */

package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.particle.ParticleMapping;
import org.geysermc.geyser.level.particle.ParticleRandomization;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.tags.BlockTag;
import org.geysermc.geyser.translator.collision.BlockCollision;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.geyser.util.SoundUtils;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.GeyserParticleData;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ParticleData;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ParticleType;

import java.util.Random;
import java.util.function.Function;

/**
 * MINEZ：Java 版客户端自己产生、服务器不发的方块效果，在基岩版中由这里照 Java 26.3 模拟。基岩版的这些方块是 MINEZ 资源包的自定义方块，
 * 客户端不会自己产生这些效果。
 * <ul>
 *     <li>杨树树叶（红、橙、黄）：落叶粒子与环境声音（FallingParticlesLeavesBlock、AmbientLeavesBlockSoundPlayer）。</li>
 *     <li>烈性硫黄：上方为水源时冒出硫黄气泡、偶尔发出有毒气体的声音（PotentSulfurBlock.animateTick）；湿润与休眠时水面上飘有毒气体，
 *     喷发与持续喷发时每秒一次喷泉粒子、每两秒一次喷发的声音，喷柱中的本地玩家被往上推（PotentSulfurBlockEntity 的客户端 ticker）。</li>
 * </ul>
 * 区块缓存载入区块、更新方块时记下这些方块的位置（{@link #onChunk}、{@link #onBlockUpdate}），每刻只看这些位置。Java 版客户端每刻在玩家
 * 周围随机抽 667 × 2 个位置调用 animateTick（半径 16 与 32，各轴为两个均匀随机数之差），这里对每个记下的方块按同样的分布算出它被抽中的
 * 概率；方块实体的 ticker 则每刻都执行（只看 64 格以内）。
 */
public final class AmbientBlockEffects {
    private static final int ANIMATE_TICK_SAMPLES = 667;
    private static final int BLOCK_ENTITY_RANGE = 64;
    private static final float POPLAR_LEAF_PARTICLE_CHANCE = 0.01F;
    private static final int POPLAR_LEAF_AMBIENT_CHANCE = 300;

    private final GeyserSession session;
    private final ChunkCache chunkCache;
    /** 区块 → 区块中记下的方块位置（{@link #pack}） */
    private final Long2ObjectMap<LongSet> tracked = new Long2ObjectOpenHashMap<>();
    /** 烈性硫黄的位置 → 喷发开始的刻（PotentSulfurBlockEntity.eruptionTick） */
    private final Long2ObjectMap<Long> eruptionTicks = new Long2ObjectOpenHashMap<>();
    private final Random random = new Random();
    private long ticks;

    AmbientBlockEffects(GeyserSession session, ChunkCache chunkCache) {
        this.session = session;
        this.chunkCache = chunkCache;
    }

    private static boolean isTracked(int javaId) {
        Block block = BlockState.of(javaId).block();
        return isPoplarLeaves(block) || block == Blocks.POTENT_SULFUR;
    }

    private static boolean isPoplarLeaves(Block block) {
        return block == Blocks.RED_POPLAR_LEAVES || block == Blocks.ORANGE_POPLAR_LEAVES || block == Blocks.YELLOW_POPLAR_LEAVES;
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }

    static int unpackX(long pos) {
        return (int) (pos >> 38);
    }

    static int unpackY(long pos) {
        return (int) (pos << 52 >> 52);
    }

    static int unpackZ(long pos) {
        return (int) (pos << 26 >> 38);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) << 32 | ((long) chunkZ & 0xFFFFFFFFL);
    }

    /**
     * 区块载入：找出其中的杨树树叶与烈性硫黄。大部分分区的调色板中没有它们，不必逐格看。
     */
    void onChunk(int chunkX, int chunkZ, DataPalette[] sections, int minSectionY) {
        long key = chunkKey(chunkX, chunkZ);
        tracked.remove(key);
        LongSet positions = null;
        for (int i = 0; i < sections.length; i++) {
            DataPalette section = sections[i];
            if (section == null || !mayContainTracked(section.getPalette())) {
                continue;
            }
            int baseY = (minSectionY + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isTracked(section.get(x, y, z))) {
                            if (positions == null) {
                                positions = new LongOpenHashSet();
                            }
                            positions.add(pack((chunkX << 4) + x, baseY + y, (chunkZ << 4) + z));
                        }
                    }
                }
            }
        }
        if (positions != null) {
            tracked.put(key, positions);
        }
    }

    private static boolean mayContainTracked(Palette palette) {
        int size = palette.size();
        if (size > 256) {
            return true; // 全局调色板：查不了，逐格看
        }
        for (int i = 0; i < size; i++) {
            if (isTracked(palette.idToState(i))) {
                return true;
            }
        }
        return false;
    }

    void onBlockUpdate(int x, int y, int z, int javaId) {
        long key = chunkKey(x >> 4, z >> 4);
        long pos = pack(x, y, z);
        if (isTracked(javaId)) {
            tracked.computeIfAbsent(key, k -> new LongOpenHashSet()).add(pos);
        } else {
            LongSet positions = tracked.get(key);
            if (positions != null && positions.remove(pos)) {
                eruptionTicks.remove(pos);
                if (positions.isEmpty()) {
                    tracked.remove(key);
                }
            }
        }
    }

    void onChunkRemoved(int chunkX, int chunkZ) {
        LongSet positions = tracked.remove(chunkKey(chunkX, chunkZ));
        if (positions != null) {
            for (LongIterator it = positions.iterator(); it.hasNext(); ) {
                eruptionTicks.remove(it.nextLong());
            }
        }
    }

    void clear() {
        tracked.clear();
        eruptionTicks.clear();
    }

    /**
     * 方块事件（ClientboundBlockEvent）：烈性硫黄开始喷发时服务器发出，Java 版据此重设 eruptionTick。
     */
    public void onBlockEvent(int x, int y, int z) {
        if (chunkCache.getBlockAt(x, y, z) != Block.JAVA_AIR_ID && BlockState.of(chunkCache.getBlockAt(x, y, z)).is(Blocks.POTENT_SULFUR)) {
            eruptionTicks.put(pack(x, y, z), Long.valueOf(ticks));
        }
    }

    public void tick() {
        ticks++;
        if (tracked.isEmpty()) {
            return;
        }
        SessionPlayerEntity player = session.getPlayerEntity();
        Vector3f feet = player.getPosition();
        int px = (int) Math.floor(feet.getX());
        int py = (int) Math.floor(feet.getY());
        int pz = (int) Math.floor(feet.getZ());
        int minChunkX = (px - BLOCK_ENTITY_RANGE) >> 4;
        int maxChunkX = (px + BLOCK_ENTITY_RANGE) >> 4;
        int minChunkZ = (pz - BLOCK_ENTITY_RANGE) >> 4;
        int maxChunkZ = (pz + BLOCK_ENTITY_RANGE) >> 4;
        for (Long2ObjectMap.Entry<LongSet> entry : tracked.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                continue;
            }
            for (LongIterator it = entry.getValue().iterator(); it.hasNext(); ) {
                long pos = it.nextLong();
                int x = unpackX(pos);
                int y = unpackY(pos);
                int z = unpackZ(pos);
                BlockState state = BlockState.of(chunkCache.getBlockAt(x, y, z));
                Block block = state.block();
                boolean animate = random.nextDouble() < animateTickChance(x - px, y - py, z - pz);
                if (isPoplarLeaves(block)) {
                    if (animate) {
                        poplarLeavesAnimateTick(block, x, y, z);
                    }
                } else if (block == Blocks.POTENT_SULFUR) {
                    String sulfurState = state.getValue(Properties.POTENT_SULFUR_STATE);
                    if (animate) {
                        potentSulfurAnimateTick(sulfurState, x, y, z);
                    }
                    if (Math.abs(x - px) <= BLOCK_ENTITY_RANGE && Math.abs(y - py) <= BLOCK_ENTITY_RANGE && Math.abs(z - pz) <= BLOCK_ENTITY_RANGE) {
                        potentSulfurBlockEntityTick(pos, sulfurState, x, y, z);
                    }
                }
            }
        }
    }

    /**
     * Java 版 ClientLevel.animateTick 在一刻中抽中某个相对玩家偏移 (dx, dy, dz) 的方块的期望次数：667 次半径 16、667 次半径 32，
     * 每轴的偏移为 nextInt(r) - nextInt(r)，取到 d 的概率为 (r - |d|) / r²。
     */
    static double animateTickChance(int dx, int dy, int dz) {
        return ANIMATE_TICK_SAMPLES * (axisChance(dx, 16) * axisChance(dy, 16) * axisChance(dz, 16)
            + axisChance(dx, 32) * axisChance(dy, 32) * axisChance(dz, 32));
    }

    private static double axisChance(int d, int range) {
        int distance = Math.abs(d);
        return distance >= range ? 0 : (double) (range - distance) / (range * range);
    }

    // ---- 杨树树叶 ----

    private void poplarLeavesAnimateTick(Block block, int x, int y, int z) {
        // FallingParticlesLeavesBlock.makeFallingLeavesParticles：下方方块的碰撞箱顶面不满时，在方块底面随机一点放出落叶
        if (random.nextFloat() < POPLAR_LEAF_PARTICLE_CHANCE && !isTopFaceFull(chunkCache.getBlockAt(x, y - 1, z))) {
            ParticleType type = block == Blocks.RED_POPLAR_LEAVES ? ParticleType.RED_POPLAR_LEAVES
                : block == Blocks.ORANGE_POPLAR_LEAVES ? ParticleType.ORANGE_POPLAR_LEAVES : ParticleType.YELLOW_POPLAR_LEAVES;
            particle(type, null, x + random.nextDouble(), y - 0.05, z + random.nextDouble());
        }
        // AmbientLeavesBlockSoundPlayer：1/300，四周水平方向的方块中依次数到 1 个 required_for_poplar_leaf_ambience 与 3 个同种树叶时
        if (random.nextInt(POPLAR_LEAF_AMBIENT_CHANCE) == 0) {
            int logs = 0;
            int leaves = 0;
            int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // 北、东、南、西
            for (int[] d : directions) {
                Block neighbor = BlockState.of(chunkCache.getBlockAt(x + d[0], y, z + d[1])).block();
                if (neighbor.is(session, BlockTag.REQUIRED_FOR_POPLAR_LEAF_AMBIENCE)) {
                    logs++;
                }
                if (neighbor == block) {
                    leaves++;
                }
                if (logs == 1 && leaves == 3) {
                    sound("minecraft:block.poplar_leaves.ambient", x, y, z);
                    return;
                }
            }
        }
    }

    private static boolean isTopFaceFull(int javaId) {
        BlockCollision collision = BlockUtils.getCollision(javaId);
        if (collision == null || collision.getBoundingBoxes() == null) {
            return false;
        }
        for (BoundingBox box : collision.getBoundingBoxes()) {
            double halfX = box.getSizeX() / 2;
            double halfY = box.getSizeY() / 2;
            double halfZ = box.getSizeZ() / 2;
            if (box.getMiddleX() - halfX <= 1.0E-4 && box.getMiddleX() + halfX >= 1 - 1.0E-4
                && box.getMiddleZ() - halfZ <= 1.0E-4 && box.getMiddleZ() + halfZ >= 1 - 1.0E-4
                && box.getMiddleY() + halfY >= 1 - 1.0E-4) {
                return true;
            }
        }
        return false;
    }

    // ---- 烈性硫黄 ----

    private void potentSulfurAnimateTick(String sulfurState, int x, int y, int z) {
        if ("dry".equals(sulfurState) || !isWaterSource(chunkCache.getBlockAt(x, y + 1, z))) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            particle(ParticleType.SULFUR_BUBBLES, null, x + random.nextFloat(), y + 1 + random.nextFloat(), z + random.nextFloat());
        }
        if (random.nextInt(10) == 0) {
            sound("minecraft:block.potent_sulfur.noxious_gas", x, y, z);
        }
    }

    private void potentSulfurBlockEntityTick(long pos, String sulfurState, int x, int y, int z) {
        switch (sulfurState) {
            case "wet", "dormant" -> {
                // CLIENT_NOXIOUS_GAS_TICKER 每 20 刻放出一个持续 20 刻、每 2 刻冒一次有毒气体的云（NoxiousGasCloudParticle），
                // 即连续地每 2 刻一次
                if (ticks % 2 == 0) {
                    long source = noxiousGasSource(x, y, z);
                    if (source != Long.MIN_VALUE) {
                        noxiousGas(unpackX(source), unpackY(source), unpackZ(source));
                    }
                }
            }
            case "erupting", "continuous" -> {
                long source = noxiousGasSource(x, y, z);
                if (source == Long.MIN_VALUE) {
                    return;
                }
                int sx = unpackX(source);
                int sy = unpackY(source);
                int sz = unpackZ(source);
                int waterBlocks = sy - y - 1;
                long eruptionTime = ticks - eruptionTicks.computeIfAbsent(pos, p -> Long.valueOf(ticks));
                if (eruptionTime % 20 == 0) {
                    particle(ParticleType.GEYSER, new GeyserParticleData(waterBlocks), sx + 0.5, sy, sz + 0.5);
                }
                if (eruptionTime % 40 == 0) {
                    sound("continuous".equals(sulfurState) ? "minecraft:block.potent_sulfur.geyser_continuous_eruption_active"
                        : "minecraft:block.potent_sulfur.geyser_eruption_active", sx + 0.5, sy + 0.5, sz + 0.5);
                }
                launchPlayer(x, y, z, waterBlocks);
            }
            default -> {
            }
        }
    }

    /**
     * PotentSulfurBlockEntity.LAUNCH_ENTITY_TICKER 对本地玩家的部分：玩家的移动由客户端计算，Java 版服务器不推玩家。
     */
    private void launchPlayer(int x, int y, int z, int waterBlocks) {
        SessionPlayerEntity player = session.getPlayerEntity();
        if (session.isFlying() || player.getVehicle() != null) {
            return;
        }
        int height = unobstructedBlockCount(x, y + 1, z, waterBlocks);
        if (height <= 0) {
            return;
        }
        Vector3f feet = player.getPosition();
        double halfWidth = player.getBoundingBoxWidth() / 2;
        if (feet.getX() + halfWidth <= x || feet.getX() - halfWidth >= x + 1 || feet.getZ() + halfWidth <= z || feet.getZ() - halfWidth >= z + 1
            || feet.getY() + player.getBoundingBoxHeight() <= y + 1 || feet.getY() >= y + 1 + height) {
            return;
        }
        Vector3f motion = player.getLastTickEndVelocity();
        if (motion.getY() < 0.3F + waterBlocks * 0.1) {
            SetEntityMotionPacket packet = new SetEntityMotionPacket();
            packet.setRuntimeEntityId(player.geyserId());
            packet.setMotion(Vector3f.from(motion.getX(), motion.getY() + 0.2F, motion.getZ()));
            session.sendUpstreamPacket(packet);
        }
    }

    private int unobstructedBlockCount(int x, int y, int z, int waterBlocks) {
        int height = 6 * waterBlocks;
        for (int i = 0; i < height; i++) {
            if (!isGeyserPassable(chunkCache.getBlockAt(x, y + i, z))) {
                return i;
            }
        }
        return height;
    }

    /**
     * PotentSulfurBlockEntity.findNoxiousGasSourceBlock：往上 1~5 格中，第一个不是水源（或是含水却不可穿过的方块）的位置；
     * 它是空气或可以穿过时为气体的源头，否则没有（{@link Long#MIN_VALUE}）。
     */
    private long noxiousGasSource(int x, int y, int z) {
        for (int cy = y + 1; cy <= y + 5; cy++) {
            int javaId = chunkCache.getBlockAt(x, cy, z);
            BlockState state = BlockState.of(javaId);
            boolean waterSource = isWaterSource(javaId);
            if (!waterSource || !state.is(Blocks.WATER) && !isGeyserPassable(javaId)) {
                if (javaId == Block.JAVA_AIR_ID || isGeyserPassable(javaId)) {
                    return pack(x, cy, z);
                }
                return Long.MIN_VALUE;
            }
        }
        return Long.MIN_VALUE;
    }

    private void noxiousGas(int sx, int sy, int sz) {
        // NoxiousGasCloudParticle：以气体源头中心为圆心、水平 3 格内随机一点，低 0.25 格；那一格可以穿过、其下为水源时放出
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = random.nextFloat() * 3.0F;
        double px = sx + 0.5 + Math.cos(angle) * distance;
        double py = sy + 0.25;
        double pz = sz + 0.5 + Math.sin(angle) * distance;
        int bx = (int) Math.floor(px);
        int by = (int) Math.floor(py);
        int bz = (int) Math.floor(pz);
        if (isGeyserPassable(chunkCache.getBlockAt(bx, by, bz)) && isWaterSource(chunkCache.getBlockAt(bx, (int) Math.floor(py - 1), bz))) {
            particle(ParticleType.NOXIOUS_GAS, null, px, py, pz);
        }
    }

    private static boolean isGeyserPassable(int javaId) {
        BlockState state = BlockState.of(javaId);
        if (javaId == Block.JAVA_AIR_ID || state.is(Blocks.WATER)) {
            return true;
        }
        BlockCollision collision = BlockUtils.getCollision(javaId);
        return collision == null || collision.getBoundingBoxes() == null || collision.getBoundingBoxes().length == 0;
    }

    private static boolean isWaterSource(int javaId) {
        BlockState state = BlockState.of(javaId);
        if (state.is(Blocks.WATER)) {
            return state.getValue(Properties.LEVEL) == 0;
        }
        return BlockRegistries.WATERLOGGED.get().get(javaId);
    }

    // ---- 发送 ----

    private void particle(ParticleType type, @Nullable ParticleData data, double x, double y, double z) {
        ParticleMapping mapping = Registries.PARTICLES.get(type);
        if (mapping == null) {
            return;
        }
        Function<ParticleRandomization, BedrockPacket> constructor = mapping.mapParticle(session, data);
        if (constructor == null) {
            return;
        }
        BedrockPacket packet = constructor.apply(ParticleRandomization.ofStatic(Vector3f.from(x, y, z)));
        if (packet != null) {
            session.sendUpstreamPacket(packet);
        }
    }

    private void sound(String javaSound, double x, double y, double z) {
        PlaySoundPacket packet = new PlaySoundPacket();
        packet.setSound(SoundUtils.translatePlaySound(javaSound));
        packet.setPosition(Vector3f.from(x, y, z));
        packet.setVolume(1.0F);
        packet.setPitch(1.0F);
        session.sendUpstreamPacket(packet);
    }
}
