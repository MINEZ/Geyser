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

import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.network.bedrock.netease.MinezClientMod;
import org.geysermc.geyser.network.bedrock.netease.NeteasePacket;
import org.geysermc.geyser.network.bedrock.netease.NeteasePyRpc;
import org.geysermc.geyser.entity.type.TextDisplayEntity;
import org.geysermc.geyser.item.ModelIcons;
import org.geysermc.geyser.level.EffectType;
import org.geysermc.geyser.registry.java.JavaRegistries;
import org.geysermc.geyser.text.SpriteGlyphs;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
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
     * 自己的用户名：{@code text} 为用户名，模组就绪时发一次。模组以收到它作为已进入 Java 版世界的标志（新手引导等它到了才显示）。
     * 原先是玩家列表中带颜色与称号的显示名，供模组在暂停菜单显示；暂停菜单与玩家列表已恢复原版，只显示用户名。
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
     * 玩家开始或停止飞行：{@code flying} 为 1 或 0。原版在飞行时显示纸娃娃，模组的聊天栏随之让位；网易的客户端接口读不到飞行状态，
     * 因此由 Geyser 告知，见 {@link #flyingChanged}。
     */
    public static final String FLYING_EVENT = "Flying";
    /**
     * 自己身上基岩版没有的 Java 版状态效果（发光、幸运、霉运、海豚的恩惠）：{"effects": [{"id", "amplifier", "duration", "ambient"}],
     * "shown": 基岩版效果栏中的图标数}，每次有变化时整个发出，duration 为剩余的刻数（-1 为无限）。基岩版的效果栏中没有这些效果
     * （{@link EffectType} 中基岩版的编号为 0），模组在游戏画面中照基岩版效果栏的样子画出图标，接在效果栏已有的 shown 个图标之后
     * （模组 1.20.1 起；效果栏中的效果增减时，身上有这些效果就重新发出）；效果的作用仍由服务器计算。
     */
    public static final String EFFECTS_EVENT = "JavaEffects";
    /**
     * 血月时钟的状态：{"ticks": 总刻数, "partial": 不足一刻的部分, "rate": 速度（暂停时为 0）}，收到时钟的变化或模组就绪时发出，
     * 模组据此自行推算。血月时钟是服务器数据包中的世界时钟 {@link #BLOOD_MOON_CLOCK}（见 MINEZ/DenizenScripts 的 world/blood_moon.dsc）：
     * 以 48000 刻为周期，24000~47999 对应开启时的一整天，Java 版由数据包的时间线把天空与雾染红、由 Bridge 模组画出红月；
     * 基岩版没有时间线，由模组（1.21.0 起）按同样的曲线改天空与雾的颜色、画出红月与血雨（1.22.0 起，见 {@link #bloodMoonState}）。
     */
    public static final String BLOOD_MOON_EVENT = "BloodMoon";
    public static final Key BLOOD_MOON_CLOCK = Key.key("minez", "blood_moon");
    /**
     * 血月期间发给基岩版的时间（刚日落，第 0 天即满月），见 {@link #bloodMoonHeldTime}。
     */
    public static final int BLOOD_MOON_HELD_TIME = 12500;
    /**
     * 停在 {@link #BLOOD_MOON_HELD_TIME} 的范围（血月时钟一个周期内的刻数，左闭右开）：主世界时间 12500~23500，即入夜到天亮前；
     * 血月时钟减去 24000 再加上 6000 即主世界时间。
     */
    private static final int BLOOD_MOON_HOLD_FROM = BLOOD_MOON_HELD_TIME - 6000 + 24000;
    private static final int BLOOD_MOON_HOLD_UNTIL = 23500 - 6000 + 24000;
    private static final int BLOOD_MOON_PERIOD = 48000;
    /**
     * 能以图片显示全部文字图标（配置中的 {@code images}）与世界中的文字的模组的最低版本。
     */
    public static final MinezClientMod.Version TEXT_ICONS_MOD_VERSION = new MinezClientMod.Version(1, 9, 0);
    /**
     * 能按飞行状态为纸娃娃让位的模组的最低版本。
     */
    public static final MinezClientMod.Version FLYING_MOD_VERSION = new MinezClientMod.Version(1, 12, 0);
    /**
     * 能画出 Java 版独有的状态效果的模组的最低版本。
     */
    public static final MinezClientMod.Version EFFECTS_MOD_VERSION = new MinezClientMod.Version(1, 20, 0);
    /**
     * 能显示血月的模组的最低版本。
     */
    public static final MinezClientMod.Version BLOOD_MOON_MOD_VERSION = new MinezClientMod.Version(1, 21, 0);
    /**
     * 血月期间改用原版月亮（转到固定位置）、因而需要停住时间的模组的最低版本，见 {@link #bloodMoonHeldTime}。
     */
    public static final MinezClientMod.Version BLOOD_MOON_HOLD_MOD_VERSION = new MinezClientMod.Version(1, 21, 5);
    /**
     * 不再停住时间的模组的最低版本（停在日落时基岩版看得到落日的余晖与白天的光照，模组改为调亮雾的颜色抵消夜里的变暗），
     * 见 {@link #bloodMoonHeldTime}。
     */
    public static final MinezClientMod.Version BLOOD_MOON_NO_HOLD_MOD_VERSION = new MinezClientMod.Version(1, 22, 2);
    /**
     * Java 版的降雨强度：{"strength": 0~1}，有变化或模组就绪时发出（模组 {@link #BLOOD_RAIN_MOD_VERSION} 起），模组画血雨时按它的疏密。
     */
    public static final String RAIN_EVENT = "Rain";
    /**
     * 是否显示左上角的坐标：{"show": 1 或 0}，与发给客户端的游戏规则 showcoordinates 相同（服务器设置中的“显示坐标”，且没有被
     * reduced debug info 或配置禁止，见 {@link org.geysermc.geyser.session.cache.PreferencesCache#updateShowCoordinates}），有变化或模组就绪时发出
     * （模组 {@link #COORDINATES_MOD_VERSION} 起）。模组接管聊天栏时把原版左上角的 chat_stack 整个隐藏，坐标在其中，一并被藏，由模组照原版画出。
     */
    public static final String COORDINATES_EVENT = "Coordinates";
    /**
     * 自己画左上角坐标的模组的最低版本，见 {@link #COORDINATES_EVENT}。
     */
    public static final MinezClientMod.Version COORDINATES_MOD_VERSION = new MinezClientMod.Version(1, 22, 13);
    /**
     * 画血雨、血色水下雾的模组的最低版本，见 {@link #bloodMoonState}。
     */
    public static final MinezClientMod.Version BLOOD_RAIN_MOD_VERSION = new MinezClientMod.Version(1, 22, 0);
    /**
     * 血月期间压入的雾（模组的资源包中 fogs/minez_blood_water.json），水下变成血色。
     */
    public static final String BLOOD_WATER_FOG = "minez:blood_water";
    /**
     * 附近各列的高度与降水（{@link BloodMoonClimate#chunk}），每个区块一条，回应模组的 {@link NeteasePyRpc#CLIMATE_QUERY_EVENT}。
     */
    public static final String CLIMATE_EVENT = "Climate";
    /**
     * 一次查询最多的区块数。
     */
    private static final int CLIMATE_QUERY_MAX_CHUNKS = 16;
    /**
     * 能复制到剪贴板的模组的最低版本。
     */
    public static final MinezClientMod.Version CLIPBOARD_MOD_VERSION = new MinezClientMod.Version(1, 8, 0);
    /**
     * 能显示动态图标的模组的最低版本。
     */
    public static final MinezClientMod.Version MIN_MOD_VERSION = new MinezClientMod.Version(1, 4, 0);
    private static final int MAX_CLIENT_LOGS = 100;
    private static final int MAX_CLIENT_LOG_LENGTH = 500;

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
    /**
     * 这个会话已写进日志的诊断信息的条数，见 {@link #clientLog}。
     */
    private int clientLogs;
    /**
     * 自己身上基岩版没有的 Java 版状态效果：{效果: [等级 - 1, 结束的刻（System.nanoTime 换算的毫秒，无限为 -1）, 是否环境效果]}。
     */
    private final Map<Effect, long[]> javaOnlyEffects = new ConcurrentHashMap<>();
    /**
     * 最近一次收到的血月时钟的状态，服务器没有这个时钟时为 null。
     */
    private volatile @Nullable ClockNetworkState bloodMoonClock;
    /**
     * 收到 {@link #bloodMoonClock} 的时刻（System.nanoTime）。
     */
    private volatile long bloodMoonClockReceived;
    /**
     * 模组是否接管了雨（不把下雨发给基岩版，由模组画出血雨），是否压入了血色水下雾，见 {@link #bloodMoonState}。
     */
    private volatile boolean bloodRain;
    private boolean bloodWater;

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
        // 血月正在停住的时段内进入世界时立即停住
        session.synchronizeTime();
        String username = session.getPlayerEntity().getUsername();
        if (username != null) {
            send(NAME_EVENT, Map.of("text", username));
        }
        if (session.isFlying()) {
            flyingChanged(true);
        }
        if (!javaOnlyEffects.isEmpty()) {
            sendJavaOnlyEffects();
        }
        sendBloodMoon();
        rainChanged();
        coordinatesChanged();
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
     * 收到模组发来的诊断信息（{@link NeteasePyRpc#CLIENT_LOG_EVENT}）：写进日志。每个会话至多 {@value #MAX_CLIENT_LOGS} 条，
     * 每条至多 {@value #MAX_CLIENT_LOG_LENGTH} 字、换行改为空格，免得被客户端用来刷屏。
     */
    public void clientLog(Map<String, Object> data) {
        if (!(data.get("text") instanceof String text) || clientLogs >= MAX_CLIENT_LOGS) {
            return;
        }
        clientLogs++;
        text = text.replaceAll("[\r\n]+", " ");
        if (text.length() > MAX_CLIENT_LOG_LENGTH) {
            text = text.substring(0, MAX_CLIENT_LOG_LENGTH) + "…";
        }
        session.getGeyser().getLogger().info("MINEZ client mod of " + session.bedrockUsername() + ": " + text);
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
     * 玩家开始或停止飞行后调用（见 {@link GeyserSession#setFlying}）。
     */
    public void flyingChanged(boolean flying) {
        MinezClientMod.Version loaded = version;
        if (ready && loaded != null && loaded.compareTo(FLYING_MOD_VERSION) >= 0) {
            send(FLYING_EVENT, Map.of("flying", flying ? 1 : 0));
        }
    }

    /**
     * 自己身上的状态效果有变化时调用（效果为 null 时表示全部清除，例如重生）。只处理基岩版没有的效果，见 {@link #EFFECTS_EVENT}。
     *
     * @param duration 剩余的刻数，-1 为无限；小于 -1 时表示移除这个效果
     */
    public void selfEffectChanged(@Nullable Effect effect, int amplifier, int duration, boolean ambient, boolean showIcon) {
        if (effect == null) {
            if (javaOnlyEffects.isEmpty()) {
                return;
            }
            javaOnlyEffects.clear();
        } else {
            if (EffectType.fromJavaEffect(effect).getBedrockId() != 0) {
                // 基岩版效果栏中的图标数变了，模组画的图标要随之挪动
                if (!javaOnlyEffects.isEmpty()) {
                    sendJavaOnlyEffects();
                }
                return;
            }
            if (duration < -1 || !showIcon) {
                if (javaOnlyEffects.remove(effect) == null) {
                    return;
                }
            } else {
                long ends = duration == -1 ? -1 : System.nanoTime() / 1_000_000L + duration * 50L;
                javaOnlyEffects.put(effect, new long[] {amplifier, ends, ambient ? 1 : 0});
            }
        }
        sendJavaOnlyEffects();
    }

    /**
     * 收到 Java 版的时钟更新时调用（见 JavaSetTimeTranslator），只处理血月时钟，见 {@link #BLOOD_MOON_EVENT}。
     *
     * @param updates {时钟的编号: 状态}，只含有变化的时钟（进服时为全部）。
     */
    public void clocksUpdated(Map<Integer, ClockNetworkState> updates) {
        OptionalInt id = session.javaRegistries().registry(JavaRegistries.WORLD_CLOCK).getId(BLOOD_MOON_CLOCK);
        if (id.isEmpty()) {
            return;
        }
        ClockNetworkState state = updates.get(id.getAsInt());
        if (state != null) {
            bloodMoonClockReceived = System.nanoTime();
            bloodMoonClock = state;
            sendBloodMoon();
            // 血月开关或对齐后立即按新的时钟决定是否停住（只有主世界的时钟变化时 Geyser 才会同步时间）
            session.synchronizeTime();
        }
    }

    /**
     * 血月期间基岩版的时间停在 {@link #BLOOD_MOON_HELD_TIME}（由 {@link GeyserSession#synchronizeTime} 发出）。基岩版的引擎夜里把雾
     * （地平线以下）、天空盒与云压到原来的约 6%（2026-11-23 测试服实测），网易的接口改不了，模组设的红色雾与天空盒红月几乎看不见；
     * 停在刚日落时它们仍然明亮，天空的颜色仍由模组设置，原版月亮由模组按真实时间转动，月相由 GeyserSession 保留真实的日数。代价是基岩版这段时间的地面比 Java 版亮。
     *
     * @return 要发给基岩版的时间；不在血月的这段时间、或模组版本不够时为空。
     */
    public OptionalInt bloodMoonHeldTime() {
        ClockNetworkState state = bloodMoonClock;
        MinezClientMod.Version loaded = version;
        if (state == null || !ready || loaded == null || loaded.compareTo(BLOOD_MOON_HOLD_MOD_VERSION) < 0
            || loaded.compareTo(BLOOD_MOON_NO_HOLD_MOD_VERSION) >= 0) {
            return OptionalInt.empty();
        }
        double elapsed = (System.nanoTime() - bloodMoonClockReceived) / 50_000_000.0;
        double ticks = state.totalTicks() + state.partialTick() + state.rate() * elapsed;
        double t = ticks - Math.floor(ticks / BLOOD_MOON_PERIOD) * BLOOD_MOON_PERIOD;
        return t >= BLOOD_MOON_HOLD_FROM && t < BLOOD_MOON_HOLD_UNTIL ? OptionalInt.of(BLOOD_MOON_HELD_TIME) : OptionalInt.empty();
    }

    /**
     * 收到模组发来的血月状态（{@link NeteasePyRpc#BLOOD_MOON_STATE_EVENT}，{"rain": 1 或 0, "water": 1 或 0}）：
     * rain 为 1 时不再把下雨发给基岩版（模组按 {@link #climateQuery} 逐列画出血雨与雪），water 为 1 时压入 {@link #BLOOD_WATER_FOG}。
     */
    public void bloodMoonState(Map<String, Object> data) {
        if (!ready) {
            return;
        }
        boolean rain = data.get("rain") instanceof Number number && number.intValue() != 0;
        boolean water = data.get("water") instanceof Number number && number.intValue() != 0;
        if (water != bloodWater) {
            bloodWater = water;
            if (water) {
                session.camera().sendFog(BLOOD_WATER_FOG);
            } else {
                session.camera().removeFog(BLOOD_WATER_FOG);
            }
        }
        if (rain != bloodRain) {
            bloodRain = rain;
            session.resendWeather();
        }
    }

    /**
     * 收到模组的降水查询（{@link NeteasePyRpc#CLIMATE_QUERY_EVENT}，{"chunks": [[区块 x, 区块 z], …], "y": 摄像机的方块高度}）：
     * 在会话的事件循环中逐个区块算出各列的高度与降水，以 {@link #CLIMATE_EVENT} 发回；不在缓存中的区块不回。
     */
    public void climateQuery(Map<String, Object> data) {
        if (!ready || !(data.get("chunks") instanceof List<?> chunks) || !(data.get("y") instanceof Number y)) {
            return;
        }
        List<int[]> positions = new ArrayList<>();
        for (Object chunk : chunks) {
            if (positions.size() >= CLIMATE_QUERY_MAX_CHUNKS) {
                break;
            }
            if (chunk instanceof List<?> pair && pair.size() == 2 && pair.get(0) instanceof Number x && pair.get(1) instanceof Number z) {
                positions.add(new int[] {x.intValue(), z.intValue()});
            }
        }
        int cameraY = y.intValue();
        session.ensureInEventLoop(() -> {
            for (int[] position : positions) {
                BloodMoonClimate.chunk(session, position[0], position[1], cameraY).ifPresent(result -> send(CLIMATE_EVENT, result));
            }
        });
    }

    /**
     * @return 是否由模组画出血雨，此时不把下雨发给基岩版（见 {@link GeyserSession#updateRain}）。
     */
    public boolean rainTakenOver() {
        return ready && bloodRain;
    }

    /**
     * Java 版的降雨强度变化时（{@link GeyserSession#updateRain}）与模组就绪时调用：告知模组，见 {@link #RAIN_EVENT}。
     */
    public void rainChanged() {
        MinezClientMod.Version loaded = version;
        if (ready && loaded != null && loaded.compareTo(BLOOD_RAIN_MOD_VERSION) >= 0) {
            send(RAIN_EVENT, Map.of("strength", session.getRainStrength()));
        }
    }

    /**
     * 是否显示坐标有变化时（{@link org.geysermc.geyser.session.cache.PreferencesCache#updateShowCoordinates}）与模组就绪时调用：告知模组，见 {@link #COORDINATES_EVENT}。
     */
    public void coordinatesChanged() {
        MinezClientMod.Version loaded = version;
        if (ready && loaded != null && loaded.compareTo(COORDINATES_MOD_VERSION) >= 0) {
            send(COORDINATES_EVENT, Map.of("show", session.getPreferencesCache().isShowingCoordinates() ? 1 : 0));
        }
    }

    private void sendBloodMoon() {
        ClockNetworkState state = bloodMoonClock;
        MinezClientMod.Version loaded = version;
        if (state == null || !ready || loaded == null || loaded.compareTo(BLOOD_MOON_MOD_VERSION) < 0) {
            return;
        }
        send(BLOOD_MOON_EVENT, Map.of("ticks", state.totalTicks(), "partial", state.partialTick(), "rate", state.rate()));
    }

    private void sendJavaOnlyEffects() {
        MinezClientMod.Version loaded = version;
        if (!ready || loaded == null || loaded.compareTo(EFFECTS_MOD_VERSION) < 0) {
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        List<Map<String, Object>> effects = new ArrayList<>();
        for (Map.Entry<Effect, long[]> entry : javaOnlyEffects.entrySet()) {
            long[] value = entry.getValue();
            int duration = value[1] == -1 ? -1 : (int) Math.max(0, (value[1] - now) / 50L);
            if (duration == 0) {
                continue;
            }
            effects.add(Map.of("id", entry.getKey().name().toLowerCase(Locale.ROOT), "amplifier", (int) value[0],
                "duration", duration, "ambient", (int) value[2]));
        }
        int shown = 0;
        for (Effect effect : session.getEffectCache().getEntityEffects()) {
            if (EffectType.fromJavaEffect(effect).getBedrockId() != 0) {
                shown++;
            }
        }
        send(EFFECTS_EVENT, Map.of("effects", effects, "shown", shown));
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
