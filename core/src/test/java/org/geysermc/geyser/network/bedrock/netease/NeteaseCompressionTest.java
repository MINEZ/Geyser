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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import org.geysermc.geyser.GeyserImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * MINEZ：网易客户端的压缩：读 raw deflate，也读带 zlib 头的流；写 raw deflate。
 */
class NeteaseCompressionTest {
    private static final byte[] DATA = "China Edition batch ".repeat(50).getBytes(StandardCharsets.UTF_8);

    private static byte[] deflate(boolean wrapped) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, !wrapped);
        deflater.setInput(DATA);
        deflater.finish();
        byte[] out = new byte[4096];
        int length = deflater.deflate(out);
        deflater.end();
        return Arrays.copyOf(out, length);
    }

    private static byte[] decode(NeteaseCompression compression, byte[] compressed) throws Exception {
        ByteBuf decoded = compression.decode(null, Unpooled.wrappedBuffer(compressed));
        try {
            return ByteBufUtil.getBytes(decoded);
        } finally {
            decoded.release();
        }
    }

    @Test
    void readsRawAndZlibWrappedBatches() throws Exception {
        try (MockedStatic<GeyserImpl> geyser = mockStatic(GeyserImpl.class)) {
            geyser.when(GeyserImpl::getInstance).thenReturn(mock(GeyserImpl.class, RETURNS_DEEP_STUBS));
            NeteaseCompression compression = new NeteaseCompression();
            assertArrayEquals(DATA, decode(compression, deflate(false)));
            assertArrayEquals(DATA, decode(compression, deflate(true)));
            assertArrayEquals(DATA, decode(compression, deflate(true)));
            assertThrows(DataFormatException.class, () -> decode(compression, new byte[] {1, 2, 3, 4, 5, 6, 7}));
        }
    }

    @Test
    void writesRawDeflate() throws Exception {
        NeteaseCompression compression = new NeteaseCompression();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        ByteBuf encoded = compression.encode(ctx, Unpooled.wrappedBuffer(DATA));
        byte[] bytes = ByteBufUtil.getBytes(encoded);
        encoded.release();
        Inflater inflater = new Inflater(true);
        inflater.setInput(bytes);
        byte[] out = new byte[DATA.length];
        inflater.inflate(out);
        inflater.end();
        assertArrayEquals(DATA, out);
    }
}
