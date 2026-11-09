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

import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.populator.NeteasePalette;
import org.geysermc.geyser.registry.type.ItemMappings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * MINEZ：核对网易客户端实际的物品表。
 * <p>
 * 网易中国版的物品表由国际版推算（见 {@link org.geysermc.geyser.registry.populator.NeteasePalette}），而网易客户端还有自己的物品，
 * 不在推算的表中；自定义物品用到它们的编号时会显示成网易的物品。MINEZ 客户端模组在 {@code ClientConfig} 的 {@code item_table}
 * 中收到要读的编号范围与一个用来自检的原版物品，用隐藏的物品图标控件按编号读出客户端认为的物品名，以 {@code ItemTable} 事件分批发回：
 * {@code {"part": 序号, "parts": 总批数, "items": [[编号, 物品名], ...]}}；读不出时为 {@code {"unsupported": 说明}}。
 * <p>
 * 收齐后与这个会话下发的物品表以及网易客户端自己的物品（{@link NeteasePalette#neteaseItems()}）逐个编号比对，有问题的写进日志
 * （每次启动内，同样的结果只记一次），完整的结果写进 Geyser 的配置目录中的 {@value #FILE_NAME}。下列情况不算问题：两边的名字相同；
 * 客户端读出的是另一个名字、而这个名字也在下发的表中（旧物品的别名，例如 {@code minecraft:wool} 读出为 {@code minecraft:white_wool}）；
 * 下发的表中没有、而客户端读出的正是记下的网易物品。数据来自客户端，只用于日志与这个文件，不影响下发的物品表；数量与长度都有上限。
 */
final class MinezItemTableCheck {
    /**
     * 要读的编号范围。网易客户端自己的物品在原版物品之后，范围取到明显大于自定义物品的编号。
     */
    static final int FIRST_ID = 0;
    static final int LAST_ID = 6000;
    /**
     * 用来自检读法的原版物品：模组先读这个物品的编号，读回的名字不对时说明这种读法在客户端上不可用。
     */
    static final String CHECK_ITEM = "minecraft:diamond";
    static final String FILE_NAME = "netease_item_table.json";
    private static final int MAX_PARTS = 64;
    private static final int MAX_ITEMS = LAST_ID - FIRST_ID + 1;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_LOGGED = 200;
    private static volatile String lastReport;

    private final GeyserSession session;
    private final Int2ObjectMap<String> table = new Int2ObjectOpenHashMap<>();
    private final boolean[] received = new boolean[MAX_PARTS];
    private int parts = -1;
    private int receivedParts;
    private boolean done;

    MinezItemTableCheck(GeyserSession session) {
        this.session = session;
    }

    /**
     * @return 随 ClientConfig 发给模组的请求；下发的物品表中没有用来自检的物品时为 null，不核对。
     */
    @Nullable Map<String, Object> request() {
        ItemDefinition check = session.getItemMappings().getDefinition(CHECK_ITEM);
        if (check == null) {
            return null;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("from", FIRST_ID);
        request.put("to", LAST_ID);
        request.put("check", List.of(check.getRuntimeId(), CHECK_ITEM));
        return request;
    }

    /**
     * 收到模组发来的一批。
     */
    void receive(Map<String, Object> data) {
        if (done) {
            return;
        }
        Object unsupported = data.get("unsupported");
        if (unsupported != null) {
            done = true;
            report("MINEZ's client mod of " + session.bedrockUsername() + " could not read the China Edition client's item table: "
                + truncate(String.valueOf(unsupported)), true);
            return;
        }
        if (!(data.get("part") instanceof Number partNumber) || !(data.get("parts") instanceof Number partsNumber)
            || !(data.get("items") instanceof List<?> items)) {
            return;
        }
        int part = partNumber.intValue();
        int total = partsNumber.intValue();
        if (total < 1 || total > MAX_PARTS || part < 0 || part >= total || (parts != -1 && total != parts) || received[part]) {
            return;
        }
        for (Object entry : items) {
            if (!(entry instanceof List<?> pair) || pair.size() != 2 || !(pair.get(0) instanceof Number id)
                || !(pair.get(1) instanceof String name) || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                continue;
            }
            int value = id.intValue();
            if (value >= FIRST_ID && value <= LAST_ID && table.size() < MAX_ITEMS) {
                table.put(value, name);
            }
        }
        parts = total;
        received[part] = true;
        if (++receivedParts == parts) {
            done = true;
            finish();
        }
    }

    private void finish() {
        ItemMappings mappings = session.getItemMappings();
        Int2ObjectMap<String> server = new Int2ObjectOpenHashMap<>();
        mappings.getItemDefinitions().forEach((id, definition) -> server.put((int) id, definition.getIdentifier()));
        List<String> differences = differences(table, server, NeteasePalette.neteaseItems());
        StringBuilder message = new StringBuilder();
        if (differences.isEmpty()) {
            message.append("The China Edition client's item table matches Geyser's (").append(table.size())
                .append(" items, IDs ").append(FIRST_ID).append(" to ").append(LAST_ID).append(", ")
                .append(NeteasePalette.neteaseItems().size()).append(" of them China Edition's own)");
        } else {
            message.append("The China Edition client's item table differs from Geyser's at ").append(differences.size())
                .append(" IDs (client / Geyser), see ").append(FILE_NAME).append(" in Geyser's folder:");
            differences.stream().limit(MAX_LOGGED).forEach(line -> message.append("\n  ").append(line));
            if (differences.size() > MAX_LOGGED) {
                message.append("\n  ...");
            }
        }
        report(message.toString(), !differences.isEmpty());
        write(differences);
    }

    /**
     * @param client  客户端读出的物品表：编号 → 名字。
     * @param server  这个会话下发的物品表：编号 → 名字。
     * @param netease 记下的网易客户端自己的物品：编号 → 名字。
     * @return 有问题的编号，每项为“编号: 客户端 / Geyser (原因)”，按编号排列；客户端或 Geyser 没有的写作“-”。原因为：
     * {@code conflict}（下发的物品被网易的物品占了编号）、{@code unlisted China Edition item}（网易的物品不在
     * {@code netease_items} 中）、{@code missing on client}（客户端没有下发的物品）、{@code China Edition item not found}
     * （记下的网易物品客户端已没有）。
     */
    static List<String> differences(Int2ObjectMap<String> client, Int2ObjectMap<String> server, Int2ObjectMap<String> netease) {
        Set<String> serverNames = new HashSet<>(server.values());
        List<String> differences = new ArrayList<>();
        for (int id = FIRST_ID; id <= LAST_ID; id++) {
            String ours = server.get(id);
            String theirs = client.get(id);
            String reason = null;
            if (theirs == null) {
                if (ours != null) {
                    reason = "missing on client";
                } else if (netease.containsKey(id)) {
                    reason = "China Edition item not found";
                }
            } else if (ours == null) {
                if (!theirs.equals(netease.get(id))) {
                    reason = "unlisted China Edition item";
                }
            } else if (!theirs.equals(ours) && !serverNames.contains(theirs)) {
                reason = "conflict";
            }
            if (reason != null) {
                differences.add(id + ": " + (theirs == null ? "-" : theirs) + " / " + (ours == null ? "-" : ours) + " (" + reason + ")");
            }
        }
        return differences;
    }

    private void report(String message, boolean warning) {
        if (message.equals(lastReport)) {
            return;
        }
        lastReport = message;
        if (warning) {
            GeyserImpl.getInstance().getLogger().warning(message);
        } else {
            GeyserImpl.getInstance().getLogger().info(message);
        }
    }

    private void write(List<String> differences) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("protocol", session.protocolVersion());
        json.put("from", FIRST_ID);
        json.put("to", LAST_ID);
        json.put("differences", differences);
        Map<String, String> items = new TreeMap<>((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
        table.forEach((id, name) -> items.put(Integer.toString(id), name));
        json.put("client", items);
        Path file = GeyserImpl.getInstance().configDirectory().resolve(FILE_NAME);
        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().error("Could not write " + file, e);
        }
    }

    private static String truncate(String text) {
        return text.length() > MAX_NAME_LENGTH ? text.substring(0, MAX_NAME_LENGTH) + "..." : text;
    }
}
