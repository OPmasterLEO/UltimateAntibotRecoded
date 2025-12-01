package me.kr1s_d.ultimateantibot.commands;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import me.kr1s_d.commandframework.objects.SubCommand;
import me.kr1s_d.ultimateantibot.common.IAntiBotManager;
import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.objects.profile.BlackListReason;
import me.kr1s_d.ultimateantibot.common.objects.profile.ConnectionProfile;
import me.kr1s_d.ultimateantibot.common.objects.profile.mapping.IPMapping;
import me.kr1s_d.ultimateantibot.common.utils.MessageManager;
import me.kr1s_d.ultimateantibot.common.utils.StringUtil;
import me.kr1s_d.ultimateantibot.utils.Utils;

public class AddRemoveBlacklistCommand implements SubCommand {
    private final IAntiBotPlugin plugin;
    private final IAntiBotManager iAntiBotManager;

    public AddRemoveBlacklistCommand(IAntiBotPlugin iAntiBotPlugin) {
        this.plugin = iAntiBotPlugin;
        this.iAntiBotManager = iAntiBotPlugin.getAntiBotManager();
    }

    @Override
    public String getSubCommandId() {
        return "blacklist";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        final String input = args[2];
        String resolvedIP = null;
        String resolutionMethod = null;
        List<String> affectedAccounts = new java.util.ArrayList<>();
        if (StringUtil.isValidIPv4(input)) {
            resolvedIP = input.replace("/", "");
            resolutionMethod = "direct IP";
        } else {
            final String usernameLower = input.toLowerCase();
            me.kr1s_d.ultimateantibot.common.objects.profile.BlackListProfile profileByID = 
                iAntiBotManager.getBlackListService().getBlacklistProfileFromID(input);
            if (profileByID != null) {
                resolvedIP = profileByID.getIp().replace("/", "");
                resolutionMethod = "blacklist ID: " + profileByID.getId();
            }
            
            if (resolvedIP == null) {
                IPMapping ipMapping = iAntiBotManager.getBlackListService().getIPMapping();
                String ipFromMapping = ipMapping.getIPFromName(input);
                if (ipFromMapping != null) {
                    resolvedIP = ipFromMapping.replace("/", "");
                    resolutionMethod = "historical mapping";
                }
            }
            
            if (resolvedIP == null && plugin instanceof me.kr1s_d.ultimateantibot.UltimateAntiBotSpigot) {
                me.kr1s_d.ultimateantibot.UltimateAntiBotSpigot spigotPlugin = (me.kr1s_d.ultimateantibot.UltimateAntiBotSpigot) plugin;
                org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayerExact(input);
                if (onlinePlayer != null && onlinePlayer.getAddress() != null) {
                    resolvedIP = onlinePlayer.getAddress().getAddress().getHostAddress();
                    resolutionMethod = "online player";
                }
            }
            
            if (resolvedIP == null) {
                ConnectionProfile connectionProfile = plugin.getUserDataService().getConnectedProfiles().stream()
                        .filter(s -> s.getCurrentNickName().equalsIgnoreCase(usernameLower))
                        .findAny()
                        .orElse(null);
                if (connectionProfile != null) {
                    resolvedIP = connectionProfile.getIP().replace("/", "");
                    resolutionMethod = "connection profile";
                }
            }
        }

        if (resolvedIP == null || !StringUtil.isValidIPv4(resolvedIP)) {
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&cFailed to resolve '" + input + "' to a valid IP address."));
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&7Tried: blacklist ID, IPMapping, online players, connection profiles."));
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&7Use a valid IP, ID, or player name."));
            return;
        }

        if (plugin instanceof me.kr1s_d.ultimateantibot.UltimateAntiBotSpigot) {
            org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayerExact(input);
            if (onlinePlayer != null && onlinePlayer.getAddress() != null) {
                String playerIP = onlinePlayer.getAddress().getAddress().getHostAddress();
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p.getAddress() != null && p.getAddress().getAddress().getHostAddress().equals(playerIP)) {
                        affectedAccounts.add(p.getName());
                    }
                }
            }
        }

        final String finalIP = resolvedIP;
        if (args[1].equalsIgnoreCase("add")) {
            iAntiBotManager.getBlackListService().blacklist("/" + finalIP, BlackListReason.ADMIN, 
                plugin.getUserDataService().getProfile("/" + finalIP).getCurrentNickName());
            iAntiBotManager.getWhitelistService().unWhitelist("/" + finalIP);
            plugin.disconnect("/" + finalIP, MessageManager.getBlacklistedMessage(
                iAntiBotManager.getBlackListService().getProfile("/" + finalIP)));
            
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&aBlacklisted IP: &f" + finalIP));
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&7Resolution: &f" + resolutionMethod));
            if (!affectedAccounts.isEmpty()) {
                sender.sendMessage(Utils.colora(MessageManager.prefix + "&7Affected accounts (&c" + affectedAccounts.size() + "&7): &f" + String.join(", ", affectedAccounts)));
            }
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&7IP removed from whitelist."));
        } else if (args[1].equalsIgnoreCase("remove")) {
            iAntiBotManager.getBlackListService().unBlacklist("/" + finalIP);
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&aRemoved from blacklist: &f" + finalIP));
            sender.sendMessage(Utils.colora(MessageManager.prefix + "&7Resolution: &f" + resolutionMethod));
        } else {
            sender.sendMessage(Utils.colora(MessageManager.prefix + MessageManager.commandWrongArgument));
        }
    }

    @Override
    public String getPermission() {
        return "uab.command.blacklist";
    }

    @Override
    public int minArgs() {
        return 3;
    }

    @Override
    public Map<Integer, List<String>> getTabCompleter(CommandSender commandSender, Command command, String s, String[] strings) {
        Map<Integer, List<String>> map = new HashMap<>();
        map.put(1, Arrays.asList("add", "remove"));
        List<String> dyn = new java.util.ArrayList<>();
        for(org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            dyn.add(p.getName());
            if(p.getAddress()!=null) dyn.add(p.getAddress().getAddress().getHostAddress());
        }
        for(String ip : iAntiBotManager.getBlackListService().getBlackListedIPS()) {
            me.kr1s_d.ultimateantibot.common.objects.profile.BlackListProfile pr = iAntiBotManager.getBlackListService().getProfile(ip);
            if(pr!=null) dyn.add(pr.getId());
            dyn.add(ip.replace("/", ""));
        }
        if(dyn.isEmpty()) dyn.add("<IP/Player/ID>");
        map.put(2, dyn);
        return map;
    }

    @Override
    public boolean allowedConsole() {
        return true;
    }
}
