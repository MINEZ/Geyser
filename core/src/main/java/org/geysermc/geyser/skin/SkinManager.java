/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.skin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.entity.type.player.AvatarEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.FileUtils;
import org.geysermc.geyser.util.PlayerListUtils;
import org.geysermc.geyser.util.WebUtils;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.texture.Texture;
import org.geysermc.mcprotocollib.auth.texture.TextureModel;
import org.geysermc.mcprotocollib.auth.texture.TextureType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.ResolvableProfile;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SkinManager {
    private static final int MAX_IMAGE_SIZE = 128 * 128 * 4;
    /**
     * The library's default limit for geometry read from a skin packet
     */
    private static final int MAX_GEOMETRY_SIZE = 512 * 1024;

    private static final Map<ResolvableProfile, CompletableFuture<GameProfile>> requestedProfiles = new ConcurrentHashMap<>();
    private static final Cache<ResolvableProfile, GameProfile> RESOLVED_PROFILES_CACHE = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build();
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    static final String GEOMETRY = new String(FileUtils.readAllBytes("bedrock/geometries/geo.json"), StandardCharsets.UTF_8);

    /**
     * Builds a Bedrock player list entry from our existing, cached Bedrock skin information
     */
    public static PlayerListPacket.Entry buildEntryFromCachedSkin(GeyserSession session, AvatarEntity playerEntity) {
        // First: see if we have the cached skin texture ID.
        GameProfileData data = GameProfileData.from(playerEntity);
        Skin skin = null;
        Cape cape = null;
        SkinGeometry geometry = SkinGeometry.WIDE;
        if (data != null) {
            // GameProfileData is not null = server provided us with textures data to work with.
            skin = SkinProvider.getCachedSkin(data.skinUrl());
            cape = SkinProvider.getCachedCape(data.capeUrl());
            geometry = data.isSlim() ? SkinGeometry.SLIM : SkinGeometry.WIDE;
        }

        if (skin == null || cape == null) {
            // The server either didn't have a texture to send, or we didn't have the texture ID cached.
            // Let's see if this player is a Bedrock player, and if so, let's pull their skin.
            // Otherwise, grab the default player skin
            SkinData fallbackSkinData = SkinProvider.determineFallbackSkinData(playerEntity.uuid());
            if (skin == null) {
                skin = fallbackSkinData.skin();
                geometry = fallbackSkinData.geometry();
            }
            if (cape == null) {
                cape = fallbackSkinData.cape();
            }
        }

        return PlayerListUtils.buildEntryManually(
                session,
                playerEntity.uuid(),
                PlayerListUtils.listName(playerEntity),
                playerEntity.geyserId(),
                getSkin(session, skin.textureUrl(), skin, cape, geometry, engineVersionFor(playerEntity.uuid(), geometry))
        );
    }

    /**
     * MINEZ：转发自带几何数据的基岩版皮肤时用它原本的引擎版本（见 {@link SkinProvider#bedrockGeometryEngineVersion}）；标准人形与 Java 版皮肤为 null。
     */
    private static @Nullable String engineVersionFor(UUID uuid, SkinGeometry geometry) {
        return geometry.geometryData().isBlank() ? null : SkinProvider.bedrockGeometryEngineVersion(uuid);
    }

    public static void sendSkinPacket(GeyserSession session, AvatarEntity entity, SkinData skinData) {
        Skin skin = skinData.skin();
        Cape cape = skinData.cape();
        SkinGeometry geometry = skinData.geometry();

        // Since 1.21.130 the client takes a player's skin from their player list entry, which means a
        // PlayerSkinPacket no longer applies to listed players (still broken as of 26.51). Always go through
        // the player list instead, as that works for both listed and unlisted entities.
        PlayerListPacket.Entry entry = PlayerListUtils.buildEntryManually(
            session,
            entity.uuid(),
            PlayerListUtils.listName(entity),
            entity.geyserId(),
            getSkin(session, skin.textureUrl(), skin, cape, geometry, engineVersionFor(entity.uuid(), geometry))
        );

        // Slight delay ensures skins are actually shown
        session.scheduleInEventLoop(() -> {
            PlayerListUtils.sendSkinUsingPlayerList(session, entry, entity, entity.isListed());
        }, 100, TimeUnit.MILLISECONDS);
    }

    private static SerializedSkin getSkin(GeyserSession session, String skinId, Skin skin, Cape cape, SkinGeometry geometry,
                                          @Nullable String engineVersion) {
        return SerializedSkin.builder()
            .skinId(skinId)
            .skinResourcePatch(geometry.geometryName())
            .skinData(ImageData.of(skin.skinData()))
            .capeData(ImageData.of(cape.capeData()))
            .geometryData(geometry.geometryData().isBlank() ? GEOMETRY : geometry.geometryData())
            .premium(true)
            .capeId(cape.capeId())
            .fullSkinId(skinId)
            .geometryDataEngineVersion(engineVersion != null ? engineVersion : session.getClientData().getGameVersion())
            .overridingPlayerAppearance(true)
            .color(new Color(0, true))
            .trusted(true)
            .profileHash("") // TODO Look into this, the session sends it, so it's probably important for skins to work correctly
            .build();
    }

    public static CompletableFuture<GameProfile> resolveProfile(ResolvableProfile profile) {
        GameProfile partial = profile.getProfile();
        if (!profile.isDynamic()) {
            // This is easy: the server has provided the entire profile for us (or however much it knew),
            // and is asking us to use this
            return CompletableFuture.completedFuture(partial);
        } else if (!partial.getProperties().isEmpty() || (partial.getId() == null && partial.getName() == null)) {
            // If properties have been provided to us, or no ID and no name have been provided, create a static profile from
            // what we do know
            // This replicates vanilla Java client behaviour
            String name = partial.getName() == null ? "" : partial.getName();
            UUID uuid = partial.getName() == null ? EMPTY_UUID : createOfflinePlayerUUID(partial.getName());
            GameProfile completed = new GameProfile(uuid, name);
            completed.setProperties(partial.getProperties());
            return CompletableFuture.completedFuture(completed);
        }

        GameProfile cached = RESOLVED_PROFILES_CACHE.getIfPresent(profile);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return requestedProfiles.computeIfAbsent(profile, resolvableProfile -> {
            CompletableFuture<GameProfile> future = (partial.getName() != null
                    ? SkinProvider.requestUUIDFromUsername(partial.getName()).thenApply(uuid -> new GameProfile(uuid, partial.getName()))
                    : SkinProvider.requestUsernameFromUUID(partial.getId()).thenApply(name -> new GameProfile(partial.getId(), name))
                ).thenCompose(nameAndUUID -> {
                        if (nameAndUUID.getId() == null || nameAndUUID.getName() == null) {
                            return CompletableFuture.completedFuture(partial);
                        }

                        return SkinProvider.requestTexturesFromUUID(nameAndUUID.getId())
                            .thenApply(encoded -> {
                                if (encoded == null) return partial;
                                nameAndUUID.setProperties(List.of(new GameProfile.Property("textures", encoded)));
                                return nameAndUUID;
                            });
                    })
                    .thenApply(resolved -> {
                        RESOLVED_PROFILES_CACHE.put(resolvableProfile, resolved);
                        return resolved;
                    });
            return future.whenComplete((r, t) -> requestedProfiles.remove(resolvableProfile));
        });
    }

    public static @Nullable Texture getTextureDataFromProfile(GameProfile profile, TextureType type) {
        Map<TextureType, Texture> textures;
        try {
            textures = profile.getTextures(false);
        } catch (IllegalStateException e) {
            GeyserImpl.getInstance().getLogger().debug("Could not decode textures from game profile (%s)! Got: %s", profile, e);
            return null;
        }

        if (textures == null) {
            return null;
        }
        return textures.get(type);
    }

    public static void requestAndHandleSkinAndCape(AvatarEntity entity, GeyserSession session, Consumer<SkinProvider.SkinAndCape> skinAndCapeConsumer) {
        SkinProvider.requestSkinData(entity, session).whenCompleteAsync((skinData, throwable) -> {
            if (skinData != null && skinData.geometry() != null) {
                sendSkinPacket(session, entity, skinData);
            }

            if (skinAndCapeConsumer != null) {
                skinAndCapeConsumer.accept(skinData == null ? null : new SkinProvider.SkinAndCape(skinData.skin(), skinData.cape()));
            }
        });
    }

    public static void handleBedrockSkin(AvatarEntity playerEntity, BedrockClientData clientData) {
        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser.config().debugMode()) {
            geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.skin.bedrock.register", playerEntity.getUsername(), playerEntity.uuid()));
        }

        try {
            byte[] skinBytes = clientData.getSkinData();
            byte[] capeBytes = clientData.getCapeData();

            byte[] geometryNameBytes = clientData.getGeometryName();
            byte[] geometryBytes = clientData.getGeometryData();

            byte[] engineVersionBytes = clientData.getGeometryDataEngineVersion();
            String engineVersion = engineVersionBytes == null ? null : new String(engineVersionBytes, StandardCharsets.UTF_8);
            boolean image = isImage(skinBytes);
            boolean geometryFits = geometryNameBytes.length + geometryBytes.length <= MAX_GEOMETRY_SIZE;
            // MINEZ：每名基岩版玩家记一行皮肤概况（不含皮肤内容），供排查 4D 皮肤等自带模型的皮肤在别人眼中透明的问题。
            String patch = new String(geometryNameBytes, StandardCharsets.UTF_8).replaceAll("\s+", "");
            geyser.getLogger().info("Bedrock skin of " + playerEntity.getUsername() + ": " + clientData.getSkinImageWidth() + "x"
                + clientData.getSkinImageHeight() + ", geometry " + geometryBytes.length + " bytes, engine version " + engineVersion
                + ", resource patch " + (patch.length() > 160 ? patch.substring(0, 160) + "..." : patch)
                + (clientData.isPersonaSkin() ? ", persona" : "") + (image ? "" : ", image not cached") + (geometryFits ? "" : ", geometry too large"));
            // MINEZ：网易的 4D 皮肤等自带模型的皮肤一律不用：转给其他基岩版玩家时常常是透明的（2026-11-21、11-22 正式服反馈），
            // 套在 Java 版的人形上又是错位的色块。不缓存，所有人（包括自己）看到的都是这名玩家的 Java 版皮肤，没有时为默认皮肤。
            boolean standardModel = SkinModels.isStandardHumanoid(new String(geometryNameBytes, StandardCharsets.UTF_8));
            if (!standardModel) {
                geyser.getLogger().info("Bedrock skin of " + playerEntity.getUsername() + " has its own model; showing the default skin instead");
            }
            if (image && !clientData.isPersonaSkin() && standardModel) {
                SkinProvider.storeBedrockSkin(playerEntity.uuid(), clientData.getSkinId(), skinBytes);
                if (geometryFits) {
                    SkinProvider.storeBedrockGeometry(playerEntity.uuid(), geometryNameBytes, geometryBytes, engineVersion);
                }
            } else if (geyser.config().debugMode()) {
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.skin.bedrock.fail", playerEntity.getUsername()));
                geyser.getLogger().debug("The size of '" + playerEntity.getUsername() + "' skin is: " + clientData.getSkinImageWidth() + "x" + clientData.getSkinImageHeight());
            }

            if (!clientData.getCapeId().isEmpty() && isImage(capeBytes)) {
                SkinProvider.storeBedrockCape(playerEntity.uuid(), clientData.getCapeId(), capeBytes);
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to cache skin for bedrock user (" + playerEntity.getUsername() + "): ", e);
        }
    }

    /**
     * Whether the data has a size Bedrock images can have.
     */
    private static boolean isImage(byte[] data) {
        if (data.length > MAX_IMAGE_SIZE) {
            return false;
        }
        try {
            ImageData.of(data);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static UUID createOfflinePlayerUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public record GameProfileData(String skinUrl, String capeUrl, boolean isSlim) {
        /**
         * Generate the GameProfileData from the given player entity
         *
         * @param entity entity to build the GameProfileData from
         * @return The built GameProfileData
         */
        public static @Nullable GameProfileData from(AvatarEntity entity) {
            Map<TextureType, Texture> textures = entity.getTextures();
            if (textures == null) {
                // Likely offline mode or failed to load
                // We'll fall back to default skins
                return null;
            }

            Texture skin = textures.get(TextureType.SKIN);
            if (skin == null) {
                return null;
            }

            String skinUrl = WebUtils.toHttps(skin.getURL());
            if (Objects.equals(DEFAULT_FLOODGATE_STEVE, skinUrl)) {
                // https://github.com/GeyserMC/Floodgate/commit/00b8b1b6364116ff4bc9b00e2015ce35bae8abb1 ensures that
                // Bedrock players on online-mode servers will always have a textures property. However, this skin is
                // also sent our way, and isn't overwritten. It's very likely that this skin is *only* a placeholder,
                // and no one should ever be using it outside of Floodgate, and therefore no one wants to see this
                // specific Steve skin.
                return null;
            }

            Texture cape = textures.get(TextureType.CAPE);
            String capeUrl = cape == null ? null : WebUtils.toHttps(cape.getURL());
            return new GameProfileData(skinUrl, capeUrl, skin.getModel() == TextureModel.SLIM);
        }

        private static final String DEFAULT_FLOODGATE_STEVE = "https://textures.minecraft.net/texture/31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb";
    }
}
