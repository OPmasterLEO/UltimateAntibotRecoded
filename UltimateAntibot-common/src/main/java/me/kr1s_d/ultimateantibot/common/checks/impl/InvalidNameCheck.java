package me.kr1s_d.ultimateantibot.common.checks.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.kr1s_d.ultimateantibot.common.IAntiBotManager;
import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.checks.CheckType;
import me.kr1s_d.ultimateantibot.common.checks.JoinCheck;
import me.kr1s_d.ultimateantibot.common.objects.profile.BlackListReason;
import me.kr1s_d.ultimateantibot.common.service.CheckService;
import me.kr1s_d.ultimateantibot.common.utils.ConfigManger;

public class InvalidNameCheck implements JoinCheck {
    private final IAntiBotPlugin plugin;
    private final IAntiBotManager antiBotManager;
    private final List<String> invalidNames;
    private final List<Pattern> compiledPatterns;
    
    public InvalidNameCheck(IAntiBotPlugin plugin) {
        this.plugin = plugin;
        this.antiBotManager = plugin.getAntiBotManager();
        this.invalidNames = new ArrayList<>();
        this.compiledPatterns = new ArrayList<>();

        for (String invalidNamesBlockedEntry : ConfigManger.invalidNamesBlockedEntries) {
            try {
                if(invalidNamesBlockedEntry.startsWith("REGEX-")) {
                    String regexFormula = invalidNamesBlockedEntry.split("-", 2)[1];
                    compiledPatterns.add(Pattern.compile(regexFormula, Pattern.CASE_INSENSITIVE));
                    plugin.getLogHelper().debug("[REGEX VALIDATOR] Input: " + regexFormula + " complete array: " + Arrays.toString(invalidNamesBlockedEntry.split("-", 2)));
                }else{
                    invalidNames.add(invalidNamesBlockedEntry.toLowerCase());
                }
            }catch (Exception e) {
                plugin.getLogHelper().error("Unable to validate regex for input " + invalidNamesBlockedEntry);
                if(ConfigManger.isDebugModeOnline) e.printStackTrace();
            }

        }

        if(isEnabled()) {
            loadTask();
            CheckService.register(this);
            plugin.getLogHelper().debug("Loaded " + this.getClass().getSimpleName() + "!");
        }
    }

    @Override
    public boolean isDenied(String ip, String name) {
        String nameLower = name.toLowerCase();
        for(String blacklisted : invalidNames){
            if(nameLower.contains(blacklisted)) {
                antiBotManager.getBlackListService().blacklist(ip, BlackListReason.STRANGE_PLAYER_INVALID_NAME, name);
                plugin.getLogHelper().debug("[UAB DEBUG] Detected attack on InvalidNameCheck! (name)");
                return true;
            }
        }

        for (Pattern pattern : compiledPatterns) {
            try {
                Matcher matcher = pattern.matcher(name);

                if(matcher.matches()) {
                    antiBotManager.getBlackListService().blacklist(ip, BlackListReason.STRANGE_PLAYER_INVALID_NAME, name);
                    plugin.getLogHelper().debug("[UAB DEBUG] Detected attack on InvalidNameCheck! (regex)");
                    return true;
                }
            }catch (Exception e){
                plugin.getLogHelper().error("Unable to validate regex for pattern " + pattern.pattern());
            }
        }
        
        return false;
    }

    @Override
    public CheckType getType() {
        return CheckType.BLACKLISTED_NAME;
    }

    @Override
    public void onDisconnect(String ip, String name) {

    }

    @Override
    public boolean isEnabled() {
        return ConfigManger.isInvalidNameCheckEnabled;
    }

    @Override
    public long getCacheSize() {
        return -1;
    }

    @Override
    public void clearCache() {
        //NOT SUPPORTED HERE
    }

    @Override
    public void removeCache(String ip) {
        //NOT SUPPORTED HERE
    }

    public void loadTask() {
        //USELESS
    }
}
