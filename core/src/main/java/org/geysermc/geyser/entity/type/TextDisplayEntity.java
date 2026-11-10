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

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.living.ArmorStandEntity;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// Note: 1.19.4 requires that the billboard is set to something in order to show, on Java Edition
@Getter
public class TextDisplayEntity extends DisplayBaseEntity {

    /**
     * The height offset per line of text in a text display entity when rendered
     * as an armor stand nametag on Bedrock Edition. This value was empirically adjusted
     * to match Java Edition's multi-line text centering behavior.
     */
    private static final float LINE_HEIGHT_OFFSET = 0.1414f;

    /**
     * On Java Edition, armor stands can have a custom name shown additionally to
     * the text in the display. They are rendered separately, and can cross each other...
     */
    private @Nullable ArmorStandEntity secondEntity = null;
    private boolean isInvisible = false;
    private int lineCount;
    /**
     * MINEZ：基岩版格式的文字；是否交给 MINEZ 客户端模组显示（文字中有文字图标时，见 MinezClientUi#showWorldText），这时名牌为空。
     */
    private String bedrockText = "";
    private boolean shownByMod;
    /**
     * MINEZ：Java 版的展示实体按 teleport_duration 在几刻内平滑移到新位置；基岩版客户端每次移动只插值约 3 刻，服务器每 4 刻移动一次
     * （例如路径点）时文字一顿一顿。因此按 Java 版的刻数，每刻发一次中间位置。移动的目标位置；正在进行的插值。
     */
    private @Nullable Vector3f moveTarget;
    private @Nullable ScheduledFuture<?> interpolation;

    public TextDisplayEntity(EntitySpawnContext context) {
        super(context);
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        // Remove armor stand body / hitbox
        this.metadata.put(EntityDataTypes.HITBOX, NbtMap.EMPTY);
        this.metadata.put(EntityDataTypes.SCALE, 0f);
        this.metadata.put(EntityDataTypes.NAMETAG_ALWAYS_SHOW, (byte) 1);
    }

    @Override
    protected void setInvisible(boolean value) {
        // we'll keep the text display armor stand always invisible; would reveal the armor stand otherwise
        // but we would need to adjust the nametag
        isInvisible = value;
        this.updateNameTag();
    }

    @Override
    public void setCustomNameVisible(BooleanEntityMetadata entityMetadata) {
        super.setCustomNameVisible(entityMetadata);
        this.updateNameTag();
    }

    @Override
    public void setCustomName(EntityMetadata<Optional<Component>, ?> entityMetadata) {
        super.setCustomName(entityMetadata);
        this.updateNameTag();
    }

    @Override
    public void setNametagAlwaysShow(boolean value) {
        // no-op
    }

    @Override
    protected void setNameEntityData(String nametag) {
        // no-op
    }

    @Override
    public void spawnEntity() {
        // MINEZ：交给模组显示的文字不写进名牌，生成实体之后再交给模组（模组的界面绑定在这个实体上）。
        if (session.getMinezClientUi().handlesWorldText(bedrockText)) {
            this.metadata.put(EntityDataTypes.NAME, "");
        }
        super.spawnEntity();
        applyText();
    }

    @Override
    public void despawnEntity() {
        cancelInterpolation();
        if (secondEntity != null) {
            secondEntity.despawnEntity();
        }
        if (shownByMod) {
            session.getMinezClientUi().hideWorldText(geyserId);
            shownByMod = false;
        }
        super.despawnEntity();
    }

