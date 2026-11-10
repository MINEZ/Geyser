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

package org.geysermc.geyser.translator.protocol.java;

import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.NeteaseProfile;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JavaCommandsTranslatorTest {

    @Test
    public void renamesEnumsThatShareANameButNotTheirValues() {
        CommandParamData tagAction = literal(enumOf("add", "add", "remove"));
        CommandParamData teamAction = literal(enumOf("add", "add"));
        CommandParamData sameAsTag = literal(enumOf("add", "add", "remove"));
        CommandParamData clashesWithAlias = literal(enumOf("teamAliases", "join"));

        CommandData tag = command("tag", new CommandOverloadData(false, new CommandParamData[]{tagAction}));
        // 同一个参数被两个重载共用，改名后两处须一致
        CommandData team = command("team",
            new CommandOverloadData(false, new CommandParamData[]{teamAction}),
            new CommandOverloadData(false, new CommandParamData[]{teamAction, clashesWithAlias}));
        CommandData other = command("other", new CommandOverloadData(false, new CommandParamData[]{sameAsTag}));

        JavaCommandsTranslator.assignUniqueEnumNames(List.of(tag, team, other));

        assertEquals("add", tagAction.getEnumData().getName());
        assertEquals("add_2", teamAction.getEnumData().getName());
        assertEquals(Set.of("add"), teamAction.getEnumData().getValues().keySet());
        assertSame(tagAction.getEnumData(), sameAsTag.getEnumData());
        assertEquals("teamAliases_2", clashesWithAlias.getEnumData().getName());
    }

    @Test
    public void sendsOnlyNewCommandsWithFreshEnumNames() {
        Set<String> sentCommands = new java.util.HashSet<>();
        Set<String> sentEnums = new java.util.HashSet<>();
        CommandParamData tagAction = literal(enumOf("add", "add", "remove"));
        List<CommandData> first = List.of(command("tag", new CommandOverloadData(false, new CommandParamData[]{tagAction})));
        assertEquals(first, JavaCommandsTranslator.newCommandsOnly(first, sentCommands, sentEnums));
        assertEquals(Set.of("tag"), sentCommands);
        assertEquals(Set.of("add", "tagAliases"), sentEnums);

        // 切换服务器后：已有的 tag 不再发；新命令 team 的 add 与已发过的重名，改名。
        CommandParamData tagAgain = literal(enumOf("add", "add", "remove"));
        CommandParamData teamAction = literal(enumOf("add", "add"));
        List<CommandData> second = JavaCommandsTranslator.newCommandsOnly(List.of(
            command("tag", new CommandOverloadData(false, new CommandParamData[]{tagAgain})),
            command("team", new CommandOverloadData(false, new CommandParamData[]{teamAction}))), sentCommands, sentEnums);
        assertEquals(List.of("team"), second.stream().map(CommandData::getName).toList());
        assertEquals("add_2", teamAction.getEnumData().getName());
        assertEquals("teamAliases", second.get(0).getAliases().getName());
        assertTrue(sentEnums.contains("add_2"));

        // 再来一次同样的命令表：没有新的命令。
        assertTrue(JavaCommandsTranslator.newCommandsOnly(List.of(
            command("team", new CommandOverloadData(false, new CommandParamData[]{literal(enumOf("add", "add"))}))), sentCommands, sentEnums).isEmpty());
    }

    @Test
    public void sendsCommandsOnlyToReleaseClients() {
        GeyserSession session = mock(GeyserSession.class);
        // 国际版客户端
        assertTrue(JavaCommandsTranslator.sendsCommands(session));
        // 网易正式环境
        when(session.getNeteaseProfile()).thenReturn(profile("obt", false));
        assertTrue(JavaCommandsTranslator.sendsCommands(session));
        // 网易测试环境（开发版客户端）与自签名登录
        when(session.getNeteaseProfile()).thenReturn(profile("expr1", false));
        assertFalse(JavaCommandsTranslator.sendsCommands(session));
        when(session.getNeteaseProfile()).thenReturn(profile("obt", true));
        assertFalse(JavaCommandsTranslator.sendsCommands(session));
    }

    private static NeteaseProfile profile(String env, boolean developer) {
        return new NeteaseProfile(null, "测试", "NE1", null, env, "pc", "windows", null, null, null, "key", null, !developer, developer);
    }

    private static CommandData command(String name, CommandOverloadData... overloads) {
        CommandEnumData aliases = enumOf(name + "Aliases", name);
        return new CommandData(name, "", Set.of(), CommandPermission.ANY, aliases, Collections.emptyList(), overloads);
    }

    private static CommandParamData literal(CommandEnumData enumData) {
        CommandParamData param = new CommandParamData();
        param.setName(enumData.getName());
        param.setEnumData(enumData);
        return param;
    }

    private static CommandEnumData enumOf(String name, String... values) {
        Map<String, Set<CommandEnumConstraint>> map = new LinkedHashMap<>();
        for (String value : values) {
            map.put(value, Set.of());
        }
        return new CommandEnumData(name, map, false);
    }
}
