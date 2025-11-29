package me.kr1s_d.ultimateantibot.netty;

import java.net.InetSocketAddress;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import me.kr1s_d.ultimateantibot.ConnectionController;
import me.kr1s_d.ultimateantibot.PacketAntibotManager;

public class PacketInspectorHandler extends ChannelInboundHandlerAdapter {
    private final Player player;
    private final PacketAntibotManager manager;
    private final ConnectionController controller;
    private static final AttributeKey<TokenBucket> TB_KEY = AttributeKey.valueOf("uab-tokenbucket");

    public PacketInspectorHandler(Player player, PacketAntibotManager manager, ConnectionController controller) {
        this.player = player;
        this.manager = manager;
        this.controller = controller;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        Channel ch = ctx.channel();
        try {
            controller.registerChannel(ch, player);
            try {
                TokenBucket tb = ch.attr(TB_KEY).get();
                if (tb == null) {
                    tb = new TokenBucket(20, 1000);
                    ch.attr(TB_KEY).set(tb);
                }
            } catch (Throwable ignored) {}
            Object ra = ch.remoteAddress();
            if (ra instanceof InetSocketAddress) {
                InetSocketAddress addr = (InetSocketAddress) ra;
                String connId = controller.getConnectionId(ch);
                manager.notifyHandshake(connId != null ? connId : (addr.getAddress().getHostAddress() + ":" + addr.getPort()), addr);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        try {
            Object ra = ch.remoteAddress();
            if (ra instanceof InetSocketAddress) {
                InetSocketAddress addr = (InetSocketAddress) ra;
                String connId = controller.getConnectionId(ch);
                manager.notifyDisconnect(connId != null ? connId : (addr.getAddress().getHostAddress() + ":" + addr.getPort()));
            }
            controller.unregisterChannel(ch);
            try { ch.attr(TB_KEY).set(null); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof MinecraftPacket) {
                MinecraftPacket p = (MinecraftPacket) msg;
                int id = p.getPacketId();
                java.util.logging.Logger log = Bukkit.getLogger();
                if (log.isLoggable(Level.FINER)) {
                    log.log(Level.FINER, "NettyPacket player=" + player.getName() + " id=" + id);
                }

                Channel ch = ctx.channel();
                String connId = controller.getConnectionId(ch);

                if (connId != null) {
                    TokenBucket tb = null;
                    try { tb = ch.attr(TB_KEY).get(); } catch (Throwable ignored) {}
                    boolean allowed = tb == null ? true : tb.tryConsume();
                    if (allowed) manager.notifyPacketSeen(connId);

                    ByteBuf payload = p.getPayload();
                    try {
                        int readable = payload.readableBytes();
                        if (readable >= 1 && readable <= 10) {
                            if (allowed) manager.notifyClientKeepAlive(connId);
                        }
                    } finally {
                        try { payload.release(); } catch (Throwable ignored) {}
                    }
                }
                return;
            }
        } finally {
            super.channelRead(ctx, msg);
        }
    }
}
