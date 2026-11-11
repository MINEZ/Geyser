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

package org.geysermc.geyser.translator.protocol.java;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import lombok.Getter;
import lombok.ToString;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.java.ServerDefineCommandsEvent;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.registry.java.BuiltInJavaRegistries;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.NeteaseProfile;
import org.geysermc.geyser.registry.java.JavaRegistries;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.geyser.util.MinezCommandNames;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.ResourceProperties;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("removal") // We know. This is our doing.
@Translator(packet = ClientboundCommandsPacket.class)
public class JavaCommandsTranslator extends PacketTranslator<ClientboundCommandsPacket> {

    /**
     * Wait until the registries load before getting all the block names.
     */
    private static final Supplier<String[]> ALL_BLOCK_NAMES = Suppliers.memoize(() -> BuiltInJavaRegistries.BLOCK.keys().stream().map(Key::asString).toArray(String[]::new));
    private static final String[] ALL_EFFECT_IDENTIFIERS = EntityUtils.getAllEffectIdentifiers();
    private static final String[] ATTRIBUTES = AttributeType.Builtin.BUILTIN.values().stream().map(type -> type.getIdentifier().asString()).toList().toArray(new String[0]);
    private static final String[] ENUM_BOOLEAN = {"true", "false"};
    private static final String[] VALID_COLORS;
    private static final String[] VALID_SCOREBOARD_SLOTS;

    private static final Hash.Strategy<BedrockCommandInfo> PARAM_STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(BedrockCommandInfo o) {
            int paramHash = Arrays.deepHashCode(o.paramData());
            if ("help".equals(o.name())) {
                paramHash = 31 * paramHash + 1;
            }
            return 31 * paramHash + o.description().hashCode();
        }

