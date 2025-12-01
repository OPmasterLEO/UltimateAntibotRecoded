package me.kr1s_d.ultimateantibot;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import me.kr1s_d.ultimateantibot.netty.TokenBucket;

public class NettyConnectionController implements ConnectionController {
    private final ConcurrentHashMap<Channel, Player> channelToPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Channel> connIdToChannel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> usernameToIP = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<String>> ipToUsernames = new ConcurrentHashMap<>();
    private static final AttributeKey<String> CONNID_KEY = AttributeKey.valueOf("uab-connid");
    private static final AttributeKey<Player> PLAYER_KEY = AttributeKey.valueOf("uab-player");
    private static final AttributeKey<TokenBucket> TB_KEY = AttributeKey.valueOf("uab-tokenbucket");

    @Override
    public Collection<String> listConnectionIds() {
        return connIdToChannel.keySet();
    }

    @Override
    public InetSocketAddress getAddress(String connId) {
        Channel ch = connIdToChannel.get(connId);
        if (ch == null) return null;
        Object addr = ch.remoteAddress();
        if (addr instanceof InetSocketAddress) return (InetSocketAddress) addr;
        return null;
    }

    @Override
    public boolean hasPlayer(String connId) {
        return connIdToChannel.containsKey(connId);
    }

    @Override
    public boolean sendKeepAlive(String connId, long id) {
        Channel ch = connIdToChannel.get(connId);
        if (ch == null) return false;
        if (!ch.isActive() || !ch.isOpen()) return false;

        final ByteBuf frame = ch.alloc().buffer();
        try {
            int packetId = 0x1F;
            ByteBuf payload = ch.alloc().buffer();
            try {
                writeVarInt(payload, packetId);
                writeVarLong(payload, id);
                int payloadLen = payload.readableBytes();
                writeVarInt(frame, payloadLen);
                frame.writeBytes(payload, payload.readerIndex(), payloadLen);
            } finally {
                try { payload.release(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            try { frame.release(); } catch (Throwable ignored) {}
            return false;
        }

        try {
            ch.eventLoop().execute(() -> {
                try {
                    ch.writeAndFlush(frame).addListener(future -> {
                        if (!future.isSuccess()) {
                            try { future.cause(); } catch (Throwable ignored) {}
                            try { unregisterChannel(ch); } catch (Throwable ignored) {}
                            try { ch.close(); } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable t) {
                    try { frame.release(); } catch (Throwable ignored) {}
                    try { unregisterChannel(ch); } catch (Throwable ignored) {}
                    try { ch.close(); } catch (Throwable ignored) {}
                }
            });
            return true;
        } catch (Throwable t) {
            try { frame.release(); } catch (Throwable ignored) {}
            return false;
        }
    }

    @Override
    public void closeConnection(String connId) {
        Channel ch = connIdToChannel.remove(connId);
        if (ch != null) {
            channelToPlayer.remove(ch);
            try { ch.attr(CONNID_KEY).set(null); } catch (Throwable ignored) {}
            try { ch.close(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void registerChannel(Channel channel, Player player) {
        if (channel == null) return;
        channelToPlayer.put(channel, player);
        Object addr = channel.remoteAddress();
        if (addr instanceof InetSocketAddress) {
            InetSocketAddress a = (InetSocketAddress) addr;
            String id = a.getAddress().getHostAddress() + ":" + a.getPort();
            String ip = a.getAddress().getHostAddress();
            connIdToChannel.put(id, channel);
            if (player != null && player.getName() != null) {
                String username = player.getName().toLowerCase();
                usernameToIP.put(username, ip);
                ipToUsernames.computeIfAbsent(ip, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .addIfAbsent(username);
            }
            try { channel.attr(CONNID_KEY).set(id); } catch (Throwable ignored) {}
            try { channel.attr(PLAYER_KEY).set(player); } catch (Throwable ignored) {}
            try { channel.attr(TB_KEY).set(new TokenBucket(20, 1000)); } catch (Throwable ignored) {}
        }
        try {
            channel.closeFuture().addListener(f -> {
                try { unregisterChannel(channel); } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    @Override
    public void unregisterChannel(Channel channel) {
        if (channel == null) return;
        Player p = channelToPlayer.remove(channel);
        if (p != null && p.getName() != null) {
            String username = p.getName().toLowerCase();
            String ip = usernameToIP.remove(username);
            if (ip != null) {
                java.util.concurrent.CopyOnWriteArrayList<String> accounts = ipToUsernames.get(ip);
                if (accounts != null) {
                    accounts.remove(username);
                    if (accounts.isEmpty()) {
                        ipToUsernames.remove(ip);
                    }
                }
            }
        }
        try { channel.attr(CONNID_KEY).set(null); } catch (Throwable ignored) {}
        List<String> toRemove = connIdToChannel.entrySet().stream().filter(en -> en.getValue().equals(channel)).map(en -> en.getKey()).collect(Collectors.toList());
        for (String k : toRemove) connIdToChannel.remove(k);
    }

    @Override
    public String getConnectionId(Channel channel) {
        if (channel == null) return null;
        try {
            String id = channel.attr(CONNID_KEY).get();
            if (id != null) return id;
        } catch (Throwable ignored) {}
        Object addr = channel.remoteAddress();
        if (addr instanceof InetSocketAddress) {
            InetSocketAddress a = (InetSocketAddress) addr;
            String id = a.getAddress().getHostAddress() + ":" + a.getPort();
            try { channel.attr(CONNID_KEY).set(id); } catch (Throwable ignored) {}
            connIdToChannel.put(id, channel);
            return id;
        }
        return null;
    }

    /**
     * Resolve IP from username (currently online players only).
     * Returns null if username not found in active connections.
     */
    public String getIPFromUsername(String username) {
        if (username == null) return null;
        return usernameToIP.get(username.toLowerCase());
    }

    /**
     * Get all usernames associated with an IP address.
     * Returns empty list if no accounts found.
     */
    public List<String> getUsernamesOnIP(String ip) {
        if (ip == null) return java.util.Collections.emptyList();
        java.util.concurrent.CopyOnWriteArrayList<String> accounts = ipToUsernames.get(ip);
        return accounts != null ? new java.util.ArrayList<>(accounts) : java.util.Collections.emptyList();
    }

    /**
     * Check if IP has multiple accounts currently connected.
     */
    public boolean hasMultipleAccounts(String ip) {
        if (ip == null) return false;
        List<String> accounts = getUsernamesOnIP(ip);
        return accounts.size() > 1;
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & 0xFFFFFF80) != 0L) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value & 0x7F);
    }

    private static void writeVarLong(ByteBuf buf, long value) {
        while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
            buf.writeByte(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte((int) value & 0x7F);
    }
}
