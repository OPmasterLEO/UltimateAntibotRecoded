package me.kr1s_d.ultimateantibot;

import java.util.ArrayList;
import java.util.List;

import com.velocitypowered.api.proxy.Player;

import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.INotificator;
import me.kr1s_d.ultimateantibot.common.utils.ConfigManger;
import me.kr1s_d.ultimateantibot.common.utils.MessageManager;
import me.kr1s_d.ultimateantibot.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;

public class Notificator implements INotificator {
    private static final List<Player> actionbars = new ArrayList<>();
    private static final List<Player> titles = new ArrayList<>();
    private static final List<Player> bar = new ArrayList<>();
    private static final BossBar BOSS = BossBar.bossBar(Utils.colora("&fWaiting for a new attack!"), 1, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private String lastActionbarRaw;
    private String lastTitleRaw;
    private String lastSubtitleRaw;

    public static void automaticNotification(Player player) {
        if(actionbars.contains(player)) return;

        actionbars.remove(player);

        //bossbar start
        bar.remove(player);
        player.hideBossBar(BOSS);

        if(player.getProtocolVersion().getProtocol() > 106){
            if(bar.contains(player)) {
                return;
            }

            if(ConfigManger.enableBossBarAutomaticNotification){
                bar.add(player);
                player.showBossBar(BOSS);
            }
        }

        actionbars.add(player);
    }

    public static void onQuit(Player player) {
        titles.remove(player);
        bar.remove(player);
        actionbars.remove(player);
    }

    public static void toggleBossBar(Player player){
        if(bar.contains(player)){
            bar.remove(player);
            player.hideBossBar(BOSS);
        }else{
            bar.add(player);
            player.showBossBar(BOSS);
        }
        player.sendMessage(Utils.colora(MessageManager.prefix + MessageManager.toggledBossBar));
    }

    public static void toggleActionBar(Player player){
        if(actionbars.contains(player)) {
            actionbars.remove(player);
        }else {
            actionbars.add(player);
        }
        player.sendMessage(Utils.colora((MessageManager.prefix + MessageManager.toggledActionbar)));
    }

    public static void toggleTitle(Player player) {
        if(titles.contains(player)){
            titles.remove(player);
        }else {
            titles.add(player);
        }
        player.sendMessage(Utils.colora((MessageManager.prefix + MessageManager.toggledTitle)));
    }

    public static void disableAllNotifications() {
        actionbars.clear();
        bar.removeIf(s -> {
            s.hideBossBar(BOSS);
            return true;
        });
        titles.clear();
    }


    public void sendActionbar(net.kyori.adventure.text.Component coloredMessage) {
        for (Player actionbar : actionbars) {
            actionbar.sendActionBar(coloredMessage);
        }
    }
    public void sendActionbar(String str) {
        sendActionbar(Utils.colora(str));
    }

    public void sendTitle(String title, String subtitle) {
    net.kyori.adventure.text.Component formattedTitle = Utils.colora(UltimateAntiBotVelocity.getInstance().getAntiBotManager().replaceInfo(title));
    net.kyori.adventure.text.Component formattedSubtitle = Utils.colora(UltimateAntiBotVelocity.getInstance().getAntiBotManager().replaceInfo(subtitle));
    Title t = Title.title(formattedTitle, formattedSubtitle);

        for (Player player : titles) {
            player.sendTitlePart(TitlePart.TITLE, t.title());
            player.sendTitlePart(TitlePart.SUBTITLE, t.subtitle());
        }
    }

    @Override
    public void sendBossBarMessage(String str, float health) {
        BOSS.name(Utils.colora(str));
        BOSS.progress(health);
    }

    public void init(IAntiBotPlugin plugin){
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
                    net.kyori.adventure.text.Component msg = Utils.colora(raw);
                    sendActionbar(msg);
                }
                return;
            }
            if(plugin.getAntiBotManager().isSomeModeOnline()) {
                String raw = plugin.getAntiBotManager().replaceInfo(MessageManager.actionbarAntiBotMode);
                if (!raw.equals(lastActionbarRaw)) {
                    lastActionbarRaw = raw;
                    net.kyori.adventure.text.Component msg = Utils.colora(raw);
                    sendActionbar(msg);
                }
            }else{
                String raw = plugin.getAntiBotManager().replaceInfo(MessageManager.actionbarOffline);
                if (!raw.equals(lastActionbarRaw)) {
                    lastActionbarRaw = raw;
                    net.kyori.adventure.text.Component msg = Utils.colora(raw);
                    sendActionbar(msg);
                }
            }
        }, false, 125L);
    }
}
