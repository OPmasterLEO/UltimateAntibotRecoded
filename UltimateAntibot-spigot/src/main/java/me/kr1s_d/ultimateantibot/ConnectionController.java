package me.kr1s_d.ultimateantibot;

import java.net.InetSocketAddress;
import java.util.Collection;

import org.bukkit.entity.Player;

import io.netty.channel.Channel;

/**
 * Abstraction for performing connection-level operations (send/close/lookups).
 * The Spigot/Netty integration should provide an implementation that maps
 * connection IDs to underlying channels/sessions.
 */
public interface ConnectionController {
    Collection<String> listConnectionIds();
    InetSocketAddress getAddress(String connId);
    boolean hasPlayer(String connId);
    /**
     * Send a keep-alive packet to the specified connection.
     * @return true if the packet was sent successfully (or enqueued)
     */
    boolean sendKeepAlive(String connId, long id);
    /**
     * Close the connection associated with connId.
     */
    void closeConnection(String connId);

    /**
     * Register a Netty channel for a player so controller can map channel ⇄ connId.
     */
    void registerChannel(Channel channel, Player player);

    /**
     * Unregister a channel when handler is removed.
     */
    void unregisterChannel(Channel channel);

    /**
     * Lightweight lookup: return the connection id (e.g. "ip:port") for a channel
     * if known. This allows high-frequency handlers to avoid recomputing strings
     * from the remote address each packet.
     */
    String getConnectionId(Channel channel);
}
