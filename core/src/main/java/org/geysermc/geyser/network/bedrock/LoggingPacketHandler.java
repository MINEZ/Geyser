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

package org.geysermc.geyser.network.bedrock;

import org.cloudburstmc.protocol.bedrock.BedrockDisconnectReasons;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.event.FireResult;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.NeteaseShopEvent;
import org.geysermc.geyser.network.bedrock.netease.NeteaseMsgPack;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.geysermc.geyser.network.bedrock.netease.NeteasePyRpc;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.GatedLoginImpl;
import org.geysermc.geyser.text.GeyserLocale;

import java.util.Map;

/**
 * Bare bones implementation of BedrockPacketHandler suitable for extension.
 * <p>
 * Logs and ignores all packets presented. Allows subclasses to override/implement only
 * packets of interest and limit boilerplate code.
 */
public class LoggingPacketHandler implements BedrockPacketHandler {
    protected final GeyserImpl geyser;
    protected final GeyserSession session;
    /**
     * MINEZ：上一次响应服务器菜单按钮的时间（{@link System#nanoTime()}），用于限制频率。
     */
    private long lastMenuRequest;
    private boolean hasMenuRequest;

    LoggingPacketHandler(GeyserImpl geyser, GeyserSession session) {
        this.geyser = geyser;
        this.session = session;
    }

    PacketSignal defaultHandler(BedrockPacket packet) {
        geyser.getLogger().debug("Handled packet: " + packet.getClass().getSimpleName());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        if (packet instanceof NeteasePacket neteasePacket) {
            // MINEZ：网易专有的数据包都不转发给 Java 端。其中网易商城的通知发出 NeteaseShopEvent，服务器菜单按钮替玩家执行 /menu，其余记下后丢弃。
            handleNetease(neteasePacket);
            return PacketSignal.HANDLED;
        }
        return BedrockPacketHandler.super.handlePacket(packet);
    }

    /**
     * MINEZ：从网易专有的数据包中认出网易商城的“购买成功”与“催促发货”，发出 {@link NeteaseShopEvent}；认出 MINEZ 客户端模组中服务器菜单按钮的事件，
     * 替玩家打开服务器菜单；认出模组载入后告知版本的事件，交给登录闸门（见 {@link GatedLoginImpl#clientModReady}）。
     * <p>
     * 只处理中国版会话：只有网易的编解码器会解出这些包，这里再以编解码器核对一次。格式不符、超大或嵌套过深的 PyRpc 只记 debug 日志，
     * 不断开连接。
     */
    private void handleNetease(NeteasePacket packet) {
        if (session.isClosed() || !GameProtocol.isNeteaseCodec(session.getUpstream().getSession().getCodec())) {
            geyser.getLogger().debug("Dropped China Edition packet " + packet + " from " + session.bedrockUsername());
            return;
        }
        NeteaseShopEvent event;
        if (packet instanceof NeteasePacket.PyRpc) {
            NeteasePyRpc.Message message;
            try {
                message = NeteasePyRpc.decode(packet.payload());
            } catch (NeteaseMsgPack.FormatException e) {
                geyser.getLogger().debug("Dropped malformed China Edition packet " + packet + " from " + session.bedrockUsername() + ": " + e.getMessage());
                return;
            }
            if (message.isOpenMenu()) {
                openMenu();
                return;
            }
            if (message.isClientModReady()) {
                Object version = message.data().get("version");
                geyser.getLogger().debug(session.bedrockUsername() + " has MINEZ's client mod " + version + " loaded");
                GatedLoginImpl gated = session.getGatedLogin();
                if (gated != null) {
                    gated.clientModReady(version instanceof String text ? text : null);
                }
                session.getMinezClientUi().clientReady(version instanceof String text ? text : null);
                return;
            }
            if (message.isItemTable()) {
                session.getMinezClientUi().itemTable(message.data());
                return;
            }
            NeteaseShopEvent.Kind kind = message.shopKind();
            if (kind == null) {
                geyser.getLogger().debug("Dropped China Edition PyRpc " + message.summary() + " from " + session.bedrockUsername());
                return;
            }
            event = new NeteaseShopEvent(session, kind, message.eventName(), message.namespace(), message.systemName(), message.data());
        } else if (packet instanceof NeteasePacket.StoreBuySuccess) {
            // 网易专有的“购买成功”数据包，空包。
            event = new NeteaseShopEvent(session, NeteaseShopEvent.Kind.BUY_SUCCESS, "StoreBuySuccess", null, null, Map.of());
        } else {
            geyser.getLogger().debug("Dropped China Edition packet " + packet + " from " + session.bedrockUsername());
            return;
        }
        geyser.getLogger().debug("China Edition shop event " + event.kind() + " (" + event.eventName() + ") from " + session.bedrockUsername());
        FireResult result = geyser.eventBus().fire(event);
        if (!result.success()) {
            result.exceptions().values().forEach(e -> geyser.getLogger().error("A NeteaseShopEvent listener failed", e));
        }
    }

    /**
     * MINEZ 客户端模组中的服务器菜单按钮：替玩家执行 {@value #MENU_COMMAND}，与 Java 版的 Shift+F 相同。
     * <p>
     * 只在玩家已进入 Java 服务器的世界后响应；每 {@value #MENU_INTERVAL_MILLIS} 毫秒最多响应一次，连点时只执行第一次。
     */
    private void openMenu() {
        long now = System.nanoTime();
        if (hasMenuRequest && now - lastMenuRequest < MENU_INTERVAL_MILLIS * 1_000_000L) {
            geyser.getLogger().debug("Ignored a repeated server menu request from " + session.bedrockUsername());
            return;
        }
        hasMenuRequest = true;
        lastMenuRequest = now;
        session.ensureInEventLoop(() -> {
            if (session.isClosed() || !session.isSpawned()) {
                geyser.getLogger().debug("Ignored a server menu request from " + session.bedrockUsername() + " before spawning");
                return;
            }
            geyser.getLogger().debug("Opening the server menu for " + session.bedrockUsername());
            session.sendCommand(MENU_COMMAND);
        });
    }

