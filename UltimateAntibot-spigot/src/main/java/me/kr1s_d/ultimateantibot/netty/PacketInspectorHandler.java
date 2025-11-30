package me.kr1s_d.ultimateantibot.netty;

import java.net.InetSocketAddress;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import me.kr1s_d.ultimateantibot.ConnectionController;
import me.kr1s_d.ultimateantibot.PacketAntibotManager;

@ChannelHandler.Sharable
public class PacketInspectorHandler extends ChannelInboundHandlerAdapter {
    private final PacketAntibotManager manager;
    private final ConnectionController controller;
    public static final AttributeKey<TokenBucket> TB_KEY = AttributeKey.valueOf("uab-tokenbucket");
    public static final AttributeKey<Player> PLAYER_KEY = AttributeKey.valueOf("uab-player");
    public static final AttributeKey<String> PLAYER_NAME_KEY = AttributeKey.valueOf("uab-player-name");
    public static final AttributeKey<java.util.concurrent.atomic.AtomicInteger> PACKET_COUNTER_KEY = AttributeKey.valueOf("uab-packet-counter");
    private final NotificationAggregator aggregator;
    private final java.util.concurrent.ExecutorService notifierExecutor;
    private static final int PACKET_NOTIFY_THRESHOLD = 4;

    public PacketInspectorHandler(PacketAntibotManager manager, ConnectionController controller) {
        this(manager, controller, null, null);
    }

    public PacketInspectorHandler(PacketAntibotManager manager, ConnectionController controller, java.util.concurrent.ExecutorService notifierExecutor) {
        this(manager, controller, notifierExecutor, null);
    }

    public PacketInspectorHandler(PacketAntibotManager manager, ConnectionController controller, java.util.concurrent.ExecutorService notifierExecutor, NotificationAggregator aggregator) {
        this.manager = manager;
        this.controller = controller;
        this.notifierExecutor = notifierExecutor;
        this.aggregator = aggregator;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        Channel ch = ctx.channel();
        controller.registerChannel(ch, ch.attr(PLAYER_KEY).get());
        Object ra = ch.remoteAddress();
        if (ra instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress) ra;
            final String connId = controller.getConnectionId(ch);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        manager.notifyHandshake(connId != null ? connId : (addr.getAddress().getHostAddress() + ":" + addr.getPort()), addr);
                    } catch (Throwable ignored) {}
                }
            };
            if (aggregator != null) {
                final String idForNotify = connId != null ? connId : (addr.getAddress().getHostAddress() + ":" + addr.getPort());
                try { manager.notifyHandshake(idForNotify, addr); } catch (Throwable ignored) {}
            } else {
                final String idForNotify = connId != null ? connId : (addr.getAddress().getHostAddress() + ":" + addr.getPort());
                try { manager.notifyHandshake(idForNotify, addr); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        Object ra = ch.remoteAddress();
        if (ra instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress) ra;
            final String connId = controller.getConnectionId(ch);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        manager.notifyDisconnect(connId != null ? connId : (addr.getAddress().getHostAddress() + ":" + addr.getPort()));
                    } catch (Throwable ignored) {}
                }
            };
            try { manager.notifyDisconnect(connId != null ? connId : (addr.getAddress().getHostAddress() + ":" + addr.getPort())); } catch (Throwable ignored) {}
        }
        controller.unregisterChannel(ch);
        try { ch.attr(TB_KEY).set(null); } catch (Throwable ignored) {}
        try { ch.attr(PACKET_COUNTER_KEY).set(null); } catch (Throwable ignored) {}
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof MinecraftPacket)) {
            super.channelRead(ctx, msg);
            return;
        }

        MinecraftPacket p = (MinecraftPacket) msg;
        int id = p.getPacketId();
        java.util.logging.Logger log = Bukkit.getLogger();
        Channel ch = ctx.channel();
        String playerName = null;
        try { playerName = ch.attr(PLAYER_NAME_KEY).get(); } catch (Throwable ignored) {}
        if (playerName == null) playerName = "?";
        if (log.isLoggable(Level.FINER)) {
            log.log(Level.FINER, "NettyPacket player=" + playerName + " id=" + id);
        }

        final String connId = controller.getConnectionId(ch);
        if (connId == null) {
            super.channelRead(ctx, msg);
            return;
        }

        TokenBucket tb = null;
        try { tb = ch.attr(TB_KEY).get(); } catch (Throwable ignored) {}
        boolean allowed = tb == null ? true : tb.tryConsume();
        if (allowed) {
            java.util.concurrent.atomic.AtomicInteger counter = null;
            try { counter = ch.attr(PACKET_COUNTER_KEY).get(); } catch (Throwable ignored) {}
            if (counter == null) {
                if (aggregator != null) aggregator.submitPacketSeen(connId); else submitNotify(() -> { try { manager.notifyPacketSeen(connId);} catch(Throwable ignored){} });
            } else {
                int v = counter.incrementAndGet();
                if ((v % PACKET_NOTIFY_THRESHOLD) == 0) {
                    if (aggregator != null) aggregator.submitPacketSeen(connId); else submitNotify(() -> { try { manager.notifyPacketSeen(connId);} catch(Throwable ignored){} });
                }
            }
        }

        ByteBuf payload = p.getPayload();
        try {
            int readable = payload.readableBytes();
            if (readable >= 1 && readable <= 10) {
                if (allowed) {
                    if (aggregator != null) aggregator.submitClientKeepAlive(connId); else submitNotify(() -> { try { manager.notifyClientKeepAlive(connId);} catch (Throwable ignored){} });
                }
            }
        } finally {
            try { payload.release(); } catch (Throwable ignored) {}
        }

        super.channelRead(ctx, msg);
    }

    private void submitNotify(Runnable r) {
        if (notifierExecutor != null) {
            try { notifierExecutor.execute(r); } catch (Throwable t) { /* drop on executor saturation */ }
        } else {
            r.run();
        }
    }
}