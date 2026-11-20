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

import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.entity.properties.type.StringEnumProperty;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.cache.tags.ItemTag;
import org.geysermc.geyser.session.cache.tags.Tag;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;

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

    private static final int BABY_VARIANT = 1;
    private static final int ADULT_VARIANT = 2;

    private int previousFuseTickTime = -1;
    private int fuseTickTime = -1;
    /**
     * MINEZ: 体内装着的东西：0 没有、1 方块、2 TNT（见 legacyMarkVariant）。
     */
    private int contents = 0;
    private boolean baby;

    public SulfurCubeEntity(EntitySpawnContext context) {
        super(context);
    }

    @Override
    public void setBody(GeyserItemStack stack) {
        contents = stack.isEmpty() ? 0 : stack.asItem() == Items.TNT ? 2 : 1;
        MobEquipmentPacket handPacket = new MobEquipmentPacket();
        handPacket.setRuntimeEntityId(geyserId);
        // MINEZ: 基岩版原版吸收 TNT 时以 hide_held_items 隐藏手持的 TNT，改画内层的 TNT 贴图（引爆前闪白）；网易 1.21.130 不认
        // hide_held_items，手持的 TNT 不发。MINEZ 资源包的客户端实体对所有版本相同，各版本一样处理。
        boolean hideHeld = contents == 2;
        handPacket.setItem(ItemTranslator.translateToBedrock(session, hideHeld ? GeyserItemStack.EMPTY : stack));
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

        putLegacyMarkVariant();
        this.updateBedrockMetadata();
    }

    /**
     * MINEZ: 26.40 之前的客户端以史莱姆为硫方怪的基础实体（见 LegacyEntityIdentifiers），读不到实体属性 sulfur_cube_archetype，
     * 也不认 query.fuse_time。以标记变种告知 MINEZ 资源包中的客户端实体（对所有版本相同，各版本都发）：个位为体内装着的东西（0 没有、1 方块、2 TNT），引爆倒计时
     * （FUSE_TIME，0 起）加 1 后乘 10 加上去，没有点燃时为 0。
     */
    static int legacyMarkVariant(int contents, int fuse) {
        return contents + (fuse >= 0 ? (fuse + 1) * 10 : 0);
    }

    private void putLegacyMarkVariant() {
        this.metadata.put(EntityDataTypes.MARK_VARIANT, legacyMarkVariant(contents, fuseTickTime));
    }

    /**
     * MINEZ: 26.40 之前的客户端画手持物品时不按实体的缩放（SCALE）成比例缩放（缩放属性改过的硫方怪，体内的方块大小、位置都不对），
     * 却按客户端实体的 scaleX/Y/Z 成比例缩放。因此不发缩放属性的缩放，改以皮肤编号（SKIN_ID，缩放 × 1000）告知资源包中的客户端实体，
     * 由它乘进 scaleX/Y/Z；碰撞箱的宽高相应乘上（原先由客户端按 SCALE 放大）。MINEZ 资源包的客户端实体对所有版本相同，各版本都这样发。
     */
    private void putLegacyScale() {
        // 父类的构造方法中调用时缩放属性还没有初始化（为 0）
        float attribute = attributeScale > 0 ? attributeScale : 1f;
        this.metadata.put(EntityDataTypes.SCALE, scale);
        this.metadata.put(EntityDataTypes.SKIN_ID, Math.round(attribute * 1000));
        this.metadata.put(EntityDataTypes.WIDTH, getBoundingBoxWidth() * attribute);
        this.metadata.put(EntityDataTypes.HEIGHT, getBoundingBoxHeight() * attribute);
    }

    @Override
    protected void applyScale() {
        super.applyScale();
        putLegacyScale();
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        this.metadata.put(EntityDataTypes.VARIANT, ADULT_VARIANT);
    }

    /**
     * MINEZ: 基岩版的硫方怪以变种表示体型（行为包 minecraft:sulfur_cube 的 minecraft:variant：幼年 1、成年 2），
     * 客户端实体按它计算弹跳的压扁量（query.variant）。Geyser 原先不发，客户端中恒为 0。
     */
    @Override
    public void setBaby(BooleanEntityMetadata entityMetadata) {
        super.setBaby(entityMetadata);
        baby = entityMetadata.getPrimitiveValue();
        this.metadata.put(EntityDataTypes.VARIANT, baby ? BABY_VARIANT : ADULT_VARIANT);
        // MINEZ: 不发幼年标志：网易 1.21.130 对幼年生物的手持物品另有处理，幼年硫方怪体内的方块被画到上方约一格处。
        // MINEZ 资源包的客户端实体改按变种（1 为幼年）选幼年的几何体与贴图。
        setFlag(EntityFlag.BABY, false);
        putLegacyScale();
    }

    @Override
    public boolean isBaby() {
        return baby;
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
            putLegacyMarkVariant();
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
        putLegacyMarkVariant();
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
