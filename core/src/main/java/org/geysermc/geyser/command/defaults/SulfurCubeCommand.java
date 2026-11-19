/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.command.defaults;

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.living.monster.cubemob.SulfurCubeEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.util.ArrayList;
import java.util.List;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

/**
 * MINEZ：临时的校正命令。调整网易客户端中显示硫方怪体内方块的矿车的参数（见 SulfurCubeEntity），立即对在线玩家身边的硫方怪生效，
 * 不必重启。参数校正后定为常量，删去本命令。只有控制台与有 geyser.command.sulfurcube 权限者可用。
 */
public class SulfurCubeCommand extends GeyserCommand {
    private static final String ARGUMENTS = "arguments";

    private final GeyserImpl geyser;

    public SulfurCubeCommand(GeyserImpl geyser, String name, String description, String permission) {
        super(name, description, permission, TriState.NOT_SET);
        this.geyser = geyser;
    }

    @Override
    public void register(CommandManager<GeyserCommandSource> manager) {
        manager.command(baseBuilder(manager)
            .optional(ARGUMENTS, greedyStringParser())
            .handler(this::execute));
    }

    @Override
    public void execute(CommandContext<GeyserCommandSource> context) {
        GeyserCommandSource source = context.sender();
        String[] args = context.<String>optional(ARGUMENTS).map(value -> value.trim().split("\\s+")).orElse(new String[0]);
        if (args.length > 0 && !args[0].isEmpty()) {
            try {
                float seatY = Float.parseFloat(args[0]);
                float rotation = args.length > 1 ? Float.parseFloat(args[1]) : SulfurCubeEntity.tuneRotationOffset;
                float scale = args.length > 2 ? Float.parseFloat(args[2]) : SulfurCubeEntity.tuneScale;
                int displayOffset = args.length > 3 ? Integer.parseInt(args[3]) : SulfurCubeEntity.tuneDisplayOffset;
                boolean noAi = args.length > 4 ? Boolean.parseBoolean(args[4]) : SulfurCubeEntity.tuneNoAi;
                SulfurCubeEntity.tuneSeatY = seatY;
                SulfurCubeEntity.tuneRotationOffset = rotation;
                SulfurCubeEntity.tuneScale = scale;
                SulfurCubeEntity.tuneDisplayOffset = displayOffset;
                SulfurCubeEntity.tuneNoAi = noAi;
            } catch (NumberFormatException e) {
                source.sendMessage("用法：" + rootCommand() + " " + name() + " <座位高度> [朝向偏移] [缩放] [显示偏移] [NO_AI]");
                return;
            }
            for (GeyserSession session : geyser.onlineConnections()) {
                session.executeInEventLoop(() -> {
                    List<SulfurCubeEntity> cubes = new ArrayList<>();
                    for (Entity entity : session.getEntityCache().getEntitiesUnsafe().values()) {
                        if (entity instanceof SulfurCubeEntity cube) {
                            cubes.add(cube);
                        }
                    }
                    cubes.forEach(SulfurCubeEntity::refreshContainedBlock);
                });
            }
        }
        source.sendMessage("硫方怪体内方块：座位高度 " + SulfurCubeEntity.tuneSeatY + "，朝向偏移 " + SulfurCubeEntity.tuneRotationOffset
            + "，缩放 " + SulfurCubeEntity.tuneScale + "，显示偏移 " + SulfurCubeEntity.tuneDisplayOffset + "，NO_AI " + SulfurCubeEntity.tuneNoAi);
    }
}
