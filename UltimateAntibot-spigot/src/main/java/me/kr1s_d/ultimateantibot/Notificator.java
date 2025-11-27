package me.kr1s_d.ultimateantibot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.entity.Player;

import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.INotificator;
import me.kr1s_d.ultimateantibot.common.utils.ConfigManger;
import me.kr1s_d.ultimateantibot.common.utils.MessageManager;
import me.kr1s_d.ultimateantibot.common.utils.ServerUtil;
import me.kr1s_d.ultimateantibot.utils.KBossBar;
import me.kr1s_d.ultimateantibot.utils.Utils;
import me.kr1s_d.ultimateantibot.utils.Version;

public class Notificator implements INotificator {
    private static final List<Player> actionbars = new CopyOnWriteArrayList<>();
    private static final List<Player> titles = new CopyOnWriteArrayList<>();
    private static final KBossBar bar = new KBossBar();
    private String lastActionbarRaw;
    private String lastTitleRaw;
    private String lastSubtitleRaw;

    public static void automaticNotification(Player player) {
        if(actionbars.contains(player)) return;
        actionbars.remove(player);
        bar.removePlayer(player);
        if (ConfigManger.enableBossBarAutomaticNotification && Version.getBukkitServerVersion() > 19) {
            bar.addPlayer(player);
        }
        actionbars.add(player);
    }

    public static void toggleBossBar(Player player){
        if(!bar.isSupported()) return;
        if(Version.getBukkitServerVersion() < 19) {
            player.sendMessage(Utils.colora(MessageManager.prefix + "&cBossbar&f notifications are not supported on &c1.8.x!"));
            return;
        }
        if(bar.getPlayers().contains(player)){
            bar.removePlayer(player);
        }else{
            bar.addPlayer(player);
        }
        player.sendMessage((ServerUtil.colorize(MessageManager.prefix + MessageManager.toggledBossBar)));
    }

    public static void toggleActionBar(Player player){
        if(actionbars.contains(player)){
            actionbars.remove(player);
        }else {
            actionbars.add(player);
        }
        player.sendMessage(ServerUtil.colorize(MessageManager.prefix + MessageManager.toggledActionbar));
    }

    public static void toggleTitle(Player player){
        if(titles.contains(player)){
            titles.remove(player);
        }else {
            titles.add(player);
        }
        player.sendMessage(ServerUtil.colorize(MessageManager.prefix + MessageManager.toggledTitle));
    }

    public static void onQuit(Player player) {
        titles.remove(player);
        bar.removePlayer(player);
        actionbars.remove(player);
    }

    public static void disableAllNotifications() {
        actionbars.clear();
        bar.getPlayers().forEach(p -> bar.removePlayer(p.getPlayer()));
        titles.clear();
    }

    public void sendActionbar(String coloredMessage){
        actionbars.forEach(ac -> Utils.sendActionbar(ac, coloredMessage));
    }

    public void sendTitle(String title, String subtitle){
        String formattedTitle = UltimateAntiBotSpigot.getInstance().getAntiBotManager().replaceInfo(ServerUtil.colorize(title));
        String formattedSubtitle = UltimateAntiBotSpigot.getInstance().getAntiBotManager().replaceInfo(ServerUtil.colorize(subtitle));
        titles.forEach(t -> t.sendTitle(
                formattedTitle,
                formattedSubtitle,
                0,
                30,
                0
        ));
    }

    @Override
    public void sendBossBarMessage(String str, float health) {
        if(!bar.isSupported()) return;
        bar.setTitle(ServerUtil.colorize(str));
        bar.setProgress(health);
    }

    public void init(IAntiBotPlugin plugin){
        plugin.scheduleRepeatingTask(() -> {
            if (titles.isEmpty() && actionbars.isEmpty() && (!bar.isSupported() || bar.getPlayers().isEmpty())) {
                return;
            }
            if(plugin.getAntiBotManager().isSomeModeOnline()){
                String rawTitle = plugin.getAntiBotManager().replaceInfo(MessageManager.titleTitle);
                String rawSubtitle = plugin.getAntiBotManager().replaceInfo(MessageManager.titleSubtitle);
                if (!rawTitle.equals(lastTitleRaw) || !rawSubtitle.equals(lastSubtitleRaw)) {
                    lastTitleRaw = rawTitle;
                    lastSubtitleRaw = rawSubtitle;
                    sendTitle(MessageManager.titleTitle, plugin.getAntiBotManager().replaceInfo(MessageManager.titleSubtitle));
                }
            }
            if(plugin.getAntiBotManager().isPacketModeEnabled()){
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
