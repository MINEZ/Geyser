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

package org.geysermc.geyser.api;

import org.geysermc.api.Geyser;
import org.geysermc.api.GeyserApiBase;
import org.geysermc.api.util.ApiVersion;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.extension.ExtensionManager;
import org.geysermc.geyser.api.login.LoginGate;
import org.geysermc.geyser.api.network.BedrockListener;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.api.util.MinecraftVersion;
import org.geysermc.geyser.api.util.PlatformType;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Represents the API used in Geyser.
 */
@ApiStatus.NonExtendable
public interface GeyserApi extends GeyserApiBase {
    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable GeyserConnection connectionByUuid(UUID uuid);

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable GeyserConnection connectionByXuid(String xuid);

    /**
     * {@inheritDoc}
     */
    List<? extends GeyserConnection> onlineConnections();

    /**
     * Gets the {@link ExtensionManager}.
     *
     * @return the extension manager
     */
    ExtensionManager extensionManager();

    /**
     * Provides an implementation for the specified API type.
     *
     * @param apiClass the builder class
     * @param <R> the implementation type
     * @param <T> the API type
     * @throws IllegalArgumentException if there is no provider for the specified API class
     * @return the builder instance
     */
    <R extends T, T> R provider(Class<T> apiClass, @Nullable Object... args);

    /**
     * Gets the {@link EventBus} for handling
     * Geyser events.
     *
     * @return the event bus
     */
    EventBus<EventRegistrar> eventBus();

    /**
     * Gets the default {@link RemoteServer} configured
     * within the config file that is used by default.
     *
     * @return the default remote server used within Geyser
     */
    RemoteServer defaultRemoteServer();

    /**
     * Gets the {@link BedrockListener} used for listening
     * for Minecraft: Bedrock Edition client connections.
     *
     * @return the listener used for Bedrock client connectins
     */
    BedrockListener bedrockListener();

    /**
     * Gets the {@link Path} to the Geyser config directory.
     *
     * @return the path to the Geyser config directory
     */
    Path configDirectory();

    /**
     * Gets the {@link Path} to the Geyser packs directory.
     *
     * @return the path to the Geyser packs directory
     */
    Path packDirectory();

    /**
     * Gets {@link PlatformType} the extension is running on
     *
     * @return type of platform
     */
    PlatformType platformType();

    /**
     * Gets the version of Java Minecraft that is supported.
     *
     * @return the supported version of Java Minecraft
     */
    MinecraftVersion supportedJavaVersion();

    /**
     * Gets a list of Bedrock Minecraft versions that are supported.
     *
     * @return the list of supported Bedrock Minecraft versions
     */
    List<MinecraftVersion> supportedBedrockVersions();

    /**
     * Gets the {@link CommandSource} for the console.
     *
     * @return the console command source
     */
    CommandSource consoleCommandSource();

    /**
     * MINEZ：注册登录闸门，见 {@link LoginGate}。同时至多注册一个。
     *
     * @param gate 登录闸门。
     * @throws IllegalStateException 已经注册了别的闸门。
     */
    void registerLoginGate(LoginGate gate);

    /**
     * MINEZ：注销登录闸门。仍在闸门中等待的玩家以 {@link org.geysermc.geyser.api.login.GatedLogin.CancelReason#UNREGISTERED}
     * 结束并被断开；之后登录的玩家照旧直接连接 Java 端。
     *
     * @param gate 登录闸门。
     * @return 它是否为当前注册的闸门。
     */
    boolean unregisterLoginGate(LoginGate gate);

    /**
     * MINEZ：按代理为 Geyser 的进程内直连分配的会话 ID 取得会话。
     * <p>
     * 只有 Geyser 以进程内的直连（{@code use-direct-connection}，Netty 的 {@code LocalChannel}）连入的代理会登记：
     * 目前只有 Geyser-Velocity，会话 ID 即 Velocity 的 {@code InboundConnection#getSessionId()}（Velocity 4.3.0 起）。
     * 经网络连入的连接一律查不到，所以查得到就说明这个连接确实来自本进程中的 Geyser，而不是冒充的网络客户端。
     * 登记从 Velocity 为这个连接调用初始化器时开始，到连接关闭时结束；会话已关闭时也查不到。
     *
     * @param proxySessionId 代理的会话 ID。
     * @return 会话，查不到时为 null。
     */
    @Nullable GeyserConnection connectionByProxySessionId(UUID proxySessionId);

    /**
     * MINEZ：基岩版玩家自己的皮肤图，即登录时客户端发来的皮肤（Persona 皮肤没有）。用于让 Java 版玩家看到基岩版玩家的皮肤。
     * 只提供标准人形模型（geometry.humanoid.custom、geometry.humanoid.customSlim）的皮肤：网易的 4D 皮肤等自带模型的皮肤，
     * 贴图按其模型排布，套在 Java 版的人形上是一团错位的色块，不提供，Java 版看到的是默认皮肤。
     *
     * @param uuid 玩家在 Java 端的 UUID。
     * @return RGBA 像素（宽 × 高 × 4 字节，常见为 64×64 或 128×128），不是本服在线的基岩版玩家、没有皮肤图或不是标准人形模型时为 null。
     */
    default byte @Nullable [] bedrockSkin(UUID uuid) {
        return null;
    }

    /**
     * MINEZ：基岩版玩家自己的皮肤是否为纤细模型，见 {@link #bedrockSkin(UUID)}。
     *
     * @param uuid 玩家在 Java 端的 UUID。
     * @return 是否为纤细模型，未知时为 false。
     */
    default boolean bedrockSkinSlim(UUID uuid) {
        return false;
    }

    /**
     * MINEZ：为档案中没有材质属性的玩家（例如 Java 版玩家：网易验证不返回材质）提供皮肤，基岩版玩家看到的是这个皮肤，而不是默认皮肤。
     * 已经看到这名玩家的基岩版玩家随即更新。玩家离开时以 null 移除。
     *
     * @param uuid 玩家 UUID。
     * @param rgba RGBA 像素，宽 × 高 × 4 字节（64×64、64×32 或宽为 64 的整数倍的高清皮肤）；null 表示移除。
     * @param slim 是否为纤细模型。
     */
    default void provideSkin(UUID uuid, byte @Nullable [] rgba, boolean slim) {
    }

    /**
     * Gets the current {@link GeyserApiBase} instance.
     *
     * @return the current geyser api instance
     */
    static GeyserApi api() {
        return Geyser.api(GeyserApi.class);
    }

    /**
     * Returns the {@link ApiVersion} representing the current Geyser api version.
     * See the <a href="https://github.com/geysermc/api/blob/master/geyser-versioning.md">Geyser version outline</a>)
     *
     * @return the current geyser api version
     */
     default ApiVersion geyserApiVersion() {
        return BuildData.API_VERSION;
     }
}
