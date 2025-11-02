package me.kr1s_d.ultimateantibot;

import java.util.ArrayList;
import java.util.List;

import me.kr1s_d.ultimateantibot.common.BarColor;
import me.kr1s_d.ultimateantibot.common.BarStyle;
import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.INotificator;
import me.kr1s_d.ultimateantibot.common.utils.ConfigManger;
import me.kr1s_d.ultimateantibot.common.utils.MessageManager;
import me.kr1s_d.ultimateantibot.common.utils.ServerUtil;
import me.kr1s_d.ultimateantibot.objects.DynamicBar;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.Title;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class Notificator implements INotificator {
    private static final List<ProxiedPlayer> actionbars = new ArrayList<>();
    private static final List<ProxiedPlayer> titles = new ArrayList<>();
    private static final DynamicBar bar = new DynamicBar("&fWaiting for a new attack!", BarColor.RED, BarStyle.SOLID);
    private String lastActionbarRaw;
    private String lastTitleRaw;
    private String lastSubtitleRaw;

    public static void automaticNotification(ProxiedPlayer player){
        if(actionbars.contains(player)) return;
        actionbars.remove(player);
        bar.removePlayer(player);
        if(player.getPendingConnection().getVersion() > 106){
            if(bar.hasPlayer(player)){
                return;
            }
            if(ConfigManger.enableBossBarAutomaticNotification){
                bar.addPlayer(player);
            }
        }
        actionbars.add(player);
    }

    public static void toggleBossBar(ProxiedPlayer player){
        if(bar.hasPlayer(player)){
            bar.removePlayer(player);
        }else{
            bar.addPlayer(player);
        }
        player.sendMessage(new TextComponent(ServerUtil.colorize(MessageManager.prefix + MessageManager.toggledBossBar)));
    }

    public static void toggleActionBar(ProxiedPlayer player){
        if(actionbars.contains(player)){
            actionbars.remove(player);
        }else {
            actionbars.add(player);
        }
        player.sendMessage(new TextComponent(ServerUtil.colorize(MessageManager.prefix + MessageManager.toggledActionbar)));
    }

    public static void toggleTitle(ProxiedPlayer player) {
        if(titles.contains(player)){
            titles.remove(player);
        }else {
            titles.add(player);
        }
        player.sendMessage(new TextComponent(ServerUtil.colorize(MessageManager.prefix + MessageManager.toggledTitle)));
    }

    public static void onQuit(ProxiedPlayer player) {
        titles.remove(player);
        bar.removePlayer(player);
        actionbars.remove(player);
    }

    public static void disableAllNotifications() {
        actionbars.clear();
        bar.removeAll();
        titles.clear();
    }

    public void sendActionbar(String coloredMessage) {
        actionbars.forEach(ac -> ac.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(coloredMessage)));
    }

    public void sendTitle(String title, String subtitle) {
        Title t = ProxyServer.getInstance().createTitle();
        String formattedTitle = ServerUtil.colorize(UltimateAntiBotBungeeCord.getInstance().getAntiBotManager().replaceInfo(title));
        String formattedSubtitle = ServerUtil.colorize(UltimateAntiBotBungeeCord.getInstance().getAntiBotManager().replaceInfo(subtitle));
        t.title(new TextComponent(formattedTitle));
        t.subTitle(new TextComponent(formattedSubtitle));
        t.stay(20);
        t.fadeIn(0);
        t.fadeOut(0);
        titles.forEach(t::send);
    }

    @Override
    public void sendBossBarMessage(String str, float health) {
        bar.updateBossBar(ServerUtil.colorize(str));
        bar.updateProgress(health);
    }

    public void init(IAntiBotPlugin plugin) {
        plugin.scheduleRepeatingTask(() -> {
            if (titles.isEmpty() && actionbars.isEmpty()) {
                return;
            }
            if(plugin.getAntiBotManager().isSomeModeOnline()) {
                String rawTitle = plugin.getAntiBotManager().replaceInfo(MessageManager.titleTitle);
                String rawSubtitle = plugin.getAntiBotManager().replaceInfo(MessageManager.titleSubtitle);
                if (!rawTitle.equals(lastTitleRaw) || !rawSubtitle.equals(lastSubtitleRaw)) {
                    lastTitleRaw = rawTitle;
                    lastSubtitleRaw = rawSubtitle;
                    sendTitle(MessageManager.titleTitle, plugin.getAntiBotManager().replaceInfo(MessageManager.titleSubtitle));
                }
            }
            if(plugin.getAntiBotManager().isPacketModeEnabled()) {
                String raw = plugin.getAntiBotManager().replaceInfo(MessageManager.actionbarPackets);
                if (!raw.equals(lastActionbarRaw)) {
                    lastActionbarRaw = raw;
                    String msg = ServerUtil.colorize(raw);
                    sendActionbar(msg);
                }
                return;
            }
            if(plugin.getAntiBotManager().isSomeModeOnline()) {
                String raw = plugin.getAntiBotManager().replaceInfo(MessageManager.actionbarAntiBotMode);
                if (!raw.equals(lastActionbarRaw)) {
                    lastActionbarRaw = raw;
                    String msg = ServerUtil.colorize(raw);
                    sendActionbar(msg);
                }
            }else{
                String raw = plugin.getAntiBotManager().replaceInfo(MessageManager.actionbarOffline);
                if (!raw.equals(lastActionbarRaw)) {
                    lastActionbarRaw = raw;
                    String msg = ServerUtil.colorize(raw);
                    sendActionbar(msg);
                }
            }
        }, false, 125L);
    }
}