    /**
     * MINEZ：服务器菜单按钮替玩家执行的命令，不带斜杠。
     */
    static final String MENU_COMMAND = "menu";
    /**
     * MINEZ：服务器菜单按钮的最短响应间隔（毫秒）。
     */
    static final long MENU_INTERVAL_MILLIS = 1000;

    @Override
    public void onDisconnect(CharSequence reason) {
        // Use our own disconnect messages for these reasons
        if (BedrockDisconnectReasons.CLOSED.contentEquals(reason)) {
            this.session.getUpstream().getSession().setDisconnectReason(GeyserLocale.getLocaleStringLog("geyser.network.disconnect.closed_by_remote_peer"));
        } else if (BedrockDisconnectReasons.TIMEOUT.contentEquals(reason)) {
            this.session.getUpstream().getSession().setDisconnectReason(GeyserLocale.getLocaleStringLog("geyser.network.disconnect.timed_out"));
        }
        this.session.disconnect(this.session.getUpstream().getSession().getDisconnectReason().toString());
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AnimatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BlockEntityDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BlockPickRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BookEditPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientCacheBlobStatusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientCacheMissResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientCacheStatusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientToServerHandshakePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraPresetsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraInstructionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CommandBlockUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CommandRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ContainerClosePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EntityEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EntityPickRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InteractPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InventoryContentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InventorySlotPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InventoryTransactionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LabTablePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LecternUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelEventGenericPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelSoundEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MapInfoRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MobArmorEquipmentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MobEquipmentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ModalFormResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MoveEntityAbsolutePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MovePlayerPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NetworkStackLatencyPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PhotoTransferPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerActionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerHotbarPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerInputPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerSkinPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PurchaseReceiptPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RequestChunkRadiusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RequestPermissionsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RiderJumpPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ServerSettingsRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetDefaultGameTypePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetPlayerGameTypePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SubChunkRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SubClientLoginPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TextPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddBehaviorTreePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddHangingEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddItemEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddPaintingPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddPlayerPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AvailableCommandsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BlockEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BossEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ChangeDimensionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ChunkRadiusUpdatedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientboundMapItemDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CommandOutputPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ContainerOpenPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ContainerSetDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CraftingDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(DisconnectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ExplodePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelChunkPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(GameRulesChangedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(GuiDataPickItemPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(HurtArmorPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AutomationClientConnectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MapCreateLockedCopyPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MobEffectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ModalFormRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MoveEntityDeltaPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NpcRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(OnScreenTextureAnimationPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerListPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlaySoundPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayStatusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RemoveEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RemoveObjectivePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackChunkDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackDataInfoPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePacksInfoPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackStackPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RespawnPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ServerSettingsResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ServerToClientHandshakePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetCommandsEnabledPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetDifficultyPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetDisplayObjectivePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetEntityDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetEntityLinkPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetEntityMotionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetHealthPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetLastHurtByPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetPlayerInventoryOptionsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetScoreboardIdentityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetScorePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetSpawnPositionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetTimePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetTitlePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ShowCreditsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ShowProfilePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ShowStoreOfferPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SimpleEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SpawnExperienceOrbPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StartGamePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StopSoundPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StructureBlockUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StructureTemplateDataRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StructureTemplateDataResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TakeItemEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TransferPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateAttributesPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateBlockPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateBlockPropertiesPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateBlockSyncedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateEquipPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateSoftEnumPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateTradePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AvailableEntityIdentifiersPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BiomeDefinitionListPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelSoundEvent2Packet packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NetworkChunkPublisherUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SpawnParticleEffectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(VideoStreamConnectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EmotePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TickSyncPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AnvilDamagePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NetworkSettingsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerAuthInputPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SettingsCommandPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EducationSettingsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CompletedUsingItemPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MultiplayerSettingsPacket packet) {
        return defaultHandler(packet);
    }

    // 1.16 new packets

    @Override
    public PacketSignal handle(DebugInfoPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EmoteListPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CodeBuilderPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CreativeContentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ItemStackRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelSoundEvent1Packet packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ItemStackResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerArmorDamagePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerEnchantOptionsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdatePlayerGameTypePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PacketViolationWarningPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PositionTrackingDBClientRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PositionTrackingDBServerBroadcastPacket packet) {
        return defaultHandler(packet);
    }

    // 1.16.100 new packets

    @Override
    public PacketSignal handle(MotionPredictionHintsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AnimateEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraShakePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerFogPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CorrectPlayerMovePredictionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ItemComponentPacket packet) {
        return defaultHandler(packet);
    }

    // 1.16.200 new packet

    @Override
    public PacketSignal handle(FilterTextPacket packet) {
        return defaultHandler(packet);
    }

    // 1.19.0 new packet

    @Override
    public PacketSignal handle(RequestAbilityPacket packet) {
        return defaultHandler(packet);
    }

    // 1.19.30 new packet

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        return defaultHandler(packet);
    }

    // todo: fix arrangement

    @Override
    public PacketSignal handle(ToggleCrafterSlotRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TrimDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MovementPredictionSyncPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ServerboundDiagnosticsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateClientOptionsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerUpdateEntityOverridesPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PartyChangedPacket packet) {
        return defaultHandler(packet);
    }
}
