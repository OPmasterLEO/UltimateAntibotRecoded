package me.kr1s_d.ultimateantibot;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;

import io.netty.channel.Channel;
import me.kr1s_d.commandframework.CommandManager;
import me.kr1s_d.ultimateantibot.commands.AddRemoveBlacklistCommand;
import me.kr1s_d.ultimateantibot.commands.AddRemoveWhitelistCommand;
import me.kr1s_d.ultimateantibot.commands.AttackLogCommand;
import me.kr1s_d.ultimateantibot.commands.CacheCommand;
import me.kr1s_d.ultimateantibot.commands.CheckIDCommand;
import me.kr1s_d.ultimateantibot.commands.ClearCommand;
import me.kr1s_d.ultimateantibot.commands.ConnectionProfileCommand;
import me.kr1s_d.ultimateantibot.commands.DumpCommand;
import me.kr1s_d.ultimateantibot.commands.FirewallCommand;
import me.kr1s_d.ultimateantibot.commands.HelpCommand;
import me.kr1s_d.ultimateantibot.commands.ReloadCommand;
import me.kr1s_d.ultimateantibot.commands.StatsCommand;
import me.kr1s_d.ultimateantibot.commands.ToggleNotificationCommand;
import me.kr1s_d.ultimateantibot.common.IAntiBotManager;
import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.IConfiguration;
import me.kr1s_d.ultimateantibot.common.INotificator;
import me.kr1s_d.ultimateantibot.common.IServerPlatform;
import me.kr1s_d.ultimateantibot.common.UABRunnable;
import me.kr1s_d.ultimateantibot.common.core.UltimateAntiBotCore;
import me.kr1s_d.ultimateantibot.common.core.server.SatelliteServer;
import me.kr1s_d.ultimateantibot.common.core.thread.AnimationThread;
import me.kr1s_d.ultimateantibot.common.core.thread.AttackAnalyzerThread;
import me.kr1s_d.ultimateantibot.common.core.thread.LatencyThread;
import me.kr1s_d.ultimateantibot.common.helper.LogHelper;
import me.kr1s_d.ultimateantibot.common.helper.PerformanceHelper;
import me.kr1s_d.ultimateantibot.common.helper.ServerType;
import me.kr1s_d.ultimateantibot.common.service.AttackTrackerService;
import me.kr1s_d.ultimateantibot.common.service.FirewallService;
import me.kr1s_d.ultimateantibot.common.service.UserDataService;
import me.kr1s_d.ultimateantibot.common.service.VPNService;
import me.kr1s_d.ultimateantibot.common.utils.ConfigManger;
import me.kr1s_d.ultimateantibot.common.utils.FilesUpdater;
import me.kr1s_d.ultimateantibot.common.utils.MessageManager;
import me.kr1s_d.ultimateantibot.common.utils.ServerUtil;
import me.kr1s_d.ultimateantibot.common.utils.Updater;
import me.kr1s_d.ultimateantibot.listener.CustomEventListener;
import me.kr1s_d.ultimateantibot.listener.MainEventListener;
import me.kr1s_d.ultimateantibot.listener.PingListener;
import me.kr1s_d.ultimateantibot.netty.ChannelInjectorSpigot;
import me.kr1s_d.ultimateantibot.netty.PacketInspectorHandler;
import me.kr1s_d.ultimateantibot.objects.Config;
import me.kr1s_d.ultimateantibot.objects.filter.Bukkit247Filter;
import me.kr1s_d.ultimateantibot.objects.filter.BukkitAttackFilter;
import me.kr1s_d.ultimateantibot.utils.Metrics;
import me.kr1s_d.ultimateantibot.utils.Utils;
import me.kr1s_d.ultimateantibot.utils.Version;

public final class UltimateAntiBotSpigot extends JavaPlugin implements IAntiBotPlugin, IServerPlatform {

    private static UltimateAntiBotSpigot instance;
    private BukkitScheduler scheduler;
    private IConfiguration config;
    private IConfiguration messages;
    private IConfiguration whitelist;
    private IConfiguration blacklist;
    private IAntiBotManager antiBotManager;
    private LatencyThread latencyThread;
    private AnimationThread animationThread;
    private LogHelper logHelper;
    private FirewallService firewallService;
    private UserDataService userDataService;
    private AttackTrackerService attackTrackerService;
    private VPNService VPNService;
    private Notificator notificator;
    private UltimateAntiBotCore core;
    private SatelliteServer satellite;
    private boolean isRunning;
    private static TaskScheduler universalScheduler;

