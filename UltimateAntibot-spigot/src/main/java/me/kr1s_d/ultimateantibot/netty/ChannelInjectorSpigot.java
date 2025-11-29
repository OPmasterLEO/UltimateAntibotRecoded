package me.kr1s_d.ultimateantibot.netty;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.logging.Level;

public final class ChannelInjectorSpigot {
    private ChannelInjectorSpigot() {}

    public static void injectForPlayer(Player player, Object plugin, PacketInspectorHandler handler) {
        try {
            // Try to get the player connection via CraftPlayer.getHandle().playerConnection.networkManager.channel
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Field pcField = null;
            for (Field f : craftPlayer.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().toLowerCase().contains("playerconnection")) {
                    pcField = f;
                    break;
                }
            }
            if (pcField == null) return;
            pcField.setAccessible(true);
            Object playerConnection = pcField.get(craftPlayer);

            Field[] pcFields = playerConnection.getClass().getDeclaredFields();
            Object networkManager = null;
            for (Field f : pcFields) {
                if (f.getType().getSimpleName().toLowerCase().contains("networkmanager") || f.getName().toLowerCase().contains("network")) {
                    f.setAccessible(true);
                    networkManager = f.get(playerConnection);
                    break;
                }
            }
            if (networkManager == null) return;

            Field channelField = null;
            for (Field f : networkManager.getClass().getDeclaredFields()) {
                if (io.netty.channel.Channel.class.isAssignableFrom(f.getType())) {
                    channelField = f;
                    break;
                }
            }
            if (channelField == null) return;
            channelField.setAccessible(true);
            Channel ch = (Channel) channelField.get(networkManager);
            if (ch == null) return;

            if (ch.pipeline().get("uab-frame") == null) {
                ch.pipeline().addLast("uab-frame", new MinecraftFrameDecoder());
            }
            if (ch.pipeline().get("uab-inspector") == null) {
                ch.pipeline().addLast("uab-inspector", handler);
            }
        } catch (Throwable t) {
            java.util.logging.Logger.getLogger("UltimateAntiBot").log(Level.FINER, "Channel injector failed for " + player.getName() + ": " + t.getMessage());
        }
    }
}
