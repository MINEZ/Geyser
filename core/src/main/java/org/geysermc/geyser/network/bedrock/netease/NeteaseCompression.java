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

package org.geysermc.geyser.network.bedrock.netease;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.ZlibCompression;
import org.cloudburstmc.protocol.common.util.Zlib;
import org.geysermc.geyser.GeyserImpl;

import java.util.zip.DataFormatException;

/**
 * MINEZ：网易客户端协商压缩之后的 zlib 压缩。
 * <p>
 * 与国际版一样，服务端在 NetworkSettings 中宣告 ZLIB 之后，双方都用不带 zlib 头与校验和的 raw deflate，
 * 每批前面照常有压缩算法的前缀字节。但据调研，个别网易客户端的构建发来的是带 zlib 头的流，
 * 所以解压时先按 raw deflate 解，失败再按带头的 zlib 解；压缩时始终用 raw deflate。
 */
public final class NeteaseCompression implements BatchCompression {
    private final ZlibCompression raw = new ZlibCompression(Zlib.RAW);
    private final ZlibCompression wrapped = new ZlibCompression(Zlib.DEFAULT);
    private volatile boolean loggedWrapped;

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        return raw.encode(ctx, msg);
    }

    @Override
    public ByteBuf decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // 解压失败时读取位置可能已经变了，每次都从同一位置读。
        try {
            return raw.decode(ctx, msg.duplicate());
        } catch (DataFormatException rawFailure) {
            ByteBuf decoded;
            try {
                decoded = wrapped.decode(ctx, msg.duplicate());
            } catch (DataFormatException wrappedFailure) {
                rawFailure.addSuppressed(wrappedFailure);
                throw rawFailure;
            }
            if (!loggedWrapped) {
                loggedWrapped = true;
                GeyserImpl.getInstance().getLogger().debug("A NetEase client sent a zlib-wrapped batch instead of raw deflate; "
                    + "reading it as zlib for this connection");
            }
            return decoded;
        }
    }

    @Override
    public CompressionAlgorithm getAlgorithm() {
        return PacketCompressionAlgorithm.ZLIB;
    }

    @Override
    public void setLevel(int level) {
        raw.setLevel(level);
    }

    @Override
    public int getLevel() {
        return raw.getLevel();
    }
}
