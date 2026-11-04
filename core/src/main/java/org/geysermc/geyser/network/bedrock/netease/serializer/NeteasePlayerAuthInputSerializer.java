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

/*
 * MINEZ：本文件改写自 CloudburstMC Protocol（https://github.com/CloudburstMC/Protocol）中的 PlayerAuthInputSerializer_v766
 * 等序列化器，按网易中国版客户端的数据包格式修改了部分字段。CloudburstMC Protocol 以 Apache License 2.0 授权，
 * Copyright 2020 NukkitX，许可证全文见本仓库的
 * core/src/main/resources/META-INF/licenses/CloudburstMC-Protocol-LICENSE.txt，构建产物中也附带一份。
 */

package org.geysermc.geyser.network.bedrock.netease.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v766.serializer.PlayerAuthInputSerializer_v766;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.math.BigInteger;
import java.util.Set;

/**
 * MINEZ：网易 898 的 PlayerAuthInputPacket，与原版 898（v766 的格式）有三处不同：
 * <ol>
 *     <li>输入标志位在原版的 {@code STOP_FLYING}（第 43 位）之后插入两个网易保留位，原版此后的标志位在线上整体后移 2 位；</li>
 *     <li>位移增量 {@code delta} 之后多一个布尔值 {@code cameraDeparted}；</li>
 *     <li>包尾 {@code rawMoveVector} 之后追加：布尔 {@code thirdPersonPerspective}、两个 float {@code playerRotationToCamera}、
 *     布尔 {@code readyPosDeltaDirty}、布尔 {@code onGround}、一个字节 {@code resetPosition}。</li>
 * </ol>
 * 网易多出的字段 Geyser 用不到，读取后丢弃，写出时填默认值。包尾的字段只在还有剩余字节时读取。
 */
public class NeteasePlayerAuthInputSerializer extends PlayerAuthInputSerializer_v766 {
    public static final NeteasePlayerAuthInputSerializer INSTANCE = new NeteasePlayerAuthInputSerializer();

    /**
     * 第一个后移的原版标志位。
     */
    static final int FIRST_SHIFTED_FLAG = PlayerAuthInputData.STOP_FLYING.ordinal() + 1;
    /**
     * 网易插入的保留位数。
     */
    static final int RESERVED_FLAGS = 2;
    /**
     * 读取标志位时允许的最大位数，留有余量，网易以后再加的位不会让读取失败。
     */
    private static final int MAX_FLAG_BITS = 128;
    private static final PlayerAuthInputData[] FLAGS = PlayerAuthInputData.values();

    /**
     * @return 这个标志位在网易客户端线上的位置。
     */
    static int wireBit(PlayerAuthInputData flag) {
        int ordinal = flag.ordinal();
        return ordinal < FIRST_SHIFTED_FLAG ? ordinal : ordinal + RESERVED_FLAGS;
    }

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, PlayerAuthInputPacket packet) {
        Vector3f rotation = packet.getRotation();
        buffer.writeFloatLE(rotation.getX());
        buffer.writeFloatLE(rotation.getY());
        helper.writeVector3f(buffer, packet.getPosition());
        buffer.writeFloatLE(packet.getMotion().getX());
        buffer.writeFloatLE(packet.getMotion().getY());
        buffer.writeFloatLE(rotation.getZ());
        BigInteger flags = BigInteger.ZERO;
        for (PlayerAuthInputData flag : packet.getInputData()) {
            flags = flags.setBit(wireBit(flag));
        }
        VarInts.writeUnsignedBigVarInt(buffer, flags);
        VarInts.writeUnsignedInt(buffer, packet.getInputMode().ordinal());
        VarInts.writeUnsignedInt(buffer, packet.getPlayMode().ordinal());
        writeInteractionModel(buffer, helper, packet);
        helper.writeVector2f(buffer, packet.getInteractRotation());
        VarInts.writeUnsignedLong(buffer, packet.getTick());
        helper.writeVector3f(buffer, packet.getDelta());
        buffer.writeBoolean(false); // cameraDeparted
        Set<PlayerAuthInputData> inputData = packet.getInputData();
        if (inputData.contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)) {
            this.writeItemUseTransaction(buffer, helper, packet.getItemUseTransaction());
        }
        if (inputData.contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)) {
            helper.writeItemStackRequest(buffer, packet.getItemStackRequest());
        }
        if (inputData.contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
            VarInts.writeInt(buffer, packet.getPlayerActions().size());
            for (PlayerBlockActionData actionData : packet.getPlayerActions()) {
                writePlayerBlockActionData(buffer, helper, actionData);
            }
        }
        if (inputData.contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
            helper.writeVector2f(buffer, packet.getVehicleRotation());
            VarInts.writeLong(buffer, packet.getPredictedVehicle());
        }
        helper.writeVector2f(buffer, packet.getAnalogMoveVector());
        helper.writeVector3f(buffer, packet.getCameraOrientation());
        helper.writeVector2f(buffer, packet.getRawMoveVector());
        buffer.writeBoolean(false); // thirdPersonPerspective
        buffer.writeFloatLE(0f); // playerRotationToCamera
        buffer.writeFloatLE(0f);
        buffer.writeBoolean(false); // readyPosDeltaDirty
        buffer.writeBoolean(false); // onGround
        buffer.writeByte(0); // resetPosition
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, PlayerAuthInputPacket packet) {
        float x = buffer.readFloatLE();
        float y = buffer.readFloatLE();
        packet.setPosition(helper.readVector3f(buffer));
        packet.setMotion(Vector2f.from(buffer.readFloatLE(), buffer.readFloatLE()));
        float z = buffer.readFloatLE();
        packet.setRotation(Vector3f.from(x, y, z));
        BigInteger flags = VarInts.readUnsignedBigVarInt(buffer, MAX_FLAG_BITS);
        Set<PlayerAuthInputData> inputData = packet.getInputData();
        for (PlayerAuthInputData flag : FLAGS) {
            if (flags.testBit(wireBit(flag))) {
                inputData.add(flag);
            }
        }
        packet.setInputMode(INPUT_MODES[VarInts.readUnsignedInt(buffer)]);
        packet.setPlayMode(CLIENT_PLAY_MODES[VarInts.readUnsignedInt(buffer)]);
        readInteractionModel(buffer, helper, packet);
        packet.setInteractRotation(helper.readVector2f(buffer));
        packet.setTick(VarInts.readUnsignedLong(buffer));
        packet.setDelta(helper.readVector3f(buffer));
        buffer.readBoolean(); // cameraDeparted
        if (inputData.contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)) {
            packet.setItemUseTransaction(this.readItemUseTransaction(buffer, helper));
        }
        if (inputData.contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)) {
            packet.setItemStackRequest(helper.readItemStackRequest(buffer));
        }
        if (inputData.contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
            helper.readArray(buffer, packet.getPlayerActions(), VarInts::readInt, this::readPlayerBlockActionData, 32);
        }
        if (inputData.contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
            packet.setVehicleRotation(helper.readVector2f(buffer));
            packet.setPredictedVehicle(VarInts.readLong(buffer));
        }
        packet.setAnalogMoveVector(helper.readVector2f(buffer));
        packet.setCameraOrientation(helper.readVector3f(buffer));
        packet.setRawMoveVector(helper.readVector2f(buffer));
        // 包尾的网易字段：视角、相对相机的转向、位移是否待更新、是否着地、重置位置。Geyser 以 Java 服务端的判断为准，不读取。
        buffer.skipBytes(Math.min(buffer.readableBytes(), 1 + 4 + 4 + 1 + 1 + 1));
    }
}
