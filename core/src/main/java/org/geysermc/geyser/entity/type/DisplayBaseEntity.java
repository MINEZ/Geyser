/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DisplayBaseEntity extends Entity {

    private @NonNull Vector3f baseTranslation = Vector3f.ZERO;
    /**
     * MINEZ：Java 版的展示实体按 teleport_duration 在几刻内平滑移到新位置；基岩版客户端每次移动只插值约 3 刻，服务器每隔几刻移动一次
     * 时（例如路径点每 4 刻、降落中的空投每 2 刻）一顿一顿。因此按 Java 版的刻数，每刻发一次中间位置。插值的刻数；移动的目标位置；正在
     * 进行的插值。
     */
    protected int teleportDuration;
    /**
     * MINEZ：Java 版改变变换（缩放等）时插值的刻数（interpolation_duration），见 {@link ItemDisplayEntity}。
     */
    protected int transformationInterpolationDuration;
    private @Nullable Vector3f moveTarget;
    private @Nullable ScheduledFuture<?> interpolation;

    public DisplayBaseEntity(EntitySpawnContext context) {
        super(context);
    }

    public void setTranslation(EntityMetadata<Vector3f, ?> translationMeta) {
        Vector3f oldTranslation = this.baseTranslation;
        this.baseTranslation = translationMeta.getValue() == null ? Vector3f.ZERO : translationMeta.getValue();

        // If translations are the same, don't update
        if (Objects.equals(oldTranslation, this.baseTranslation)) {
            return;
        }

        if (this.vehicle == null) {
            this.setRiderSeatPosition(this.baseTranslation);
            this.moveAbsoluteRaw(position, yaw, pitch, headYaw, onGround, true);
        } else {
            EntityUtils.updateMountOffset(this, this.vehicle, true, true, 0, 1);
            this.updateBedrockMetadata();
        }
    }

    public void setTransformationInterpolationDuration(EntityMetadata<Integer, ?> entityMetadata) {
        Integer value = entityMetadata.getValue();
        this.transformationInterpolationDuration = value == null ? 0 : Math.max(0, value);
    }

    public void setTeleportDuration(EntityMetadata<Integer, ?> entityMetadata) {
        Integer value = entityMetadata.getValue();
        this.teleportDuration = value == null ? 0 : Math.max(0, value);
    }

    /**
     * MINEZ：变换中的缩放（scale）。文字展示实体不用（名牌的大小不随之变化）。
     */
    public void setDisplayScale(EntityMetadata<Vector3f, ?> entityMetadata) {
    }

    public Vector3f getTranslation() {
        return baseTranslation;
    }

    @Override
    public void despawnEntity() {
        cancelInterpolation();
        super.despawnEntity();
    }

    @Override
    public void moveRelative(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        if (teleportDuration > 1 && valid) {
            Vector3f from = moveTarget != null ? moveTarget : position;
            moveAbsolute(from.add(relX, relY, relZ), yaw, pitch, headYaw, isOnGround, false);
            return;
        }
        super.moveRelative(relX, relY, relZ, yaw, pitch, headYaw, isOnGround);
    }

    @Override
    public void moveAbsolute(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        cancelInterpolation();
        if (teleportDuration <= 1 || !valid) {
            super.moveAbsolute(position, yaw, pitch, headYaw, isOnGround, teleported);
            return;
        }
        // MINEZ：与 Java 版一样在 teleport_duration 刻内平滑移过去：每刻发一次中间位置（见 moveTarget）。
        Vector3f start = this.position;
        moveTarget = position;
        interpolate(start, position, 1, teleportDuration, yaw, pitch, headYaw, isOnGround);
    }

    private void interpolate(Vector3f start, Vector3f end, int step, int steps, float yaw, float pitch, float headYaw, boolean isOnGround) {
        if (!valid) {
            return;
        }
        Vector3f point = step >= steps ? end : start.add(end.sub(start).mul((float) step / steps));
        moveAbsoluteRaw(point, yaw, pitch, headYaw, isOnGround, false);
        if (step >= steps) {
            interpolation = null;
            moveTarget = null;
            return;
        }
        interpolation = session.scheduleInEventLoop(() -> interpolate(start, end, step + 1, steps, yaw, pitch, headYaw, isOnGround),
            50, TimeUnit.MILLISECONDS);
    }

    /**
     * MINEZ：立即移到正在插值的目标位置并停止插值（基岩版的实体要移除时，以免位置停在中途、此后的相对移动都偏一截）。
     */
    protected void finishInterpolation() {
        if (moveTarget != null) {
            setPosition(moveTarget);
        }
        cancelInterpolation();
    }

    protected void cancelInterpolation() {
        if (interpolation != null) {
            interpolation.cancel(false);
            interpolation = null;
        }
        moveTarget = null;
    }

    @Override
    public Vector3f bedrockPosition() {
        return super.bedrockPosition().add(baseTranslation);
    }
}
