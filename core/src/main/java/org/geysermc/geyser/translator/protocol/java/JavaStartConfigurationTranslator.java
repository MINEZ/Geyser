/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

import org.geysermc.geyser.erosion.GeyserboundHandshakePacketHandler;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistryTagCache;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;

@Translator(packet = ClientboundStartConfigurationPacket.class)
public class JavaStartConfigurationTranslator extends PacketTranslator<ClientboundStartConfigurationPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundStartConfigurationPacket packet) {
        var erosionHandler = session.getErosionHandler();
        if (erosionHandler.isActive()) {
            // Set new handler before closing
            session.setErosionHandler(new GeyserboundHandshakePacketHandler(session));
            erosionHandler.close();
        }

        // Reset code of conduct being accepted
        session.hasAcceptedCodeOfConduct(false);

        ChunkUtils.sendEmptyChunks(session, session.getPlayerEntity().position().toInt(), session.getServerRenderDistance(), false);
        if (session.isSpawned()) {
            // MINEZ：代理换服时先重新进入配置阶段，上面已清空周围的区块，玩家此时还在原来的世界中，照常下坠、看到世界消失，
            // 直到新的服务器发来登录包、切换维度。从这里起就去掉重力、定身并加上失明，收到新服务器给出的位置后恢复。
            session.getPlayerEntity().holdUntilSpawned();
        }

        // Clear networked registries, pending new data from the server
        ((JavaRegistryTagCache) session.javaRegistries()).clear();
    }
}
