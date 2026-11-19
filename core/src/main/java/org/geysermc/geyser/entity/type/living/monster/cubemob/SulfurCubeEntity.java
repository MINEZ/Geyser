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

package org.geysermc.geyser.entity.type.living.monster.cubemob;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.properties.type.StringEnumProperty;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.MinecartEntity;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.level.block.property.Property;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.block.type.SkullBlock;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.session.cache.tags.ItemTag;
import org.geysermc.geyser.session.cache.tags.Tag;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.BlockStateProperties;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SulfurCubeEntity extends AbstractCubeEntity {

    private static final Map<Tag<Item>, String> ARCHETYPE_MAPPING = new HashMap<>();
    private static final List<String> BEDROCK_ARCHETYPES = new ArrayList<>();

    public static StringEnumProperty SULFUR_CUBE_ARCHETYPE_PROPERTY = new StringEnumProperty(
        Identifier.of("minecraft", "sulfur_cube_archetype"), BEDROCK_ARCHETYPES, 0
    );

    /**
     * MINEZ: Java 版 SulfurCubeRenderer 把体内的方块画成 1×1×1 的方块，随硫方怪转动；方块底面比脚底高这么多
     * （看得见时；隐身时低 1/16）。装着方块时硫方怪不压扁，方块不用跟着变形。
     */
    private static final float CONTAINED_BLOCK_Y = 0.064f;
    private static final float CONTAINED_BLOCK_Y_INVISIBLE = 0.002f;
    /**
     * 显示体内方块的矿车：标记变种为这个值时，MINEZ 资源包中矿车的渲染控制器不画车身（见 MINEZ/CraftengineResources 的
     * vanilla/overrides/minecart）。矿车把显示的方块缩到 0.75 倍，矿车放大 4/3 倍后方块为 1×1×1。
     */
    private static final int HIDDEN_MINECART_MARK_VARIANT = 7351;
    private static final float CONTAINED_BLOCK_MINECART_SCALE = 4f / 3f;
    private static final int CONTAINED_BLOCK_DISPLAY_OFFSET = 8;
    /**
     * 按上面的放大与显示偏移，基岩版矿车显示的方块的底面比矿车（骑乘时即座位）高这么多；矿车按硫方怪身体的朝向转动时，
     * 方块的朝向与 Java 版硫方怪体内的方块相差的角度（照 Java 版 AbstractMinecartRenderer 推算，以实测校正）。
     */
    private static final float MINECART_BLOCK_BOTTOM = 0.033f;
    private static final float MINECART_ROTATION_OFFSET = -90f;

    private int previousFuseTickTime = -1;
    private int fuseTickTime = -1;
    /**
     * MINEZ: 网易 1.21.130 没有硫方怪的实体类，客户端中它只是个不画装备的通用实体，主手物品（吸收的方块）不显示。
     * 26.40 之前的客户端改由这个矿车显示体内的方块：车身不画，只画矿车显示的方块；不受重力、不碰撞、没有碰撞箱，
     * 作为乘客骑在硫方怪上，由客户端按座位放在硫方怪体内并随它移动，朝向锁定为硫方怪身体的朝向。
     * （b1305.10 用不受重力的下落方块并以移动包跟随，下落方块不理会移动包；b1305.11 让下落方块骑上硫方怪，跟随了，
     * 但基岩版的下落方块不按朝向绘制，硫方怪转身时方块不转。）
     */
    private @Nullable MinecartEntity containedBlock;

    public SulfurCubeEntity(EntitySpawnContext context) {
        super(context);
    }

    @Override
    public void setBody(GeyserItemStack stack) {
        MobEquipmentPacket handPacket = new MobEquipmentPacket();
        handPacket.setRuntimeEntityId(geyserId);
        handPacket.setItem(ItemTranslator.translateToBedrock(session, stack));
        handPacket.setHotbarSlot(0);
        handPacket.setInventorySlot(0);
        handPacket.setContainerId(ContainerId.INVENTORY);

        session.sendUpstreamPacket(handPacket);

        LevelSoundEventPacket levelSoundEventPacket = new LevelSoundEventPacket();

        if (stack.isEmpty()) {
            levelSoundEventPacket.setSound(SoundEvent.EJECT_BLOCK);
        } else {
            levelSoundEventPacket.setSound(SoundEvent.ABSORB_BLOCK);
        }

        levelSoundEventPacket.setPosition(bedrockPosition());
        levelSoundEventPacket.setExtraData(-1);
        levelSoundEventPacket.setIdentifier("minecraft:sulfur_cube");
        levelSoundEventPacket.setEntityUniqueId(this.geyserId());

        session.sendUpstreamPacket(levelSoundEventPacket);

        if (stack.isEmpty()) {
            this.updateProperty(SULFUR_CUBE_ARCHETYPE_PROPERTY, "none");
        } else {
            String archetype = null;
            for (Map.Entry<Tag<Item>, String> entry : ARCHETYPE_MAPPING.entrySet()) {
                if (stack.is(session, entry.getKey())) {
                    archetype = entry.getValue();
                    break;
                }
            }

            if (archetype == null) archetype = "regular"; // Needs a value, this is the best default

            this.updateProperty(SULFUR_CUBE_ARCHETYPE_PROPERTY, archetype);
        }

        updateContainedBlock(stack);
    }

    private void updateContainedBlock(GeyserItemStack stack) {
        if (containedBlock != null) {
            containedBlock.despawnEntity();
            containedBlock = null;
        }
        if (GameProtocol.is26_40orHigher(session.protocolVersion())) {
            return; // 客户端自己画
        }
        BlockState state = containedBlockState(stack);
        if (state == null) {
            return;
        }
        containedBlock = new MinecartEntity(EntitySpawnContext.inherited(session, VanillaEntities.MINECART, this, position));
        containedBlock.getMetadata().put(EntityDataTypes.CUSTOM_DISPLAY, (byte) 1);
        containedBlock.getMetadata().put(EntityDataTypes.DISPLAY_BLOCK_STATE, session.getBlockMappings().getBedrockBlock(state.javaId()));
        containedBlock.getMetadata().put(EntityDataTypes.DISPLAY_OFFSET, CONTAINED_BLOCK_DISPLAY_OFFSET);
        containedBlock.getMetadata().put(EntityDataTypes.MARK_VARIANT, HIDDEN_MINECART_MARK_VARIANT);
        containedBlock.setFlag(EntityFlag.HAS_GRAVITY, false);
        containedBlock.setFlag(EntityFlag.NO_AI, true);
        containedBlock.setFlag(EntityFlag.HAS_COLLISION, false);
        containedBlock.getMetadata().put(EntityDataTypes.WIDTH, 0.0f);
        containedBlock.getMetadata().put(EntityDataTypes.HEIGHT, 0.0f);
        containedBlock.getMetadata().put(EntityDataTypes.HITBOX, NbtMap.EMPTY);
        putContainedBlockSeat();
        if (isValid()) {
            spawnContainedBlock();
        }
    }

    /**
     * 骑乘、座位与缩放的实体数据。实体数据发出后即清空，改动时须整体再写一次。
     */
    private void putContainedBlockSeat() {
        if (containedBlock == null) {
            return;
        }
        float entityScale = scale * attributeScale;
        float y = getFlag(EntityFlag.INVISIBLE) ? CONTAINED_BLOCK_Y_INVISIBLE : CONTAINED_BLOCK_Y;
        containedBlock.setScale(CONTAINED_BLOCK_MINECART_SCALE * entityScale);
        containedBlock.setFlag(EntityFlag.RIDING, true);
        containedBlock.setRiderSeatPosition(Vector3f.from(0, (y - MINECART_BLOCK_BOTTOM) * entityScale, 0));
        // 朝向锁定为硫方怪身体的朝向（与 ItemDisplayEntity 骑在载具上时相同），另加矿车画方块时的转角差
        containedBlock.getMetadata().put(EntityDataTypes.SEAT_LOCK_RIDER_ROTATION, true);
        containedBlock.getMetadata().put(EntityDataTypes.SEAT_LOCK_RIDER_ROTATION_DEGREES, 0f);
        containedBlock.getMetadata().put(EntityDataTypes.SEAT_HAS_ROTATION, true);
        containedBlock.getMetadata().put(EntityDataTypes.SEAT_ROTATION_OFFSET_DEGREES, MINECART_ROTATION_OFFSET);
    }

    private void spawnContainedBlock() {
        if (containedBlock == null || containedBlock.isValid()) {
            return;
        }
        containedBlock.setPosition(position);
        containedBlock.spawnEntity();
        SetEntityLinkPacket linkPacket = new SetEntityLinkPacket();
        linkPacket.setEntityLink(new EntityLinkData(geyserId, containedBlock.geyserId(), EntityLinkData.Type.PASSENGER, false, false, 0f));
        session.sendUpstreamPacket(linkPacket);
    }

    /**
     * 照 Java 版 SulfurCubeRenderer#extractRenderState：物品对应方块（Block.byItem）的默认状态，套用物品的 block_state 组件。
     * 头颅由 Java 版画成戴着的头颅，这里不画；不是方块物品时不画。
     */
    private static @Nullable BlockState containedBlockState(GeyserItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return containedBlockState(stack.asItem(), stack.getComponent(DataComponentTypes.BLOCK_STATE));
    }

    static @Nullable BlockState containedBlockState(Item item, @Nullable BlockStateProperties properties) {
        if (!(item instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        if (block instanceof SkullBlock) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        Property<?>[] keys = block.propertyKeys();
        if (properties != null && keys != null) {
            for (Map.Entry<String, String> entry : properties.getProperties().entrySet()) {
                for (Property<?> key : keys) {
                    if (key.name().equals(entry.getKey())) {
                        state = withValue(state, key, entry.getValue());
                    }
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
        return property.valueOf(value).map(v -> state.withValue(property, v)).orElse(state);
    }

    private void updateContainedBlockSeat() {
        if (containedBlock != null && containedBlock.isValid()) {
            putContainedBlockSeat();
            containedBlock.updateBedrockMetadata();
        }
    }

    @Override
    public void spawnEntity() {
        super.spawnEntity();
        spawnContainedBlock();
    }

    @Override
    public void despawnEntity() {
        if (containedBlock != null) {
            containedBlock.despawnEntity();
        }
        super.despawnEntity();
    }

    @Override
    protected void setInvisible(boolean value) {
        super.setInvisible(value);
        updateContainedBlockSeat();
    }

    @Override
    protected void applyScale() {
        super.applyScale();
        updateContainedBlockSeat();
    }

    @Override
    public void setCubeScale(IntEntityMetadata entityMetadata) {
        // Ignore, sulfur cubes use age and baby tags instead
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuseTickTime > 0) {
            this.fuseTickTime--;
        }

        if (this.previousFuseTickTime != this.fuseTickTime) {
            this.metadata.put(EntityDataTypes.FUSE_TIME, fuseTickTime);
            this.updateBedrockMetadata();
            this.previousFuseTickTime = this.fuseTickTime;
        }
    }

    @Override
    protected float getBabySize() {
        return 1f;
    }

    public void setMaxFuse(IntEntityMetadata entityMetadata) {
        this.fuseTickTime = entityMetadata.getPrimitiveValue();
        this.metadata.put(EntityDataTypes.FUSE_TIME, fuseTickTime);
    }

    static {
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_BOUNCY, "bouncy");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_REGULAR, "regular");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_SLOW_BOUNCY, "slow_bouncy");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_SLOW_FLAT, "slow_flat");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_FAST_FLAT, "fast_flat");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_LIGHT, "light");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_FAST_SLIDING, "fast_sliding");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_SLOW_SLIDING, "slow_sliding");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_STICKY, "sticky");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_HIGH_RESISTANCE, "high_resistance");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_EXPLOSIVE, "explosive");
        ARCHETYPE_MAPPING.put(ItemTag.SULFUR_CUBE_ARCHETYPE_HOT, "hot");

        BEDROCK_ARCHETYPES.add("none");
        BEDROCK_ARCHETYPES.addAll(ARCHETYPE_MAPPING.values());
    }
}