    @Override
    public void onEnable() {
        long a = System.currentTimeMillis();
        instance = this;
        this.isRunning = true;
        PerformanceHelper.init(ServerType.SPIGOT);
        ServerUtil.setInstance(this);
        this.scheduler = Bukkit.getScheduler();
        try {
            universalScheduler = UniversalScheduler.getScheduler(this);
        } catch (Throwable t) {
            universalScheduler = null;
        }

        this.config = new Config(this, "config");
        this.messages = new Config(this, "messages");
        this.whitelist = new Config(this, "whitelist");
        this.blacklist = new Config(this, "blacklist");
        this.logHelper = new LogHelper(this);
        FilesUpdater updater = new FilesUpdater(this, config, messages, whitelist, blacklist);
        updater.check(4.4, 4.4);
        if (updater.requiresReassign()) {
            this.config = new Config(this, "config");
            this.messages = new Config(this, "messages");
            this.whitelist = new Config(this, "whitelist");
            this.blacklist = new Config(this, "blacklist");
        }
        try {
            ConfigManger.init(config, false);
            PerformanceHelper.init(ServerType.SPIGOT);
            MessageManager.init(messages);
        } catch (Exception e) {
            logHelper.error("[ERROR] Error during config.yml & messages.yml loading!");
            e.printStackTrace();
            throw e;
        }
        Version.init(this);
        new Metrics(this, 11777);
        logHelper.info("&fLoading &cUltimateAntiBot...");
        this.firewallService = new FirewallService(this);
        VPNService = new VPNService(this);
        VPNService.load();
        antiBotManager = new AntiBotManager(this);
        antiBotManager.getQueueService().load();
        antiBotManager.getWhitelistService().load();
        antiBotManager.getBlackListService().load();
        this.attackTrackerService = new AttackTrackerService(this);
        attackTrackerService.load();
        firewallService.enable();
        latencyThread = new LatencyThread(this);
        animationThread = new AnimationThread(this);
        userDataService = new UserDataService(this);
        userDataService.load();
        this.core = new UltimateAntiBotCore(this);
        this.core.load();
        ((Logger) LogManager.getRootLogger()).addFilter(new BukkitAttackFilter(this));
        ((Logger) LogManager.getRootLogger()).addFilter(new Bukkit247Filter(this));
        notificator = new Notificator();
        notificator.init(this);
        new AttackAnalyzerThread(this);
        logHelper.info("&fLoaded &cUltimateAntiBot!");
        logHelper.sendLogo();
        logHelper.info("&cVersion: &f$1 &4| &cAuthor: &f$2 &4| &cCores: &f$3 &4| &cMode: &f$4"
                .replace("$1", this.getDescription().getVersion())
                .replace("$2", this.getDescription().getAuthors().toString())
                .replace("$3", String.valueOf(PerformanceHelper.getCores()))
                .replace("$4", String.valueOf(PerformanceHelper.get()))
        );
        logHelper.info("&fThe &cabyss&f is ready to swallow all the bots!");
        CommandManager commandManager = new CommandManager("ultimateantibot", "", "ab", "uab");
        commandManager.setPrefix(MessageManager.prefix);
        commandManager.register(new AddRemoveBlacklistCommand(this));
        commandManager.register(new AddRemoveWhitelistCommand(this));
        commandManager.register(new ClearCommand(this));
        commandManager.register(new DumpCommand(this));
        commandManager.register(new HelpCommand(this));
        commandManager.register(new StatsCommand(this));
        commandManager.register(new ToggleNotificationCommand());
        commandManager.register(new CheckIDCommand(this));
        commandManager.register(new ReloadCommand(this));
        commandManager.register(new FirewallCommand(this));
        commandManager.register(new AttackLogCommand(this));
        commandManager.register(new CacheCommand());
        commandManager.register(new ConnectionProfileCommand(this));
        commandManager.setWrongArgumentMessage(MessageManager.commandWrongArgument);
        commandManager.setNoPlayerMessage("&fYou are not a &cplayer!");
        Bukkit.getPluginManager().registerEvents(new PingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MainEventListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CustomEventListener(this), this);
        NettyConnectionController netController = new NettyConnectionController();
        PacketAntibotManager packetManager = new PacketAntibotManager(this, netController);
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            try {
                ChannelInjectorSpigot.injectForPlayer(p, this, new PacketInspectorHandler(p, packetManager, netController));
            } catch (Throwable ignored) {}
        }
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                ChannelInjectorSpigot.injectForPlayer(e.getPlayer(), UltimateAntiBotSpigot.this, new PacketInspectorHandler(e.getPlayer(), packetManager, netController));
            }
        }, this);
        long b = System.currentTimeMillis() - a;
        logHelper.info("&7Took &c" + b + "ms&7 to load");
        new Updater(this);
    }

    @Override
    public void onDisable() {
        long a = System.currentTimeMillis();
        logHelper.info("&cUnloading...");
        this.isRunning = false;
        if(attackTrackerService != null) attackTrackerService.unload();
        if(firewallService != null) firewallService.shutDownFirewall();
        if(userDataService != null) userDataService.unload();
        if(VPNService != null) VPNService.unload();
        if(antiBotManager != null){
            antiBotManager.getBlackListService().unload();
            antiBotManager.getWhitelistService().unload();
        }
        logHelper.info("&cThanks for choosing us!");
        long b = System.currentTimeMillis() - a;
        logHelper.info("&7Took &c" + b + "ms&7 to unload");
    }

    /**
     * Simple `ConnectionController` implementation that maps connection ids to online players.
     * This is intentionally minimal: Netty-backed controller can replace it for real packet sends.
     */
    private class SpigotConnectionController implements ConnectionController {
        @Override
        public List<String> listConnectionIds() {
            List<String> ids = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                InetSocketAddress a = p.getAddress();
                if (a == null) continue;
                ids.add(a.getAddress().getHostAddress() + ":" + a.getPort());
            }
            return ids;
        }

        @Override
        public InetSocketAddress getAddress(String connId) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                InetSocketAddress a = p.getAddress();
                if (a == null) continue;
                String id = a.getAddress().getHostAddress() + ":" + a.getPort();
                if (id.equals(connId)) return a;
            }
            return null;
        }

        @Override
        public boolean hasPlayer(String connId) {
            return getAddress(connId) != null;
        }

        @Override
        public boolean sendKeepAlive(String connId, long id) {
            // Not implemented in this simple controller. Netty-backed controller should implement.
            return false;
        }

        @Override
        public void closeConnection(String connId) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                InetSocketAddress a = p.getAddress();
                if (a == null) continue;
                String id = a.getAddress().getHostAddress() + ":" + a.getPort();
                if (id.equals(connId)) {
                    try {
                        p.kickPlayer(Utils.colora("&cConnection closed by UltimateAntiBot"));
                    } catch (Throwable ignored) {}
                    return;
                }
            }
        }

        @Override
        public void registerChannel(Channel channel, Player player) {
        }

        @Override
        public void unregisterChannel(Channel channel) {
        }

        @Override
        public String getConnectionId(Channel channel) {
            if (channel == null) return null;
            try {
                Object addr = channel.remoteAddress();
                if (addr instanceof InetSocketAddress) {
                    InetSocketAddress a = (InetSocketAddress) addr;
                    return a.getAddress().getHostAddress() + ":" + a.getPort();
                }
            } catch (Throwable ignored) {}
            return null;
        }
    }

    private long msToTicks(long ms){
        long t = ms / 50L;
        return t <= 0 ? 1 : t;
    }

    @Override
    public void reload() {
        this.config = new Config(this, "config");
        this.messages = new Config(this, "messages");

        ConfigManger.init(config, true);
        MessageManager.init(messages);
    }

    @Override
    public void runTask(Runnable task, boolean isAsync) {
        if (universalScheduler != null) {
            universalScheduler.runTask(task);
            return;
        }
        if (isAsync) {
            scheduler.runTaskAsynchronously(this, task);
        } else {
            scheduler.runTask(this, task);
        }
    }

    @Override
    public void runTask(UABRunnable runnable) {
        if (universalScheduler != null) {
            universalScheduler.runTask(runnable);
            runnable.setTaskID(-1);
            return;
        }
        // Bukkit fallback
        BukkitTask bukkitTask = runnable.isAsync() ?
                scheduler.runTaskAsynchronously(this, runnable) :
                scheduler.runTask(this, runnable);
        runnable.setTaskID(bukkitTask.getTaskId());
    }

    @Override
    public void scheduleDelayedTask(Runnable runnable, boolean async, long milliseconds) {
        if (universalScheduler != null) {
            universalScheduler.runTaskLater(runnable, msToTicks(milliseconds));
            return;
        }
        if (async) {
            scheduler.runTaskLaterAsynchronously(this, runnable, msToTicks(milliseconds));
        } else {
            scheduler.runTaskLater(this, runnable, msToTicks(milliseconds));
        }
    }

    @Override
    public void scheduleDelayedTask(UABRunnable runnable) {
        long ticks = msToTicks(runnable.getPeriod());
        if (universalScheduler != null) {
            universalScheduler.runTaskLater(runnable, ticks);
            runnable.setTaskID(-1);
            return;
        }
        BukkitTask bukkitTask = runnable.isAsync() ?
                scheduler.runTaskLaterAsynchronously(this, runnable, ticks) :
                scheduler.runTaskLater(this, runnable, ticks);
        runnable.setTaskID(bukkitTask.getTaskId());
    }

    @Override
    public void scheduleRepeatingTask(Runnable runnable, boolean async, long repeatMilliseconds) {
        long periodTicks = msToTicks(repeatMilliseconds);
        if (universalScheduler != null) {
            universalScheduler.runTaskTimer(runnable, 0L, periodTicks);
            return;
        }
        if (async) {
            scheduler.runTaskTimerAsynchronously(this, runnable, 0L, periodTicks);
        } else {
            scheduler.runTaskTimer(this, runnable, 0L, periodTicks);
        }
    }

    @Override
    public void scheduleRepeatingTask(UABRunnable runnable) {
        long periodTicks = msToTicks(runnable.getPeriod());
        if (universalScheduler != null) {
            universalScheduler.runTaskTimer(runnable, 0L, periodTicks);
            runnable.setTaskID(-1);
            return;
        }
        BukkitTask bukkitTask = runnable.isAsync() ?
                scheduler.runTaskTimerAsynchronously(this, runnable, 0L, periodTicks) :
                scheduler.runTaskTimer(this, runnable, 0L, periodTicks);
        runnable.setTaskID(bukkitTask.getTaskId());
    }

    @Override
    public IConfiguration getConfigYml() {
        return config;
    }

    @Override
    public IConfiguration getMessages() {
        return messages;
    }

    @Override
    public IConfiguration getWhitelist() {
        return whitelist;
    }

    @Override
    public IConfiguration getBlackList() {
        return blacklist;
    }

    @Override
    public IAntiBotManager getAntiBotManager() {
        return antiBotManager;
    }

    @Override
    public LatencyThread getLatencyThread() {
        return latencyThread;
    }

    @Override
    public AnimationThread getAnimationThread() {
        return animationThread;
    }

    @Override
    public LogHelper getLogHelper() {
        return logHelper;
    }

    @Override
    public Class<?> getClassInstance() {
        return Bukkit.spigot().getClass();
    }

    @Override
    public UserDataService getUserDataService() {
        return userDataService;
    }

    @Override
    public VPNService getVPNService() {
        return VPNService;
    }

    @Override
    public INotificator getNotificator() {
        return notificator;
    }

    @Override
    public UltimateAntiBotCore getCore() {
        return core;
    }

    @Override
    public FirewallService getFirewallService() {
        return firewallService;
    }

    @Override
    public boolean isConnected(String ip) {
        return Bukkit.getOnlinePlayers().stream().anyMatch(p -> Utils.getPlayerIP(p).equals(ip));
    }

    @Override
    public String getVersion() {
        return this.getDescription().getVersion();
    }

    @Override
    public void disconnect(String ip, String reasonNoColor) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (Utils.getPlayerIP(player).equals(ip)) {
                        player.kickPlayer(Utils.colora(reasonNoColor));
                    }
                }
            }
        }.runTaskLater(this, 1);
    }

    @Override
    public int getOnlineCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public void cancelTask(int id) {
        Bukkit.getScheduler().cancelTask(id);
    }

    @Override
    public void log(LogHelper.LogType type, String log) {
        Bukkit.getConsoleSender().sendMessage(log);
    }

    @Override
    public void broadcast(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Utils.colora(message));
        }
    }

    @Override
    public AttackTrackerService getAttackTrackerService(){
        return attackTrackerService;
    }

    @Override
    public File getDFolder() {
        return getDataFolder();
    }

    public static TaskScheduler getScheduler() {
        return universalScheduler;
    }

    public static UltimateAntiBotSpigot getInstance() {
        return instance;
    }
}
