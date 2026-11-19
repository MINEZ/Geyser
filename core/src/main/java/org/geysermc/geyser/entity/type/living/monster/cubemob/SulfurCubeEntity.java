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
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.properties.type.StringEnumProperty;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.FallingBlockEntity;
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

    private int previousFuseTickTime = -1;
    private int fuseTickTime = -1;
    /**
     * MINEZ: 网易 1.21.130 没有硫方怪的实体类，客户端中它只是个不画装备的通用实体，主手物品（吸收的方块）不显示。
     * 26.40 之前的客户端改由这个跟随硫方怪的下落方块实体显示体内的方块：不受重力、不碰撞、没有碰撞箱。
     */
    private @Nullable FallingBlockEntity containedBlock;

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
        containedBlock = new FallingBlockEntity(EntitySpawnContext.inherited(session, VanillaEntities.FALLING_BLOCK, this, containedBlockPosition()), state.javaId());
        containedBlock.setFlag(EntityFlag.HAS_GRAVITY, false);
        containedBlock.setFlag(EntityFlag.NO_AI, true);
        containedBlock.setFlag(EntityFlag.HAS_COLLISION, false);
        containedBlock.getMetadata().put(EntityDataTypes.WIDTH, 0.0f);
        containedBlock.getMetadata().put(EntityDataTypes.HEIGHT, 0.0f);
        containedBlock.getMetadata().put(EntityDataTypes.HITBOX, NbtMap.EMPTY);
        containedBlock.setScale(scale * attributeScale);
        containedBlock.setYaw(yaw);
        containedBlock.setHeadYaw(yaw);
        if (isValid()) {
            containedBlock.spawnEntity();
        }
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

    private Vector3f containedBlockPosition() {
        float y = getFlag(EntityFlag.INVISIBLE) ? CONTAINED_BLOCK_Y_INVISIBLE : CONTAINED_BLOCK_Y;
        return position.up(y * scale * attributeScale);
    }

    private void moveContainedBlock(boolean teleported) {
        if (containedBlock != null && containedBlock.isValid()) {
            containedBlock.moveAbsoluteRaw(containedBlockPosition(), yaw, 0, yaw, onGround, teleported);
        }
    }

    @Override
    public void spawnEntity() {
        super.spawnEntity();
        if (containedBlock != null && !containedBlock.isValid()) {
            containedBlock.setPosition(containedBlockPosition());
            containedBlock.spawnEntity();
        }
    }

    @Override
    public void despawnEntity() {
        if (containedBlock != null) {
            containedBlock.despawnEntity();
        }
        super.despawnEntity();
    }

    @Override
    public void moveRelativeRaw(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        super.moveRelativeRaw(relX, relY, relZ, yaw, pitch, headYaw, isOnGround);
        moveContainedBlock(false);
    }

    @Override
    public void moveAbsoluteRaw(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        super.moveAbsoluteRaw(position, yaw, pitch, headYaw, isOnGround, teleported);
        moveContainedBlock(teleported);
    }

    @Override
    protected void setInvisible(boolean value) {
        super.setInvisible(value);
        moveContainedBlock(true);
    }

    @Override
    protected void applyScale() {
        super.applyScale();
        if (containedBlock != null) {
            containedBlock.setScale(scale * attributeScale);
            if (containedBlock.isValid()) {
                containedBlock.updateBedrockMetadata();
            }
            moveContainedBlock(true);
        }
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
