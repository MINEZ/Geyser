/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.protocol.java.entity.player;

import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.waypoint.GeyserWaypoint;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.PlayerListUtils;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Translator(packet = ClientboundPlayerInfoUpdatePacket.class)
public class JavaPlayerInfoUpdateTranslator extends PacketTranslator<ClientboundPlayerInfoUpdatePacket> {
    @Override
    public void translate(GeyserSession session, ClientboundPlayerInfoUpdatePacket packet) {
        Set<PlayerListEntryAction> actions = packet.getActions();

        if (actions.contains(PlayerListEntryAction.ADD_PLAYER)) {
            for (PlayerListEntry entry : packet.getEntries()) {
                GameProfile profile = entry.getProfile();

                if (profile == null) {
                    // Should never be null for the ADD_PLAYER case
                    // 1.21.11 client NPEs here
                    GeyserImpl.getInstance().getLogger().debug("Received a null profile in a player info update packet!");
                    continue;
                }

                UUID id = entry.getProfileId();
                boolean self = id.equals(session.getPlayerEntity().uuid());

                PlayerEntity playerEntity;
                if (self) {
                    // Entity is ourself!
                    playerEntity = session.getPlayerEntity();
                    playerEntity.setUsername(profile.getName());
                    playerEntity.setSkin(profile, () -> GeyserImpl.getInstance().getLogger().debug("Loaded Local Bedrock Java Skin Data for " + session.getClientData().getUsername()));
                } else {
                    // It's a new player
                    playerEntity = new PlayerEntity(EntitySpawnContext.DUMMY_CONTEXT.apply(session, id, VanillaEntities.PLAYER), profile);
                    session.getEntityCache().addPlayerEntity(playerEntity);
                }
            }
        }

        if (actions.contains(PlayerListEntryAction.UPDATE_DISPLAY_NAME)) {
            // MINEZ：玩家列表的显示名（例如带颜色与称号）同样显示在基岩版的玩家列表中。已列出的玩家要重发一次条目，基岩版没有只改名字的操作；
            // 同一个数据包中还要更新是否列出时，由下面统一发出。
            List<PlayerListPacket.Entry> toUpdate = new ArrayList<>();
            for (PlayerListEntry entry : packet.getEntries()) {
                boolean self = entry.getProfileId().equals(session.getPlayerEntity().uuid());
                PlayerEntity entity = self ? session.getPlayerEntity() : session.getEntityCache().getPlayerEntity(entry.getProfileId());
                if (entity == null) {
                    continue;
                }
                String listName = entry.getDisplayName() == null ? null : MessageTranslator.convertMessage(entry.getDisplayName(), session.locale());
                if (Objects.equals(listName, entity.getListName())) {
                    continue;
                }
                entity.setListName(listName);
                if (self) {
                    // MINEZ：客户端模组在暂停菜单的人物模型上方显示自己的名字与称号。
                    session.getMinezClientUi().selfNameChanged();
                }
                // 自己不在实体缓存中，isListed() 总是 false，但自己总在列表中。
                if ((self || entity.isListed()) && !actions.contains(PlayerListEntryAction.UPDATE_LISTED)
                    && !PlayerListUtils.shouldLimitPlayerListEntries(session)) {
                    PlayerListPacket.Entry playerListEntry = SkinManager.buildEntryFromCachedSkin(session, entity);
                    playerListEntry.setAction(PlayerListPacket.Action.ADD);
                    toUpdate.add(playerListEntry);
                }
            }
            if (!toUpdate.isEmpty()) {
                PlayerListUtils.batchSendPlayerList(session, toUpdate, PlayerListPacket.Action.ADD);
            }
        }

        if (actions.contains(PlayerListEntryAction.UPDATE_LISTED)) {
            // TODO This could probably be optimised for 26.40, each entry has the action now, so we can send one packet!
            List<PlayerListPacket.Entry> toAdd = new ArrayList<>();
            List<PlayerListPacket.Entry> toRemove = new ArrayList<>();

            for (PlayerListEntry entry : packet.getEntries()) {
                PlayerEntity entity = session.getEntityCache().getPlayerEntity(entry.getProfileId());
                if (entity == null) {
                    session.getGeyser().getLogger().debug("Ignoring player info update for " + entry.getProfileId());
                    continue;
                }

                if (entry.isListed()) {
                    if (!PlayerListUtils.shouldLimitPlayerListEntries(session)) {
                        PlayerListPacket.Entry playerListEntry = SkinManager.buildEntryFromCachedSkin(session, entity);
                        playerListEntry.setAction(PlayerListPacket.Action.ADD);
                        toAdd.add(playerListEntry);
                    }
                } else {
                    // No need to unlist players that were never listed
                    if (entity.isListed()) {
                        PlayerListPacket.Entry playerListEntry = new PlayerListPacket.Entry(entity.getTabListUuid());
                        playerListEntry.setAction(PlayerListPacket.Action.REMOVE);
                        toRemove.add(playerListEntry);
                    }
                }
                entity.setListed(entry.isListed());
            }

            if (!toAdd.isEmpty()) {
                PlayerListUtils.batchSendPlayerList(session, toAdd, PlayerListPacket.Action.ADD);
            }
            if (!toRemove.isEmpty()) {
                PlayerListUtils.batchSendPlayerList(session, toRemove, PlayerListPacket.Action.REMOVE);
            }
        }
    }
}