    @Override
    public void moveRelativeRaw(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        if (secondEntity != null) {
            secondEntity.moveRelativeRaw(relX, relY, relZ, yaw, pitch, headYaw, isOnGround);
        }
        super.moveRelativeRaw(relX, relY, relZ, yaw, pitch, headYaw, isOnGround);
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
        int steps = teleportDuration;
        interpolate(start, position, 1, steps, yaw, pitch, headYaw, isOnGround);
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

    private void cancelInterpolation() {
        if (interpolation != null) {
            interpolation.cancel(false);
            interpolation = null;
        }
        moveTarget = null;
    }

    @Override
    public void moveAbsoluteRaw(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        if (secondEntity != null) {
            secondEntity.moveAbsoluteRaw(position.down(LINE_HEIGHT_OFFSET), yaw, pitch, headYaw, isOnGround, teleported);
        }
        super.moveAbsoluteRaw(position, yaw, pitch, headYaw, isOnGround, teleported);
    }

    public void setText(EntityMetadata<Component, ?> entityMetadata) {
        this.bedrockText = MessageTranslator.convertMessage(entityMetadata.getValue(), session.locale());
        if (valid) {
            applyText();
        } else {
            this.metadata.put(EntityDataTypes.NAME, bedrockText);
        }
        int oldLineCount = this.lineCount;
        this.lineCount = calculateLineCount(entityMetadata.getValue());

        // If the line count changed, update the position to account for the new offset
        if (this.lineCount != oldLineCount) {
            setOffset(calculateLineOffset());
            moveAbsoluteRaw(position, yaw, pitch, headYaw, onGround, false);
        }
    }

    /**
     * MINEZ：重新决定由模组还是名牌显示（例如模组显示不了时改回名牌），并发出变化。
     */
    public void refreshText() {
        if (valid) {
            applyText();
            updateBedrockMetadata();
        }
    }

    /**
     * MINEZ：已生成的实体：文字中有文字图标时交给模组显示、名牌为空，否则以名牌显示。
     */
    private void applyText() {
        shownByMod = session.getMinezClientUi().showWorldText(geyserId, bedrockText);
        this.metadata.put(EntityDataTypes.NAME, shownByMod ? "" : bedrockText);
    }

    private int calculateLineCount(@Nullable Component text) {
        if (text == null) {
            return 0;
        }
        return PlainTextComponentSerializer.plainText().serialize(text).split("\n").length;
    }

    @Override
    public void updateBedrockMetadata() {
        // Bundle metadata updates to ensure they aren't ignored
        if (secondEntity != null) {
            if (!secondEntity.valid) { // Spawn the entity once
                secondEntity.spawnEntity();
            } else {
                secondEntity.updateBedrockMetadata();
            }
        }
        super.updateBedrockMetadata();
    }

    public void updateNameTag() {
        // Text displays are special: customNameVisible must be set for the custom name to ever show
        if (this.nametag.isBlank() || isInvisible || !customNameVisible) {
            if (secondEntity != null) {
                secondEntity.despawnEntity();
                secondEntity = null;
            }
            return;
        }

        if (this.secondEntity == null) {
            secondEntity = new ArmorStandEntity(EntitySpawnContext.inherited(session, VanillaEntities.ARMOR_STAND, this, position.down(LINE_HEIGHT_OFFSET)));
        }
        secondEntity.getMetadata().put(EntityDataTypes.NAME, this.nametag);
        secondEntity.getMetadata().put(EntityDataTypes.NAMETAG_ALWAYS_SHOW, (byte) 1);
        // Scale to 0 to show nametag
        secondEntity.setScale(0f);
        // No bounding box as we don't want to interact with this entity
        secondEntity.getMetadata().put(EntityDataTypes.WIDTH, 0.0f);
        secondEntity.getMetadata().put(EntityDataTypes.HEIGHT, 0.0f);
        secondEntity.getMetadata().put(EntityDataTypes.HITBOX, NbtMap.EMPTY);
    }

    /**
     * Calculates the Y offset needed to match Java Edition's text centering
     * behavior for multi-line text displays.
     * In Java Edition, multi-line text displays are centered vertically.
     *
     * @return the Y offset to apply based on the number of lines
     */
    public float calculateLineOffset() {
        if (lineCount == 0) {
            return 0;
        }
        return -0.6f + LINE_HEIGHT_OFFSET * lineCount;
    }
}
