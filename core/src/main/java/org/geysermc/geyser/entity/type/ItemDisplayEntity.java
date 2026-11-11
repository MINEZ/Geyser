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

package org.geysermc.geyser.entity.type;

import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;
import org.geysermc.geyser.entity.CustomBedrockEntityDefinition;
import org.geysermc.geyser.entity.DisplayModels;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

/**
 * MINEZ：物品展示实体。
 * <p>
 * 基岩版没有物品展示实体。映射文件 {@code display_entities} 中有这个物品模型与显示方式时（见 {@link DisplayModels}），显示成生成工具
 * 事先转好的自定义实体：模型已按显示方式的变换与物品展示实体自带的转向转好，实体只需放在同一位置、同一朝向，按变换中的缩放放大；
 * 染色（dyed_color，例如降落伞）以变种（VARIANT，0xRRGGBB）传给客户端，由实体的渲染控制器给模型中染色的面上色。没有对应的实体时
 * 基岩版照旧看不到。
 * <p>
 * 实体的样子要等物品（实体数据）到了才知道，而 Geyser 在生成 Java 版实体时就生成基岩版的实体，因此先不生成，有了物品再以对应的实体
 * 生成；物品换成别的模型时移除后重新生成，并重新骑上原来的载具（例如身上的翅膀骑在玩家身上）。
 */
public class ItemDisplayEntity extends DisplayBaseEntity {
    /**
     * Java 版染色物品的默认颜色（白色，不改变贴图的颜色）。
     */
    private static final int WHITE = 0xFFFFFF;

    private @Nullable ItemStack item;
    private int context;
    /**
     * 已经要求生成（Java 版的实体已出现）；按当前的物品与显示方式应显示成的自定义实体，与已经显示成的（null 为没有对应的实体，这时
     * 基岩版不生成）。
     */
    private boolean spawnRequested;
    private @Nullable CustomBedrockEntityDefinition wanted;
    private @Nullable CustomBedrockEntityDefinition shown;
    private int color = WHITE;

    public ItemDisplayEntity(EntitySpawnContext context) {
        super(context);
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        // 其余实体数据在生成时写入（putSpawnMetadata）：这里在父类的构造方法中调用，本类的字段还没有初始化。
        setFlag(EntityFlag.HAS_GRAVITY, false);
        setFlag(EntityFlag.HAS_COLLISION, false);
    }

    /**
     * 生成时要带上的实体数据。实体数据发出后即清空，重新生成（换模型）时须再写一次，否则缩放、碰撞箱等都会回到默认值。
     */
    private void putSpawnMetadata() {
        // 没有碰撞箱，玩家点不到、撞不到，也没有影子。
        this.metadata.put(EntityDataTypes.HITBOX, NbtMap.EMPTY);
        this.metadata.put(EntityDataTypes.WIDTH, 0f);
        this.metadata.put(EntityDataTypes.HEIGHT, 0f);
        this.metadata.put(EntityDataTypes.VARIANT, color);
        applyScale();
    }

    @Override
    public void spawnEntity() {
        spawnRequested = true;
        if (shown != null) {
            spawnShown();
        }
    }

    @Override
    public void despawnEntity() {
        spawnRequested = false;
        super.despawnEntity();
    }

    public void setItem(EntityMetadata<ItemStack, ?> entityMetadata) {
        this.item = entityMetadata.getValue();
        refresh();
    }

    public void setDisplayType(ByteEntityMetadata entityMetadata) {
        this.context = entityMetadata.getPrimitiveValue();
        refresh();
    }

    @Override
    public void setDisplayScale(EntityMetadata<Vector3f, ?> entityMetadata) {
        Vector3f value = entityMetadata.getValue();
        // 基岩版的实体只能整体缩放：各轴不同时取平均（MINEZ 的展示实体都是等比缩放）。
        setScale(value == null ? 1f : (value.getX() + value.getY() + value.getZ()) / 3f);
    }

    /**
     * 按物品与显示方式重新决定应显示成哪种实体与颜色。实体要等这一批实体数据都处理完（{@link #updateBedrockMetadata()}）再换，以免物品
     * 与显示方式在同一批中先后到达时生成两次。
     */
    private void refresh() {
        CustomBedrockEntityDefinition definition = null;
        int newColor = WHITE;
        if (item != null && item.getAmount() > 0) {
            GeyserItemStack stack = GeyserItemStack.from(session, item);
            Key model = stack.getComponent(DataComponentTypes.ITEM_MODEL);
            if (model != null) {
                definition = DisplayModels.get(model, context);
            }
            Integer dyed = stack.getComponent(DataComponentTypes.DYED_COLOR);
            if (dyed != null) {
                newColor = dyed & 0xFFFFFF;
            }
        }
        wanted = definition;
        if (newColor != color) {
            color = newColor;
            this.metadata.put(EntityDataTypes.VARIANT, color);
        }
    }

    @Override
    public void updateBedrockMetadata() {
        if (wanted != shown) {
            if (valid) {
                // 只移除基岩版的实体：Java 版的实体还在，骑乘关系不变。
                finishInterpolation();
                RemoveEntityPacket removeEntityPacket = new RemoveEntityPacket();
                removeEntityPacket.setUniqueEntityId(geyserId);
                session.sendUpstreamPacket(removeEntityPacket);
                valid = false;
            }
            shown = wanted;
            if (shown != null && spawnRequested) {
                spawnShown();
                return;
            }
        }
        super.updateBedrockMetadata();
    }

    private void spawnShown() {
        bedrockDefinition = shown;
        putSpawnMetadata();
        super.spawnEntity();
        relinkVehicle();
    }

    /**
     * 骑乘关系在基岩版的实体生成之前就可能已经到了（例如翅膀生成后立即骑上玩家），那时发出的骑乘没有生效，生成后重新发一次。
     */
    private void relinkVehicle() {
        if (vehicle == null || !vehicle.isValid() || !vehicle.getPassengers().contains(this)) {
            return;
        }
        boolean rider = vehicle.getPassengers().getFirst() == this;
        SetEntityLinkPacket linkPacket = new SetEntityLinkPacket();
        linkPacket.setEntityLink(new EntityLinkData(vehicle.geyserId(), geyserId,
            rider ? EntityLinkData.Type.RIDER : EntityLinkData.Type.PASSENGER, false, false, 0f));
        session.sendUpstreamPacket(linkPacket);
        updateMountOffset();
    }
}
