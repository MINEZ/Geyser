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
import org.geysermc.geyser.text.SpriteGlyphs;
import org.geysermc.geyser.util.PlayerListUtils;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * MINEZ：与 MINEZ 客户端模组配合显示动态图标（例如称号）。
 * <p>
 * 基岩版的字形不能播放动画。客户端模组载入后（{@link #clientReady}），Geyser 把动态图标的表（{@link SpriteGlyphs#animated()}）发给模组，
 * 模组隐藏原版游戏画面中的聊天栏，自己画一个，在其中把这些字符换成播放动画的图片。此后每一条显示在聊天栏中的消息，除了照常发出
 * {@link TextPacket}（打开聊天界面后的历史消息仍由原版显示），还另以 {@value #CHAT_EVENT} 事件发给模组。
 * <p>
 * 以 U+E0FF 开头（前面只有颜色与格式代码）的系统消息（服务器只发给基岩版玩家）不显示在聊天栏中，改以 {@value #PREVIEW_EVENT}
 * 事件发给模组，由模组弹出预览框，例如佩戴称号后的效果。只认系统消息的开头，玩家在聊天中打出这个字符不会被当作预览。
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
     * 模组就绪后发出的配置：{@code chat} 为 1 时模组接管聊天栏，{@code sprites} 为动态图标的表。
     */
    public static final String CONFIG_EVENT = "ClientConfig";
    /**
     * 一条聊天栏消息：{@code text} 为基岩版格式的文字（§ 颜色代码），可能有多行。
     */
    public static final String CHAT_EVENT = "ChatMessage";
    /**
     * 弹出预览框：{@code text} 为预览的文字。
     */
    public static final String PREVIEW_EVENT = "Preview";
    /**
     * 自己在玩家列表中的名字：{@code text} 为基岩版格式的文字，称号换到第二行（见 {@link SpriteGlyphs#groupOnNewLine}）。模组用它在暂停菜单中
     * 人物模型的上方显示名字与播放动画的称号，代替原版只能显示第一帧的名牌。
     */
    public static final String NAME_EVENT = "PlayerName";
    /**
     * 消息中带有这个字符时（服务器以 {@code <&chr[E0FF]>} 写出）不显示在聊天栏中，改为预览框。
     */
    public static final String PREVIEW_MARKER = "\uE0FF";
    /**
     * 能显示动态图标与预览框的模组的最低版本。
     */
    public static final MinezClientMod.Version MIN_MOD_VERSION = new MinezClientMod.Version(1, 4, 0);

    /**
     * 显示在聊天栏中的消息类型。动作栏（TIP、POPUP、JUKEBOX_POPUP）不在其中。
     */
    private static final Set<TextPacket.Type> CHAT_TYPES = EnumSet.of(TextPacket.Type.RAW, TextPacket.Type.CHAT,
        TextPacket.Type.TRANSLATION, TextPacket.Type.SYSTEM, TextPacket.Type.WHISPER, TextPacket.Type.ANNOUNCEMENT);

    private final GeyserSession session;
    private volatile boolean ready;

    MinezClientUi(GeyserSession session) {
        this.session = session;
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
        send(CONFIG_EVENT, data);
        ready = true;
        selfNameChanged();
        session.getGeyser().getLogger().debug("Sent the client config to MINEZ's client mod of " + session.bedrockUsername()
            + " with " + SpriteGlyphs.animated().size() + " animated sprites");
    }

    /**
     * 发给客户端之前调用。
     *
     * @return 是否不要照常发出这个数据包（预览框的消息）。
     */
    boolean beforeSend(BedrockPacket packet) {
        if (!ready || !(packet instanceof TextPacket text) || !CHAT_TYPES.contains(text.getType())) {
            return false;
        }
        String message = text.getMessage();
        if (message == null) {
            return false;
        }
        if (text.getType() == TextPacket.Type.SYSTEM) {
            int start = 0;
            while (start + 1 < message.length() && message.charAt(start) == '\u00a7') {
                start += 2;
            }
            if (message.startsWith(PREVIEW_MARKER, start)) {
                send(PREVIEW_EVENT, Map.of("text", message.substring(0, start) + message.substring(start + PREVIEW_MARKER.length())));
                return true;
            }
        }
        String source = text.getSourceName();
        if (text.getType() == TextPacket.Type.CHAT && source != null && !source.isEmpty()) {
            // 与原版的写法相同：<名字> 消息。
            message = "<" + source + "> " + message;
        }
        send(CHAT_EVENT, Map.of("text", message));
        return false;
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