        @Override
        public boolean equals(BedrockCommandInfo a, BedrockCommandInfo b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if ("help".equals(a.name()) != "help".equals(b.name())) {
                // Merging this causes Bedrock to fallback to its own help command
                // Tested on Paper 1.20.4 with Essentials and Bedrock 1.21
                // https://github.com/GeyserMC/Geyser/issues/2573
                // MINEZ：须对称判断。fastutil 以 equals(新键, 已有键) 查找，只拦一边时，后插入的命令仍可能并进 help，
                // help 就会同时作为别名与另补的假 help 下发，开发版客户端报“Duplicate command registration for help”。
                return false;
            }
            if (!a.description().equals(b.description())) return false;
            if (a.paramData().length != b.paramData().length) return false;
            for (int i = 0; i < a.paramData().length; i++) {
                CommandParamData[] a1 = a.paramData()[i].getOverloads();
                CommandParamData[] b1 = b.paramData()[i].getOverloads();
                if (a1.length != b1.length) return false;

                for (int j = 0; j < a1.length; j++) {
                    if (!a1[j].equals(b1[j])) return false;
                }
            }
            return true;
        }
    };

    static {
        VALID_COLORS = NamedTextColor.NAMES.keys().toArray(new String[0]);

        List<String> teamOptions = new ArrayList<>(Arrays.asList("list", "sidebar", "belowName"));
        for (String color : NamedTextColor.NAMES.keys()) {
            teamOptions.add("sidebar.team." + color);
        }
        VALID_SCOREBOARD_SLOTS = teamOptions.toArray(new String[0]);
    }

    @Override
    public void translate(GeyserSession session, ClientboundCommandsPacket packet) {
        // Don't send command suggestions if they are disabled
        if (!session.getGeyser().config().gameplay().commandSuggestions()) {
            session.getGeyser().getLogger().debug("Not sending translated command suggestions as they are disabled.");

            // Send a mostly empty packet so Bedrock doesn't override /help with its own, built-in help command.
            AvailableCommandsPacket emptyPacket = new AvailableCommandsPacket();
            emptyPacket.getCommands().add(createFakeHelpCommand());
            session.sendUpstreamPacket(emptyPacket);
            return;
        }

        CommandRegistry registry = session.getGeyser().commandRegistry();
        CommandNode[] nodes = packet.getNodes();
        List<CommandData> commandData = new ArrayList<>();
        IntSet commandNodes = new IntOpenHashSet();
        Set<String> knownAliases = new HashSet<>();
        Map<BedrockCommandInfo, Set<String>> commands = new Object2ObjectOpenCustomHashMap<>(PARAM_STRATEGY);
        Int2ObjectMap<List<CommandNode>> commandArgs = new Int2ObjectOpenHashMap<>();

        // Get the first node, it should be a root node
        CommandNode rootNode = nodes[packet.getFirstNodeIndex()];

        List<String> knownCommands = new ArrayList<>();
        List<String> restrictedCommands = new ArrayList<>();
        // MINEZ：Java 版的全部命令名，用来决定名字以“/”开头的命令在基岩版中的写法（见 MinezCommandNames）。
        Set<String> javaNames = new HashSet<>();
        for (int nodeIndex : rootNode.getChildIndices()) {
            javaNames.add(nodes[nodeIndex].getName().toLowerCase(Locale.ROOT));
        }
        // Loop through the root nodes to get all commands
        for (int nodeIndex : rootNode.getChildIndices()) {
            CommandNode node = nodes[nodeIndex];

            // Make sure we don't have duplicated commands (happens if there is more than 1 root node)
            if (!commandNodes.add(nodeIndex) || !knownAliases.add(node.getName().toLowerCase(Locale.ROOT))) continue;

            // Get and update the commandArgs list with the found arguments
            if (node.getChildIndices().length >= 1) {
                for (int childIndex : node.getChildIndices()) {
                    commandArgs.computeIfAbsent(nodeIndex, ($) -> new ArrayList<>()).add(nodes[childIndex]);
                }
            }

            // Get and parse all params
            CommandOverloadData[] params = getParams(session, nodes[nodeIndex], nodes);

            // Insert the alias name into the command list
            String name = node.getName().toLowerCase(Locale.ROOT);
            String description = registry.description(name, session.locale());
            // MINEZ：基岩版客户端不发出以“//”开头的命令，名字以“/”开头的命令在基岩版中写作“_”开头（见 MinezCommandNames）。
            String bedrockName = MinezCommandNames.toBedrock(name, javaNames);
            BedrockCommandInfo info = new BedrockCommandInfo(bedrockName, description, params);
            commands.computeIfAbsent(info, $ -> new HashSet<>()).add(bedrockName);

            // Add the command to the command lists
            knownCommands.add(name);
            if (node.isAllowsRestricted()) { // Name is a bit confusing - this is what we want
                restrictedCommands.add(name);
            }
        }

        session.setKnownCommands(knownCommands);
        session.setRestrictedCommands(restrictedCommands);

        if (!sendsCommands(session)) {
            // MINEZ：开发版客户端不发命令表（见 sendsCommands）；命令照常可以输入执行，只是没有补全。
            session.getGeyser().getLogger().debug("Not sending commands to a China Edition development client");
            return;
        }

        var eventBus = session.getGeyser().eventBus();

        var event = new ServerDefineCommandsEvent(session, commands.keySet());
        eventBus.fire(event);
        if (event.isCancelled()) {
            return;
        }

        var oldEvent = new org.geysermc.geyser.api.event.downstream.ServerDefineCommandsEvent(session, commands.keySet());
        eventBus.fire(oldEvent);
        if (oldEvent.isCancelled()) {
            return;
        }

        // The command flags, set to NOT_CHEAT so known commands can be used while achievements are enabled.
        Set<CommandData.Flag> flags = Set.of(CommandData.Flag.NOT_CHEAT);

        boolean helpAdded = false;

        // Loop through all the found commands
        for (Map.Entry<BedrockCommandInfo, Set<String>> entry : commands.entrySet()) {
            String commandName = entry.getValue().iterator().next(); // We know this has a value

            LinkedHashMap<String, Set<CommandEnumConstraint>> values = new LinkedHashMap<>();
            // Is this right?
            for (String s : entry.getValue()) {
                values.put(s, EnumSet.of(CommandEnumConstraint.ALLOW_ALIASES));
            }

            // Create a basic alias
            CommandEnumData aliases = new CommandEnumData(commandName + "Aliases", values, false);

            // Fetch command description
            String description = entry.getKey().description();

            // Command suggestion list is illegible if a command description contains line breaks
            int lineBreak = description.indexOf('\n');
            if (lineBreak >= 0) {
                description = description.substring(0, lineBreak);
            }

            // Since 1.21.130, the maximum description length is 1000 characters
            // https://www.minecraft.net/en-us/article/minecraft-1-21-130-bedrock-changelog
            // As issues are still experienced at that length, truncate descriptions at 950 characters
            if (description.length() > 950) {
                description = description.substring(0, 947) + "...";
            }

            // Build the completed command and add it to the final list
            CommandData data = new CommandData(commandName, description, flags, CommandPermission.ANY, aliases, Collections.emptyList(), entry.getKey().paramData());
            commandData.add(data);

            if (entry.getValue().contains("help")) {
                helpAdded = true;
            }
        }

        if (!helpAdded) {
            // https://github.com/GeyserMC/Geyser/issues/2573 if Brigadier does not send the help command.
            commandData.add(createFakeHelpCommand());
        }

        // MINEZ：按名字排序，同样的命令每次得到同样的包（枚举改名的结果也相同），以便认出重复的命令表。
        commandData.sort(Comparator.comparing(CommandData::getName));
        assignUniqueEnumNames(commandData);

        if (session.getGeyser().platformType() == PlatformType.STANDALONE) {
            session.getGeyser().commandRegistry().export(session, commandData, knownAliases);
        }

        if (commandData.equals(session.getSentCommands())) {
            // MINEZ：与上次发出的完全相同（Java 服务器常在进服时连发两次），不再重发，见 GeyserSession#getSentCommands。
            session.getGeyser().getLogger().debug("Skipping a command packet identical to the last one");
            return;
        }
        session.setSentCommands(commandData);
        if (GameProtocol.isNeteaseCodec(session.getUpstream().getSession().getCodec())) {
            // MINEZ：网易客户端把新的命令表并进已有的命令，只发它还没有的命令，见 GeyserSession#getSentCommandNames。
            commandData = newCommandsOnly(commandData, session.getSentCommandNames(), session.getSentEnumNames());
            if (commandData.isEmpty()) {
                session.getGeyser().getLogger().debug("No new commands to send to the China Edition client");
                return;
            }
        }

        // Add our commands to the AvailableCommandsPacket for the bedrock client
        AvailableCommandsPacket availableCommandsPacket = new AvailableCommandsPacket();
        availableCommandsPacket.getCommands().addAll(commandData);

        session.getGeyser().getLogger().debug("Sending command packet of " + commandData.size() + " commands");

        // Finally, send the commands to the client
        session.sendUpstreamPacket(availableCommandsPacket);
    }

    /**
     * Build the command parameter array for the given command
     *
     * @param session the session
     * @param commandNode The command to build the parameters for
     * @param allNodes    Every command node
     * @return An array of parameter option arrays
     */
    private static CommandOverloadData[] getParams(GeyserSession session, CommandNode commandNode, CommandNode[] allNodes) {
        // Check if the command is an alias and redirect it
        if (commandNode.getRedirectIndex().isPresent()) {
            int redirectIndex = commandNode.getRedirectIndex().getAsInt();
            GeyserImpl.getInstance().getLogger().debug("Redirecting command " + commandNode.getName() + " to " + allNodes[redirectIndex].getName());
            commandNode = allNodes[redirectIndex];
        }

        if (commandNode.getChildIndices().length >= 1) {
            // Create the root param node and build all the children
            ParamInfo rootParam = new ParamInfo(commandNode, null);
            rootParam.buildChildren(new CommandBuilderContext(session), allNodes);

            List<CommandOverloadData> treeData = rootParam.getTree();

            return treeData.toArray(new CommandOverloadData[0]);
        }

        return new CommandOverloadData[0];
    }

    /**
     * Convert Java edition command types to Bedrock edition
     *
     * @param context the session's command context
     * @param node Command type to convert
     * @return Bedrock parameter data type
     */
    private static Object mapCommandType(CommandBuilderContext context, CommandNode node) {
        CommandParser parser = node.getParser();
        if (parser == null) {
            return CommandParam.STRING;
        }

        return switch (parser) {
            case FLOAT, ROTATION, DOUBLE -> CommandParam.FLOAT;
            case INTEGER, LONG -> CommandParam.INT;
            case ENTITY, GAME_PROFILE -> CommandParam.TARGET;
            case BLOCK_POS -> CommandParam.BLOCK_POSITION;
            case COLUMN_POS, VEC3 -> CommandParam.POSITION;
            case MESSAGE -> CommandParam.MESSAGE;
            case NBT_COMPOUND_TAG, NBT_TAG, NBT_PATH -> CommandParam.JSON; //TODO NBT was removed
            case RESOURCE_LOCATION, FUNCTION -> CommandParam.FILE_PATH;
            case BOOL -> ENUM_BOOLEAN;
            case OPERATION -> CommandParam.OPERATOR; // ">=", "==", etc
            case BLOCK_STATE -> ALL_BLOCK_NAMES.get();
            case ITEM_STACK -> context.getItemNames();
            case TEAM_COLOR -> VALID_COLORS;
            case SCOREBOARD_SLOT -> VALID_SCOREBOARD_SLOTS;
            case RESOURCE -> handleResource(context, ((ResourceProperties) node.getProperties()).getRegistryKey(), false);
            case RESOURCE_OR_TAG -> handleResource(context, ((ResourceProperties) node.getProperties()).getRegistryKey(), true);
            case DIMENSION -> context.session.getLevels();
            case TEAM -> context.getTeams(); // Note: as of Java 1.19.3, objectives are currently parsed from the server
            default -> CommandParam.STRING;
        };
    }

    private static Object handleResource(CommandBuilderContext context, Key resource, boolean tags) {
        return switch (resource.asString()) {
            case "minecraft:attribute" -> ATTRIBUTES;
            case "minecraft:enchantment" -> context.getEnchantments();
            case "minecraft:entity_type" -> context.getEntityTypes();
            case "minecraft:mob_effect" -> ALL_EFFECT_IDENTIFIERS;
            case "minecraft:worldgen/biome" -> tags ? context.getBiomesWithTags() : context.getBiomes();
            default -> CommandParam.STRING;
        };
    }

    /**
     * MINEZ：是否给这个客户端发命令表（AvailableCommandsPacket）。
     * <p>
     * 网易的开发版客户端自带一些命令（实测有 help 与 test，Java 版给 OP 的命令中也有 /test），服务器发来同名的命令时弹出
     * “Duplicate command registration”“Commands added interleaved”“Found 1 duplicate entries in enum values”三个断言；客户端自带
     * 哪些命令无从得知。开发版客户端（Mod PC 等）登录在网易的测试环境（env 不是 obt）或为自签名登录，因此不给它们发命令表：命令照常
     * 可以输入执行（客户端把输入的命令发给服务器），只是没有补全；/help 由客户端自己处理。正式环境的网易客户端与国际版客户端照旧。
     */
    static boolean sendsCommands(GeyserSession session) {
        NeteaseProfile profile = session.getNeteaseProfile();
        return profile == null || (!profile.developer() && "obt".equals(profile.env()));
    }

    /**
     * MINEZ：让同一个包中名字相同的硬枚举取值也相同。
     * <p>
     * 字面量枚举以其中第一个字面量命名，不同命令、同一命令的不同分支都可能产生同名但取值不同的枚举，
     * 例如 {@code tag … add|remove} 的 {@code add} 与 {@code team add} 的 {@code add}。协议库只合并名字与取值都相同的枚举，
     * 两个都会发出去；基岩版客户端以名字识别枚举，把后一个的取值并入前一个，补全中会混入别的命令的选项，
     * 开发版客户端还会报“Found 1 duplicate entries in enum values”。
     * <p>
     * 这里保留最先出现的，与它取值相同的复用同一个对象，取值不同的依次改名为 {@code name_2}、{@code name_3}…。
     * 命令的别名枚举不能改名，先登记；软枚举由客户端按名字增删取值，不处理。
     *
     * @param commands 将要下发的命令
     */
    static void assignUniqueEnumNames(List<CommandData> commands) {
        Map<String, CommandEnumData> byName = new HashMap<>();
        for (CommandData command : commands) {
            CommandEnumData aliases = command.getAliases();
            if (aliases != null) {
                byName.putIfAbsent(aliases.getName(), aliases);
            }
        }

        // 同一个 CommandParamData 可能被多个重载共用，按枚举对象记下结果，保证处处一致
        Map<CommandEnumData, CommandEnumData> replaced = new IdentityHashMap<>();
        for (CommandData command : commands) {
            for (CommandOverloadData overload : command.getOverloads()) {
                for (CommandParamData param : overload.getOverloads()) {
                    CommandEnumData enumData = param.getEnumData();
                    if (enumData == null || enumData.isSoft()) {
                        continue;
                    }
                    CommandEnumData unique = replaced.computeIfAbsent(enumData, original -> {
                        CommandEnumData existing = byName.putIfAbsent(original.getName(), original);
                        if (existing == null) {
                            return original;
                        }
                        if (existing.equals(original)) {
                            return existing;
                        }
                        for (int suffix = 2; ; suffix++) {
                            CommandEnumData renamed = new CommandEnumData(original.getName() + "_" + suffix, original.getValues(), false);
                            CommandEnumData taken = byName.putIfAbsent(renamed.getName(), renamed);
                            if (taken == null) {
                                return renamed;
                            }
                            if (taken.equals(renamed)) {
                                return taken;
                            }
                        }
                    });
                    if (unique != enumData) {
                        param.setEnumData(unique);
                    }
                }
            }
        }
    }

    /**
     * MINEZ：只留下客户端还没有的命令（按名字），其中的硬枚举（包括别名枚举）若与已发过的同名，依次改名为 {@code name_2}、
     * {@code name_3}…，并记下这次发出的命令名与枚举名。客户端把命令表并进已有的命令时，就不会有重复的命令与枚举。
     * 命令的参数改变时（例如换到另一个服务器），客户端仍是第一次发去的样子，与客户端自己合并时的结果相同。
     *
     * @param commands     这次的命令（已按名字排序、同一包中的枚举名已唯一，见 {@link #assignUniqueEnumNames}）
     * @param sentCommands 已发过的命令名，会加上这次发出的
     * @param sentEnums    已发过的硬枚举名，会加上这次发出的
     * @return 要发出的命令
     */
    static List<CommandData> newCommandsOnly(List<CommandData> commands, Set<String> sentCommands, Set<String> sentEnums) {
        List<CommandData> fresh = new ArrayList<>();
        for (CommandData command : commands) {
            if (!sentCommands.contains(command.getName())) {
                fresh.add(command);
            }
        }
        // 新名字既不能与已发过的重名，也不能与这次的其他枚举重名。
        Set<String> used = new HashSet<>(sentEnums);
        for (CommandData command : fresh) {
            if (command.getAliases() != null) {
                used.add(command.getAliases().getName());
            }
            for (CommandOverloadData overload : command.getOverloads()) {
                for (CommandParamData param : overload.getOverloads()) {
                    if (param.getEnumData() != null && !param.getEnumData().isSoft()) {
                        used.add(param.getEnumData().getName());
                    }
                }
            }
        }
        Map<CommandEnumData, CommandEnumData> renamed = new IdentityHashMap<>();
        java.util.function.Function<CommandEnumData, CommandEnumData> unique = original -> renamed.computeIfAbsent(original, e -> {
            if (!sentEnums.contains(e.getName())) {
                return e;
            }
            for (int suffix = 2; ; suffix++) {
                String name = e.getName() + "_" + suffix;
                if (used.add(name)) {
                    return new CommandEnumData(name, e.getValues(), e.isSoft());
                }
            }
        });
        List<CommandData> result = new ArrayList<>();
        for (CommandData command : fresh) {
            for (CommandOverloadData overload : command.getOverloads()) {
                for (CommandParamData param : overload.getOverloads()) {
                    CommandEnumData enumData = param.getEnumData();
                    if (enumData != null && !enumData.isSoft()) {
                        param.setEnumData(unique.apply(enumData));
                    }
                }
            }
            CommandEnumData aliases = command.getAliases();
            CommandEnumData newAliases = aliases == null ? null : unique.apply(aliases);
            if (newAliases != aliases) {
                command = new CommandData(command.getName(), command.getDescription(), command.getFlags(), command.getPermission(),
                    newAliases, command.getSubcommands(), command.getOverloads());
            }
            result.add(command);
        }
        for (CommandData command : result) {
            sentCommands.add(command.getName());
        }
        sentEnums.addAll(used);
        return result;
    }

    private CommandData createFakeHelpCommand() {
        CommandEnumData aliases = new CommandEnumData("helpAliases", Map.of("help", EnumSet.of(CommandEnumConstraint.ALLOW_ALIASES)), false);
        return new CommandData("help", "", Set.of(CommandData.Flag.NOT_CHEAT), CommandPermission.ANY, aliases, Collections.emptyList(), new CommandOverloadData[0]);
    }

    /**
     * Stores the command description and parameter data for best optimizing the Bedrock commands packet.
     */
    private record BedrockCommandInfo(String name, String description, CommandOverloadData[] paramData) implements
            org.geysermc.geyser.api.event.downstream.ServerDefineCommandsEvent.CommandInfo,
            ServerDefineCommandsEvent.CommandInfo
    {
    }

    /**
     * Stores command completions so we don't have to rebuild the same values multiple times.
     */
    @MonotonicNonNull
    private static class CommandBuilderContext {
        private final GeyserSession session;
        private Object biomesWithTags;
        private Object biomesNoTags;
        private String[] enchantments;
        private String[] entityTypes;
        private String[] itemNames;
        private CommandEnumData teams;

        CommandBuilderContext(GeyserSession session) {
            this.session = session;
        }

        private Object getBiomes() {
            if (biomesNoTags != null) {
                return biomesNoTags;
            }

            String[] identifiers = session.getGeyser().getWorldManager().getBiomeIdentifiers(false);
            return (biomesNoTags = identifiers != null ? identifiers : CommandParam.STRING);
        }

        private Object getBiomesWithTags() {
            if (biomesWithTags != null) {
                return biomesWithTags;
            }

            String[] identifiers = session.getGeyser().getWorldManager().getBiomeIdentifiers(true);
            return (biomesWithTags = identifiers != null ? identifiers : CommandParam.STRING);
        }

        private String[] getEnchantments() {
            if (enchantments != null) {
                return enchantments;
            }
            return (enchantments = session.javaRegistries().registry(JavaRegistries.ENCHANTMENT).keys().stream().map(Key::asString).toArray(String[]::new));
        }

        private String[] getEntityTypes() {
            if (entityTypes != null) {
                return entityTypes;
            }
            return (entityTypes = BuiltInJavaRegistries.ENTITY_TYPE.keys().stream().map(Key::asString).toArray(String[]::new));
        }

        public String[] getItemNames() {
            if (itemNames != null) {
                return itemNames;
            }
            return (itemNames = BuiltInJavaRegistries.ITEM.keys().stream().map(Key::asString).toArray(String[]::new));
        }

        private CommandEnumData getTeams() {
            if (teams != null) {
                return teams;
            }
            return (teams = new CommandEnumData("Geyser_Teams",
                    session.getWorldCache().getScoreboard().getTeamNames(), true
            ));
        }
    }

    @Getter
    @ToString
    private static class ParamInfo {
        private final CommandNode paramNode;
        private final CommandParamData paramData;
        private final List<ParamInfo> children;

        /**
         * Create a new parameter info object
         *
         * @param paramNode CommandNode the parameter is for
         * @param paramData The existing parameters for the command
         */
        public ParamInfo(CommandNode paramNode, CommandParamData paramData) {
            this.paramNode = paramNode;
            this.paramData = paramData;
            this.children = new ArrayList<>();
        }

        /**
         * Build the array of all the child parameters (recursive)
         *
         * @param context the session's command builder context
         * @param allNodes Every command node
         */
        public void buildChildren(CommandBuilderContext context, CommandNode[] allNodes) {
            for (int paramID : paramNode.getChildIndices()) {
                CommandNode paramNode = allNodes[paramID];

                if (paramNode == this.paramNode) {
                    // Fixes a StackOverflowError when an argument has itself as a child
                    continue;
                }

                if (paramNode.getParser() == null) {
                    boolean foundCompatible = false;
                    for (int i = 0; i < children.size(); i++) {
                        ParamInfo enumParamInfo = children.get(i);
                        // Check to make sure all descending nodes of this command are compatible - otherwise, create a new overload
                        if (isCompatible(allNodes, enumParamInfo.getParamNode(), paramNode)) {
                            foundCompatible = true;
                            // TODO: Check this
                            // Extend the current list of enum values
                            // String[] enumOptions = Arrays.copyOf(enumParamInfo.getParamData().getEnumData().getValues(), enumParamInfo.getParamData().getEnumData().getValues().size() + 1);
                            // enumOptions[enumOptions.length - 1] = paramNode.getName();

                            Map<String, Set<CommandEnumConstraint>> values = new LinkedHashMap<>(enumParamInfo.getParamData().getEnumData().getValues());
                            values.put(paramNode.getName(), Set.of());

                            // Re-create the command using the updated values
                            CommandEnumData enumData = new CommandEnumData(enumParamInfo.getParamData().getEnumData().getName(), values, false);
                            CommandParamData commandParamData = new CommandParamData();
                            commandParamData.setName(enumParamInfo.getParamData().getName());
                            commandParamData.setOptional(this.paramNode.isExecutable());
                            commandParamData.setEnumData(enumData);

                            children.set(i, new ParamInfo(enumParamInfo.getParamNode(), commandParamData));
                            break;
                        }
                    }

                    if (!foundCompatible) {
                        // Create a new subcommand with this exact type
                        LinkedHashMap<String, Set<CommandEnumConstraint>> map = new LinkedHashMap<>();
                        map.put(paramNode.getName(), Set.of());
                        CommandEnumData enumData = new CommandEnumData(paramNode.getName(), map, false);

                        // On setting optional:
                        // isExecutable is defined as a node "constitutes a valid command."
                        // Therefore, any children of the parameter must simply be optional.
                        CommandParamData commandParamData = new CommandParamData();
                        commandParamData.setName(paramNode.getName());
                        commandParamData.setOptional(this.paramNode.isExecutable());
                        commandParamData.setEnumData(enumData);

                        children.add(new ParamInfo(paramNode, commandParamData));
                    }
                } else {
                    // Put the non-enum param into the list
                    Object mappedType = mapCommandType(context, paramNode);
                    CommandEnumData enumData = null;
                    CommandParam type = null;
                    boolean optional = this.paramNode.isExecutable();
                    if (mappedType instanceof CommandEnumData) {
                        // Likely to specify isSoft, to be possibly updated later.
                        enumData = (CommandEnumData) mappedType;
                    } else if (mappedType instanceof String[]) {
                        LinkedHashMap<String, Set<CommandEnumConstraint>> map = new LinkedHashMap<>();
                        for (String s : (String[]) mappedType) {
                            map.put(s, Set.of());
                        }

                        enumData = new CommandEnumData(getEnumDataName(paramNode).toLowerCase(Locale.ROOT), map, false);
                    } else {
                        type = (CommandParam) mappedType;
                        // Bedrock throws a fit if an optional message comes after a string or target
                        // Example vanilla commands: ban-ip, ban, and kick
                        if (optional && type == CommandParam.MESSAGE && paramData != null && (paramData.getType() == CommandParam.STRING || paramData.getType() == CommandParam.TARGET)) {
                            optional = false;
                        }
                    }
                    // IF enumData != null:
                    // In game, this will show up like <paramNode.getName(): enumData.getName()>
                    // So if paramNode.getName() == "value" and enumData.getName() == "bool": <value: bool>
                    CommandParamData commandParamData = new CommandParamData();
                    commandParamData.setName(paramNode.getName());
                    commandParamData.setOptional(optional);
                    commandParamData.setEnumData(enumData);
                    commandParamData.setType(type);

                    children.add(new ParamInfo(paramNode, commandParamData));
                }
            }

            // Recursively build all child options
            for (ParamInfo child : children) {
                child.buildChildren(context, allNodes);
            }
        }

        /**
         * Mitigates <a href="https://github.com/GeyserMC/Geyser/issues/3411">issue 3411</a>. Not a perfect solution.
         */
        private static String getEnumDataName(CommandNode node) {
            if (node.getProperties() instanceof ResourceProperties properties) {
                Key registryKey = properties.getRegistryKey();
                return registryKey.value();
            }
            return node.getParser().name();
        }

        /**
         * Comparing CommandNode type a and b, determine if they are in the same overload.
         * <p>
         * Take the <code>gamerule</code> command, and let's present three "subcommands" you can perform:
         *
         * <ul>
         *     <li><code>gamerule doDaylightCycle true</code></li>
         *     <li><code>gamerule announceAdvancements false</code></li>
         *     <li><code>gamerule randomTickSpeed 3</code></li>
         * </ul>
         *
         * While all three of them are indeed part of the same command, the command setting randomTickSpeed parses an int,
         * while the others use boolean. In Bedrock, this should be presented as a separate overload to indicate that this
         * does something a little different.
         * <p>
         * Therefore, this function will return <code>true</code> if the first two are compared, as they use the same
         * parsers. If the third is compared with either of the others, this function will return <code>false</code>.
         * <p>
         * Here's an example of how the above would be presented to Bedrock (as of 1.16.200). Notice how the top two <code>CommandParamData</code>
         * classes of each array are identical in type, but the following class is different:
         * <pre>
         *     overloads=[
         *         [
         *            CommandParamData(name=doDaylightCycle, optional=false, enumData=CommandEnumData(name=announceAdvancements, values=[announceAdvancements, doDaylightCycle], isSoft=false), type=STRING, postfix=null, options=[])
         *            CommandParamData(name=value, optional=false, enumData=CommandEnumData(name=value, values=[true, false], isSoft=false), type=null, postfix=null, options=[])
         *         ]
         *         [
         *            CommandParamData(name=randomTickSpeed, optional=false, enumData=CommandEnumData(name=randomTickSpeed, values=[randomTickSpeed], isSoft=false), type=STRING, postfix=null, options=[])
         *            CommandParamData(name=value, optional=false, enumData=null, type=INT, postfix=null, options=[])
         *         ]
         *     ]
         * </pre>
         *
         * @return if these two can be merged into one overload.
         */
        private boolean isCompatible(CommandNode[] allNodes, CommandNode a, CommandNode b) {
            if (a == b) return true;
            if (a.getParser() != b.getParser()) return false;
            if (a.getChildIndices().length != b.getChildIndices().length) return false;

            for (int i = 0; i < a.getChildIndices().length; i++) {
                boolean hasSimilarity = false;
                CommandNode a1 = allNodes[a.getChildIndices()[i]];
                // Search "b" until we find a child that matches this one
                for (int j = 0; j < b.getChildIndices().length; j++) {
                    if (isCompatible(allNodes, a1, allNodes[b.getChildIndices()[j]])) {
                        hasSimilarity = true;
                        break;
                    }
                }

                if (!hasSimilarity) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Get the tree of every parameter node (recursive)
         *
         * @return List of parameter options arrays for the command
         */
        public List<CommandOverloadData> getTree() {
            List<CommandOverloadData> treeParamData = new ArrayList<>();

            for (ParamInfo child : children) {
                // Get the tree from the child
                List<CommandOverloadData> childTree = child.getTree();

                // Un-pack the tree append the child node to it and push into the list
                for (CommandOverloadData subChildData : childTree) {
                    CommandParamData[] subChild = subChildData.getOverloads();
                    CommandParamData[] tmpTree = new CommandParamData[subChild.length + 1];
                    tmpTree[0] = child.getParamData();
                    System.arraycopy(subChild, 0, tmpTree, 1, subChild.length);

                    treeParamData.add(new CommandOverloadData(false, tmpTree));
                }

                // If we have no more child parameters just the child
                if (childTree.size() == 0) {
                    treeParamData.add(new CommandOverloadData(false, new CommandParamData[] { child.getParamData() }));
                }
            }

            return treeParamData;
        }
    }
}
