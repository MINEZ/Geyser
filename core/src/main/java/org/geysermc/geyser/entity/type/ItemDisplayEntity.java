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
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MINEZ：物品展示实体。
 * <p>
 * 基岩版没有物品展示实体。映射文件 {@code display_entities} 中有这个物品模型与显示方式时（见 {@link DisplayModels}），显示成生成工具
 * 事先转好的自定义实体：模型已按显示方式的变换与物品展示实体自带的转向转好，实体放在同一位置。没有对应的实体时基岩版照旧看不到。
 * 其余的由实体数据传给客户端，由实体的渲染控制器与动画（见 MINEZ/CraftengineResources 的生成工具）使用：
 * <ul>
 *     <li>染色（dyed_color，例如降落伞）：变种（VARIANT，0xRRGGBB），给模型中染色的面上色；</li>
 *     <li>朝向：基岩版实体自身的朝向一律为 0，Java 版的朝向写进标记变种（MARK_VARIANT，0~359 度），动画按它转动模型。骑在载具上时
 *     （例如翅膀骑在玩家身上），基岩版不按服务器发来的转向转动骑乘者，因此以座位把实体的朝向锁定为载具的朝向（偏移 0，见
 *     {@link org.geysermc.geyser.util.EntityUtils#updateRiderRotationLock}），标记变种为 0：模型随载具转动，不必等服务器同步。本服骑乘中的
 *     展示实体都朝着载具的方向（Java 版由脚本把翅膀的朝向同步为玩家的朝向，有 3 度的阈值与延迟）；</li>
 *     <li>缩放：实体的缩放（SCALE）为变换中水平两轴的平均，竖直方向与之的比值乘以 1000 写进皮肤编号（SKIN_ID），动画按它在竖直方向
 *     缩放模型（例如降落伞落地后塌下：竖直方向缩到 0.15）。变换有插值时间（interpolation_duration）时与 Java 版一样逐刻过渡。</li>
 * </ul>
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
    /**
     * Java 版的朝向（度），以标记变种发给客户端；正在显示的缩放；正在进行的缩放插值。
     */
    private float displayYaw;
    private Vector3f displayScale = Vector3f.ONE;
    private @Nullable ScheduledFuture<?> scaleInterpolation;

    public ItemDisplayEntity(EntitySpawnContext context) {
        super(context);
        this.displayYaw = this.yaw;
        this.yaw = 0;
        this.pitch = 0;
        this.headYaw = 0;
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
        this.metadata.put(EntityDataTypes.MARK_VARIANT, markVariant());
        putScale(displayScale);
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
        cancelScaleInterpolation();
        super.despawnEntity();
    }

    @Override
    public void moveRelativeRaw(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        setDisplayYaw(yaw);
        super.moveRelativeRaw(relX, relY, relZ, 0, 0, 0, isOnGround);
    }

    @Override
    public void moveAbsoluteRaw(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        setDisplayYaw(yaw);
        super.moveAbsoluteRaw(position, 0, 0, 0, isOnGround, teleported);
    }

    private void setDisplayYaw(float yaw) {
        int before = markVariant();
        this.displayYaw = yaw;
        if (markVariant() != before) {
            this.metadata.put(EntityDataTypes.MARK_VARIANT, markVariant());
            if (valid) {
                updateBedrockMetadata();
            }
        }
    }

    /**
     * 模型在实体自身朝向之外再转的角度（0~359 度）：骑乘中为 0（朝向锁定为载具的朝向），否则为 Java 版的朝向。
     */
    private int markVariant() {
        return vehicle != null ? 0 : Math.floorMod(Math.round(displayYaw), 360);
    }

    @Override
    public void setVehicle(Entity entity) {
        super.setVehicle(entity);
        // 由骑乘的变化处随后发出（updateBedrockMetadata）。
        this.metadata.put(EntityDataTypes.MARK_VARIANT, markVariant());
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
        Vector3f target = entityMetadata.getValue() == null ? Vector3f.ONE : entityMetadata.getValue();
        cancelScaleInterpolation();
        int steps = transformationInterpolationDuration;
        if (steps <= 1 || !valid) {
            showScale(target);
            return;
        }
        // 与 Java 版一样在 interpolation_duration 刻内过渡：每刻发一次中间的缩放。
        interpolateScale(displayScale, target, 1, steps);
    }

    private void interpolateScale(Vector3f start, Vector3f end, int step, int steps) {
        if (!valid) {
            displayScale = end;
            return;
        }
        showScale(step >= steps ? end : start.add(end.sub(start).mul((float) step / steps)));
        if (step < steps) {
            scaleInterpolation = session.scheduleInEventLoop(() -> interpolateScale(start, end, step + 1, steps), 50, TimeUnit.MILLISECONDS);
        } else {
            scaleInterpolation = null;
        }
    }

    private void cancelScaleInterpolation() {
        if (scaleInterpolation != null) {
            scaleInterpolation.cancel(false);
            scaleInterpolation = null;
        }
    }

    private void showScale(Vector3f scale) {
        displayScale = scale;
        putScale(scale);
        if (valid) {
            updateBedrockMetadata();
        }
    }

    /**
     * 基岩版的实体只能整体缩放：整体为水平两轴的平均，竖直方向与之的比值（千分之几）另写进皮肤编号，由动画缩放。
     */
    private void putScale(Vector3f scale) {
        float horizontal = (scale.getX() + scale.getZ()) / 2f;
        setScale(horizontal);
        int ratio = horizontal > 1.0e-4f ? Math.round(scale.getY() / horizontal * 1000f) : 1000;
        this.metadata.put(EntityDataTypes.SKIN_ID, ratio);
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
        // 座位的朝向锁定与位置一样重新写入：换模型重新生成时，骑乘时写入的实体数据早已发出、清空。
        EntityUtils.updateRiderRotationLock(this, vehicle, true);
        updateMountOffset();
    }
}
