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

import io.netty.channel.ChannelException;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.LinuxSocket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.Socket;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.netty.util.nethernet.IpRangeSet;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * MINEZ：向网易的 noa 内核模块查询经中转进入的玩家的真实地址。
 * <p>
 * 网易的中转是 fullnat 网关：玩家的包到本服时，来源已换成中转的地址与端口。中转另发一个与这条连接五元组相同、但 UDP 校验和
 * 故意填错的包，负载里是玩家的真实地址（UOA，UDP Option of Address，首字节为操作码 127，即 0x7f）。网易的服务器上装有 noa
 * 内核模块（/proc/net/noa，网易云网络组，源自 iQIYI DPVS 的 uoa 模块），它在 LOCAL_IN（priority 101）认出这种包，
 * 按“中转地址:端口 → 本服地址:端口”记下真实地址后丢弃它，不交给任何程序；程序以 getsockopt 查询：
 * <pre>
 * getsockopt(fd, IPPROTO_IP, UOA_CTL = 2048, struct uoa_param *, &amp;len)
 * struct uoa_param（packed，56 字节）：
 *   输入  saddr 16 | daddr 16 | sport 2 | dport 2 | af 1      中转与本服的地址、端口（网络字节序），af 为 AF_INET（2）
 *   输出  real_saddr 16 | real_sport 2 | real_af 1             玩家的真实地址与端口
 * </pre>
 * IPv4 地址写在 16 字节字段的前 4 字节。本服地址不能是 0.0.0.0，须为包的目的地址，即本机实际的地址。Geyser 通常绑定在
 * 0.0.0.0，连接上看不到包的目的地址，因此按本机的各个 IPv4 地址逐个查询，查到为止（每次查询只是一次系统调用，本机地址只有几个）。
 * 查询用哪个套接字都可以（noa 只按五元组查），这里另开一个不绑定的 IPv4 UDP 套接字专门用于查询。noa 默认保存 30 秒，
 * 查询一次后约 2 秒内删除，所以每条连接只在建立会话时查询一次。
 * <p>
 * 中转只在一条中转会话开始时发地址包。玩家断开后很快重连时，中转沿用原来的会话（中转的地址与端口不变），不再发地址包，
 * noa 中已经没有记录（2026-11-19 正式服：两名玩家两三分钟内重连，第二次 noa 都查不到，只能以中转的地址进入）。因此每次从 noa
 * 查到时都记下“中转地址:端口 → 真实地址”，noa 查不到时用 {@value #REMEMBER_MINUTES} 分钟内同一中转地址:端口记下的地址，
 * 用一次重新计时。noa 查得到时始终以 noa 为准：中转开始新的会话时一定会发新的地址包，所以 noa 查不到时，这个中转端口
 * 仍是原来那名玩家的会话。
 * <p>
 * 前提：主机装有 noa 并开启 uoa（/proc/net/noa/uoa_on 为 1）；用了 ufw 时，校验和错误的包会先被 conntrack 判为 INVALID 丢弃、
 * 到不了 noa，须在 prerouting（priority -300）把这些包的 UDP 校验和置 0（0 仍不等于正确值，noa 照样认得），见 README。
 * 没有 noa、不是 Linux、或查不到时一律返回 null，玩家照旧以中转的地址进入。
 */
public final class NeteaseRealAddress implements AutoCloseable {
    /** noa 的 UDP 查询选项，见 noa 源码 uapi/unoa.h。 */
    static final int UOA_CTL = 2048;
    static final int IPPROTO_IP = 0;
    static final int AF_INET = 2;
    static final int PARAM_LENGTH = 56;
    private static final int ADDRESS_LENGTH = 16;
    private static final int REAL_ADDRESS = 37;
    private static final int REAL_PORT = 53;
    private static final int REAL_FAMILY = 55;

    /** noa 查不到时，同一中转地址:端口记下的真实地址在多少分钟内可用。 */
    static final long REMEMBER_MINUTES = 30;
    /** 最多记下多少个中转地址:端口，超出时去掉最久没用的。 */
    static final int REMEMBER_MAX = 20_000;

    /** 每分钟的汇总，见 {@link NeteaseRelayStats}。 */
    public static final NeteaseRelayStats STATS = new NeteaseRelayStats();

    private final IpRangeSet relays;
    private final @Nullable Socket socket;
    private final @Nullable String unavailable;
    /** 本机的 IPv4 地址，查询时依次作为本服地址；启动时取一次。 */
    private final List<Inet4Address> localAddresses;
    /** 中转地址:端口 → 从 noa 查到的真实地址，按最近使用排序；访问时加锁。 */
    private final Map<InetSocketAddress, Remembered> remembered = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<InetSocketAddress, Remembered> eldest) {
            return size() > REMEMBER_MAX;
        }
    };
    /** 毫秒时钟，测试时替换。 */
    LongSupplier clock = System::currentTimeMillis;

    private record Remembered(InetSocketAddress real, long at) {
    }

    private NeteaseRealAddress(IpRangeSet relays, @Nullable Socket socket, @Nullable String unavailable,
                               List<Inet4Address> localAddresses) {
        this.relays = relays;
        this.socket = socket;
        this.unavailable = unavailable;
        this.localAddresses = localAddresses;
    }

    /**
     * @param relays 网易中转的地址或网段，即 {@code relay-addresses}
     */
    public static NeteaseRealAddress open(Collection<String> relays) {
        IpRangeSet ranges = IpRangeSet.parse(relays);
        List<Inet4Address> locals = localIpv4Addresses();
        if (!Epoll.isAvailable()) {
            return new NeteaseRealAddress(ranges, null, "not on Linux with epoll", locals);
        }
        try {
            return new NeteaseRealAddress(ranges, LinuxSocket.newSocketDgram(SocketProtocolFamily.INET), null, locals);
        } catch (RuntimeException | LinkageError e) {
            return new NeteaseRealAddress(ranges, null, "could not open a socket for the lookup: " + e, locals);
        }
    }

    /** 本机启用中的网卡上的 IPv4 地址，回环地址除外。 */
    private static List<Inet4Address> localIpv4Addresses() {
        List<Inet4Address> result = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address ipv4 && !result.contains(ipv4)) {
                        result.add(ipv4);
                    }
                }
            }
        } catch (SocketException ignored) {
            // 取不到时只用连接上的本机地址
        }
        return List.copyOf(result);
    }

    /**
     * @return 不能查询的原因，能查询时为 null；用于启动日志
     */
    public @Nullable String unavailable() {
        return unavailable;
    }

    /**
     * 查询这条连接的玩家的真实地址，并计入每分钟的汇总。每条连接只在建立会话时调用一次。
     *
     * @param remote 连接的对端，即中转的地址与端口
     * @param local  连接上的本机地址与端口；地址为 0.0.0.0 时按本机的各个 IPv4 地址查询
     * @return 玩家的真实地址；不经中转、没有 noa、或 noa 中没有这条连接时为 null
     */
    public @Nullable InetSocketAddress lookup(InetSocketAddress remote, InetSocketAddress local) {
        if (!this.relays.contains(remote.getAddress())) {
            STATS.directConnection();
            return null;
        }
        String[] problem = new String[1];
        InetSocketAddress real = query(remote, local, problem);
        if (real != null) {
            remember(remote, real);
            STATS.relayedConnection(true, null);
            return real;
        }
        InetSocketAddress earlier = recall(remote);
        if (earlier != null) {
            STATS.rememberedConnection();
            return earlier;
        }
        STATS.relayedConnection(false, problem[0]);
        return null;
    }

    /** 记下从 noa 查到的真实地址。 */
    void remember(InetSocketAddress relay, InetSocketAddress real) {
        synchronized (this.remembered) {
            this.remembered.put(relay, new Remembered(real, this.clock.getAsLong()));
        }
    }

    /** 同一中转地址:端口在 {@value #REMEMBER_MINUTES} 分钟内记下的真实地址，用一次重新计时；没有或已过期时为 null。 */
    @Nullable InetSocketAddress recall(InetSocketAddress relay) {
        long now = this.clock.getAsLong();
        synchronized (this.remembered) {
            Remembered entry = this.remembered.get(relay);
            if (entry == null) {
                return null;
            }
            if (now - entry.at() > TimeUnit.MINUTES.toMillis(REMEMBER_MINUTES)) {
                this.remembered.remove(relay);
                return null;
            }
            this.remembered.put(relay, new Remembered(entry.real(), now));
            return entry.real();
        }
    }

    private @Nullable InetSocketAddress query(InetSocketAddress remote, InetSocketAddress local, String[] problem) {
        Socket socket = this.socket;
        if (socket == null) {
            problem[0] = this.unavailable;
            return null;
        }
        if (!(remote.getAddress() instanceof Inet4Address)) {
            problem[0] = "not an IPv4 connection";
            return null;
        }
        ByteBuffer param = ByteBuffer.allocateDirect(PARAM_LENGTH);
        for (Inet4Address candidate : candidates(local)) {
            writeQuery(param, remote, new InetSocketAddress(candidate, local.getPort()));
            try {
                socket.getRawOpt(IPPROTO_IP, UOA_CTL, param);
            } catch (IOException | ChannelException e) {
                // 常见的有：noa 中没有这条连接（No such file or directory）、没有 noa（Protocol not available）、
                // noa 的 uoa 没有开启（Invalid argument）。只记最后一个，不含任何地址。
                problem[0] = String.valueOf(e.getMessage());
                continue;
            }
            InetSocketAddress real = readAnswer(param);
            if (real != null) {
                return real;
            }
            problem[0] = "noa answered without an IPv4 address";
        }
        if (problem[0] == null) {
            problem[0] = "no local IPv4 address to look up with";
        }
        return null;
    }

    /** 作为本服地址查询的候选：连接上的本机地址是具体地址时只用它，是 0.0.0.0 时用本机的各个 IPv4 地址。 */
    List<Inet4Address> candidates(InetSocketAddress local) {
        if (local.getAddress() instanceof Inet4Address ipv4 && !ipv4.isAnyLocalAddress()) {
            return List.of(ipv4);
        }
        return this.localAddresses;
    }

    /** 按 struct uoa_param 写入要查询的五元组，其余字节为 0。 */
    static void writeQuery(ByteBuffer param, InetSocketAddress remote, InetSocketAddress local) {
        param.clear();
        for (int i = 0; i < PARAM_LENGTH; i++) {
            param.put(i, (byte) 0);
        }
        param.put(0, remote.getAddress().getAddress());
        param.put(ADDRESS_LENGTH, local.getAddress().getAddress());
        param.putShort(2 * ADDRESS_LENGTH, (short) remote.getPort());
        param.putShort(2 * ADDRESS_LENGTH + 2, (short) local.getPort());
        param.put(2 * ADDRESS_LENGTH + 4, (byte) AF_INET);
        param.limit(PARAM_LENGTH);
        param.position(0);
    }

    /** 读出 noa 写回的真实地址，不是 IPv4 或为空时为 null。 */
    static @Nullable InetSocketAddress readAnswer(ByteBuffer param) {
        if (param.get(REAL_FAMILY) != AF_INET) {
            return null;
        }
        byte[] address = new byte[4];
        param.get(REAL_ADDRESS, address);
        int port = Short.toUnsignedInt(param.getShort(REAL_PORT));
        try {
            InetAddress real = InetAddress.getByAddress(address);
            return real.isAnyLocalAddress() ? null : new InetSocketAddress(real, port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Override
    public void close() {
        Socket socket = this.socket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关服时，关不掉也无所谓
            }
        }
    }
}
