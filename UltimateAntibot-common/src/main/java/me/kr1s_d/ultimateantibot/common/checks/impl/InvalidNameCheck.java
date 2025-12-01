package me.kr1s_d.ultimateantibot.common.checks.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final Set<String> invalidNames;
    private final List<Pattern> compiledPatterns;
    private static final int MIN_TOKEN_LENGTH = 4;
    private static final long GRACE_PERIOD_SECONDS = ConfigManger.invalidNameGraceSeconds;
    
    public InvalidNameCheck(IAntiBotPlugin plugin) {
        this.plugin = plugin;
        this.antiBotManager = plugin.getAntiBotManager();
        this.invalidNames = new HashSet<>();
        this.compiledPatterns = new ArrayList<>();

        for (String invalidNamesBlockedEntry : ConfigManger.invalidNamesBlockedEntries) {
            try {
                if(invalidNamesBlockedEntry.startsWith("REGEX-")) {
                    String regexFormula = invalidNamesBlockedEntry.split("-", 2)[1];
                    compiledPatterns.add(Pattern.compile(regexFormula, Pattern.CASE_INSENSITIVE));
                    plugin.getLogHelper().debug("[REGEX VALIDATOR] Input: " + regexFormula + " complete array: " + Arrays.toString(invalidNamesBlockedEntry.split("-", 2)));
                }else{
                    String token = invalidNamesBlockedEntry.toLowerCase();
                    if(token.length() >= MIN_TOKEN_LENGTH) {
                        invalidNames.add(token);
                    }
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
        int hits = 0;
        for(String token : invalidNames){
            if(nameLower.equals(token) || nameLower.startsWith(token) || nameLower.endsWith(token)) {
                hits++;
            } else {
                int idx = nameLower.indexOf(token);
                if(idx != -1) {
                    boolean boundaryLeft = idx == 0 || !Character.isLetterOrDigit(nameLower.charAt(idx - 1));
                    int end = idx + token.length();
                    boolean boundaryRight = end >= nameLower.length() || !Character.isLetterOrDigit(nameLower.charAt(end));
                    if(boundaryLeft && boundaryRight) hits++;
                }
            }
            if(hits >= 2) break;
        }

        boolean regexHit = false;
        if(hits == 0) {
            for (Pattern pattern : compiledPatterns) {
                try {
                    Matcher matcher = pattern.matcher(name);
                    if(matcher.matches()) {
                        regexHit = true;
                        break;
                    }
                }catch (Exception ignored){
                }
            }
        }

        if(hits > 0 || regexHit) {
            long firstJoinAgo = plugin.getUserDataService().getProfile(ip).getSecondsFromFirstJoin();
            if(firstJoinAgo < GRACE_PERIOD_SECONDS) {
                plugin.getUserDataService().getProfile(ip).process(me.kr1s_d.ultimateantibot.common.objects.profile.meta.ScoreTracker.ScoreID.ABNORMAL_NAME);
                return false;
            }
            if(hits + (regexHit ? 1 : 0) >= 2) {
                antiBotManager.getBlackListService().blacklist(ip, BlackListReason.STRANGE_PLAYER_INVALID_NAME, name);
                plugin.getLogHelper().debug("[UAB DEBUG] Detected attack on InvalidNameCheck (multi-hit)");
                return true;
            }
            plugin.getUserDataService().getProfile(ip).process(me.kr1s_d.ultimateantibot.common.objects.profile.meta.ScoreTracker.ScoreID.ABNORMAL_NAME);
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
