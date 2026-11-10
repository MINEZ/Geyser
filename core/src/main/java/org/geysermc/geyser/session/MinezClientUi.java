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

package org.geysermc.geyser.session;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.network.bedrock.netease.MinezClientMod;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.geysermc.geyser.network.bedrock.netease.NeteasePyRpc;
import org.geysermc.geyser.entity.type.TextDisplayEntity;
import org.geysermc.geyser.item.ModelIcons;
import org.geysermc.geyser.text.SpriteGlyphs;
import org.geysermc.geyser.util.PlayerListUtils;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MINEZ：与 MINEZ 客户端模组配合显示动态图标（例如称号）。
 * <p>
 * 基岩版的字形不能播放动画。客户端模组载入后（{@link #clientReady}），Geyser 把动态图标的表（{@link SpriteGlyphs#animated()}）发给模组，
 * 模组隐藏原版游戏画面中的聊天栏，自己画一个，在其中把这些字符换成播放动画的图片。此后每一条显示在聊天栏中的消息，除了照常发出
 * {@link TextPacket}（打开聊天界面后的历史消息仍由原版显示），还另以 {@value #CHAT_EVENT} 事件发给模组。
 * <p>
 * 只对中国版会话、且模组为 {@link #MIN_MOD_VERSION} 起时生效；更早的模组或没有就绪时一切照旧。
 */
public final class MinezClientUi {
    /**
     * 发给模组的事件的命名空间与系统名，与模组的客户端系统一致。
     */
    public static final String NAMESPACE = NeteasePyRpc.MENU_NAMESPACE;
    public static final String SYSTEM = NeteasePyRpc.MENU_SYSTEM;
    /**
     * 模组就绪后发出的配置：{@code chat} 为 1 时模组接管聊天栏，{@code sprites} 为动态图标的表，{@code icons} 为物品栏中实时渲染的
     * 3D 物品图标的表（见 {@link ModelIcons}）；Geyser 启动后、核对完成之前，每个会话的第一次另有 {@code item_table}，请模组读出网易客户端的物品表（见
     * {@link MinezItemTableCheck}）。
     */
    public static final String CONFIG_EVENT = "ClientConfig";
    /**
     * 一条聊天栏消息：{@code text} 为基岩版格式的文字（§ 颜色代码），可能有多行。
     */
    public static final String CHAT_EVENT = "ChatMessage";
    /**
     * 自己在玩家列表中的名字：{@code text} 为基岩版格式的文字，称号换到第二行（见 {@link SpriteGlyphs#groupOnNewLine}）。模组用它在暂停菜单中
     * 人物模型的上方显示名字与播放动画的称号，代替原版只能显示第一帧的名牌。
     */
    public static final String NAME_EVENT = "PlayerName";
    /**
     * 复制到剪贴板：{@code text} 为要复制的文字，模组以网易的 SetClipboardContent 写入系统剪贴板。对话框中“复制到剪贴板”的按钮
     * （Java 版的 copy_to_clipboard 动作）在基岩版中由此实现，见 {@link #copyToClipboard}。
     */
    public static final String CLIPBOARD_EVENT = "Clipboard";
    /**
     * 世界中的文字（文本展示实体）：{@code id} 为实体的编号，{@code text} 为基岩版格式的文字，可能有多行；没有 {@code text} 时撤下。
     * 文字中有文字图标时，基岩版的名牌只能把图标显示为约 1.6 倍大的字形，因此交给模组以绑定在实体上的界面显示，图标与 Java 版一样大，
     * 见 {@link #showWorldText}。
     */
    public static final String WORLD_TEXT_EVENT = "WorldText";
    /**
     * 能以图片显示全部文字图标（配置中的 {@code images}）与世界中的文字的模组的最低版本。
     */
    public static final MinezClientMod.Version TEXT_ICONS_MOD_VERSION = new MinezClientMod.Version(1, 9, 0);
    /**
     * 能复制到剪贴板的模组的最低版本。
     */
    public static final MinezClientMod.Version CLIPBOARD_MOD_VERSION = new MinezClientMod.Version(1, 8, 0);
    /**
     * 能显示动态图标的模组的最低版本。
     */
    public static final MinezClientMod.Version MIN_MOD_VERSION = new MinezClientMod.Version(1, 4, 0);

    /**
     * 显示在聊天栏中的消息类型。动作栏（TIP、POPUP、JUKEBOX_POPUP）不在其中。
     */
    private static final Set<TextPacket.Type> CHAT_TYPES = EnumSet.of(TextPacket.Type.RAW, TextPacket.Type.CHAT,
        TextPacket.Type.TRANSLATION, TextPacket.Type.SYSTEM, TextPacket.Type.WHISPER, TextPacket.Type.ANNOUNCEMENT);

    private final GeyserSession session;
    private final MinezItemTableCheck itemTableCheck;
    private volatile boolean ready;
    private volatile MinezClientMod.@Nullable Version version;
    /**
     * 交给模组显示的世界中的文字：{实体编号: 文字}。
     */
    private final Map<Long, String> worldTexts = new ConcurrentHashMap<>();
    /**
     * 模组不能把世界中的文字绑定到实体上时，这个会话改回以名牌显示。
     */
    private volatile boolean worldTextsFailed;
    private boolean itemTableRequested;

    MinezClientUi(GeyserSession session) {
        this.session = session;
        this.itemTableCheck = new MinezItemTableCheck(session);
    }

    /**
     * 客户端模组载入、界面初始化完成后（每次进入世界都会）调用：发出配置，此后转发聊天栏消息。
     *
     * @param version 模组报告的版本，没有或格式不对时为 null。
     */
    public void clientReady(@Nullable String version) {
        MinezClientMod.Version loaded = MinezClientMod.Version.parse(version);
        if (!isNetease() || loaded == null || loaded.compareTo(MIN_MOD_VERSION) < 0) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chat", 1);
        data.put("sprites", SpriteGlyphs.animated());
        data.put("icons", ModelIcons.icons());
        if (loaded.compareTo(TEXT_ICONS_MOD_VERSION) >= 0) {
            data.put("images", SpriteGlyphs.images());
        }
        if (!itemTableRequested) {
            // 每个会话最多请模组读一次网易客户端的物品表（切换维度时界面会重新初始化，模组再次就绪）；这次启动后已经核对过时不再请求。
            Map<String, Object> request = itemTableCheck.request();
            if (request != null) {
                itemTableRequested = true;
                data.put("item_table", request);
            }
        }
        send(CONFIG_EVENT, data);
        this.version = loaded;
        ready = true;
        selfNameChanged();
        session.getGeyser().getLogger().debug("Sent the client config to MINEZ's client mod of " + session.bedrockUsername()
            + " with " + SpriteGlyphs.animated().size() + " animated sprites");
    }

    /**
     * 收到模组读出的网易客户端的物品表（一批），见 {@link MinezItemTableCheck}。
     */
    public void itemTable(Map<String, Object> data) {
        if (ready && itemTableRequested) {
            itemTableCheck.receive(data);
        }
    }

    /**
     * 发给客户端之前调用：聊天栏中的消息另发给模组。
     */
    void beforeSend(BedrockPacket packet) {
        if (!ready || !(packet instanceof TextPacket text) || !CHAT_TYPES.contains(text.getType())) {
            return;
        }
        String message = text.getMessage();
        if (message == null) {
            return;
        }
        String source = text.getSourceName();
        if (text.getType() == TextPacket.Type.CHAT && source != null && !source.isEmpty()) {
            // 与原版的写法相同：<名字> 消息。
            message = "<" + source + "> " + message;
        }
        send(CHAT_EVENT, Map.of("text", message));
    }

    /**
     * 自己在玩家列表中的显示名改变后调用；模组就绪后（每次进入世界）也发一次。
     */
    public void selfNameChanged() {
        String name = ready ? PlayerListUtils.listName(session.getPlayerEntity()) : null;
        if (name != null) {
            send(NAME_EVENT, Map.of("text", SpriteGlyphs.groupOnNewLine(name)));
        }
    }

    /**
     * 请模组把文字复制到系统剪贴板。
     *
     * @return 是否已交给模组；模组没有就绪或版本太旧时为 false，由调用方另想办法（例如显示文字让玩家自己复制）。
     */
    public boolean copyToClipboard(String text) {
        MinezClientMod.Version loaded = version;
        if (!ready || loaded == null || loaded.compareTo(CLIPBOARD_MOD_VERSION) < 0) {
            return false;
        }
        send(CLIPBOARD_EVENT, Map.of("text", text));
        return true;
    }

    /**
     * @return 这段世界中的文字（文本展示实体的文字）是否由模组显示：模组就绪、版本足够，并且文字中有文字图标。
     */
    public boolean handlesWorldText(String text) {
        MinezClientMod.Version loaded = version;
        return ready && !worldTextsFailed && loaded != null && loaded.compareTo(TEXT_ICONS_MOD_VERSION) >= 0
            && SpriteGlyphs.hasIcon(text);
    }

    /**
     * 请模组在实体上显示文字（实体已生成之后调用）；文字没有变化时不重发。
     *
     * @return 是否由模组显示（见 {@link #handlesWorldText}）；为 false 时调用方照常以名牌显示，之前交给模组的文字已撤下。
     */
    public boolean showWorldText(long id, String text) {
        if (!handlesWorldText(text)) {
            hideWorldText(id);
            return false;
        }
        if (!text.equals(worldTexts.put(id, text))) {
            send(WORLD_TEXT_EVENT, Map.of("id", id, "text", text));
        }
        return true;
    }

    /**
     * 撤下交给模组显示的文字（例如实体消失时）。
     */
    public void hideWorldText(long id) {
        if (worldTexts.remove(id) != null) {
            send(WORLD_TEXT_EVENT, Map.of("id", id));
        }
    }

    /**
     * 模组不能显示世界中的文字（绑定不到实体上）：这个会话此后都以名牌显示，交给模组的文字改回名牌。
     */
    public void worldTextFailed(Map<String, Object> data) {
        if (worldTextsFailed) {
            return;
        }
        worldTextsFailed = true;
        session.getGeyser().getLogger().warning("MINEZ's client mod of " + session.bedrockUsername()
            + " could not show the text of entity " + data.get("id") + ", using name tags for world texts in this session");
        session.ensureInEventLoop(() -> {
            for (Long id : worldTexts.keySet()) {
                if (session.getEntityCache().getEntityByGeyserId(id) instanceof TextDisplayEntity display) {
                    display.refreshText();
                }
            }
            worldTexts.clear();
        });
    }

    private boolean isNetease() {
        return GameProtocol.isNeteaseCodec(session.getUpstream().getSession().getCodec());
    }

    private void send(String event, Map<String, ?> data) {
        NeteasePacket.PyRpc packet = new NeteasePacket.PyRpc();
        packet.payload(NeteasePyRpc.encodeModEvent(NAMESPACE, SYSTEM, event, data));
        session.getUpstream().sendPacket(packet);
    }

    /**
     * @return 是否已收到模组就绪、正在转发聊天栏消息。
     */
    public boolean isReady() {
        return ready;
    }
}
