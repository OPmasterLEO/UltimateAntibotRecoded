package me.kr1s_d.ultimateantibot.common.checks.impl;

import java.util.ArrayList;
import java.util.List;

import me.kr1s_d.ultimateantibot.common.IAntiBotManager;
import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.checks.CheckType;
import me.kr1s_d.ultimateantibot.common.checks.StaticCheck;
import me.kr1s_d.ultimateantibot.common.objects.LimitedList;
import me.kr1s_d.ultimateantibot.common.objects.profile.ConnectionProfile;
import me.kr1s_d.ultimateantibot.common.objects.profile.entry.NickNameEntry;
import me.kr1s_d.ultimateantibot.common.objects.profile.meta.MetadataContainer;
import me.kr1s_d.ultimateantibot.common.objects.profile.meta.ScoreTracker;
import me.kr1s_d.ultimateantibot.common.service.CheckService;
import me.kr1s_d.ultimateantibot.common.service.UserDataService;
import me.kr1s_d.ultimateantibot.common.service.WhitelistService;
import me.kr1s_d.ultimateantibot.common.utils.ConfigManger;
import me.kr1s_d.ultimateantibot.common.utils.StringUtil;

/**
 * This is not a real check, it helps detect slow bot attacks and is inserted into different
 * parts of the various events which are reported below (to remind)
 *
 * UserDataService#isFirstJoin
 * PacketCheck
 *
 * Methods will be considered as static, and will be called in the MainEventListener with CheckService
 * Instantiation is executed in the main classes
 */
public class ConnectionAnalyzerCheck implements StaticCheck {
    private IAntiBotPlugin plugin;
    private IAntiBotManager antiBotManager;
    private UserDataService userDataService;
    private WhitelistService whitelistService;
    private MetadataContainer<ConnectionProfile> chatSuspected;

    public ConnectionAnalyzerCheck(IAntiBotPlugin plugin) {
        this.plugin = plugin;
        this.antiBotManager = plugin.getAntiBotManager();
        this.userDataService = plugin.getUserDataService();
        this.whitelistService = antiBotManager.getWhitelistService();
        this.chatSuspected = new MetadataContainer<>(false);

        CheckService.register(this);
        if(isEnabled()) {
            plugin.getLogHelper().debug("Loaded " + this.getClass().getSimpleName() + "!");
        }
    }

    public void checkJoined() {
        List<ConnectionProfile> last = userDataService.getLastJoinedAndConnectedProfiles(15);
        if (last.size() < 3) return;
        List<ConnectionProfile> suspected = new ArrayList<>();

        for (ConnectionProfile profile : last) {
            if (whitelistService.isWhitelisted(profile.getIP())) continue;

            LimitedList<NickNameEntry> nickHistory = profile.getLastNickNames();
            if (nickHistory == null || nickHistory.size() == 0) continue;

            for (NickNameEntry currentNickname : nickHistory) {
                String name1 = currentNickname.getName();
                if (name1 == null || name1.length() < 3) continue; // Skip very short names
                for (ConnectionProfile otherProfile : last) {
                    if (profile == otherProfile) continue;
                    if (whitelistService.isWhitelisted(otherProfile.getIP())) continue;

                    LimitedList<NickNameEntry> otherNickHistory = otherProfile.getLastNickNames();
                    if (otherNickHistory == null || otherNickHistory.size() == 0) continue;

                    for (NickNameEntry otherNickname : otherNickHistory) {
                        String name2 = otherNickname.getName();
                        if (name2 == null || name2.length() < 3) continue;
                        
                        // Length pre-filter: if lengths differ by more than 30%, skip comparison
                        int lenDiff = Math.abs(name1.length() - name2.length());
                        if (lenDiff > name1.length() * 0.3) continue;
                        
                        // Calculate similarity with optimized algorithm
                        if (StringUtil.calculateSimilarity(name1, name2) > 80) {
                            if (!suspected.contains(profile)) suspected.add(profile);
                            if (!suspected.contains(otherProfile)) suspected.add(otherProfile);
                        }
                    }
                }
            }
        }

        if(suspected.size() > ConfigManger.connectionAnalyzeNameTrigger) {
            for (ConnectionProfile profile : suspected) {
                profile.process(ScoreTracker.ScoreID.ABNORMAL_NAME);
            }
        }
    }

    public void onChat(String ip, String nickname, String message) {
        ConnectionProfile profile = userDataService.getProfile(ip);
        if (profile == null) return;
        profile.trackChat(message);
        
        // Optimized chat similarity detection
        List<ConnectionProfile> last = userDataService.getLastJoinedAndConnectedProfiles(15);
        if (last.size() < 2) return; // Need at least 2 profiles

        List<String> entries = new ArrayList<>();
        for (ConnectionProfile p : last) {
            if (p.getIP().equals(ip)) continue;
            if (whitelistService.isWhitelisted(p.getIP())) continue;
            try {
                Object chatMsgsObj = p.getChatMessages();
                if (chatMsgsObj != null) {
                    if (chatMsgsObj instanceof Iterable) {
                        for (Object msgEntry : (Iterable<?>) chatMsgsObj) {
                            try {
                                String msgContent = msgEntry.getClass().getMethod("getMessage").invoke(msgEntry).toString();
                                if (msgContent != null && !msgContent.isEmpty()) {
                                    entries.add(msgContent);
                                }
                            } catch (Exception ignored) {
                                String msgContent = msgEntry.toString();
                                if (msgContent != null && !msgContent.isEmpty()) {
                                    entries.add(msgContent);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        
        if(entries.isEmpty() || entries.size() > 10) return;

        int similarCount = 0;
        for (String entry : entries) {
            // Early skip for small messages or entry
            if (StringUtil.spaces(message) < 2 && message.length() < 5) continue;
            if (StringUtil.spaces(entry) < 2 && entry.length() < 5) continue;
            int lenDiff = Math.abs(message.length() - entry.length());
            if (lenDiff > message.length() * 0.5) continue;
            
            if (StringUtil.calculateSimilarity(message, entry) > 85) {
                similarCount++;
                chatSuspected.incrementInt(profile, 0);
            }
        }
        
        double percent = entries.isEmpty() ? 0.0 : (double) chatSuspected.getOrDefaultNoPut(profile, Integer.class, 0) / ((double) entries.size()) * 100D;

        if (percent >= ConfigManger.connectionAnalyzeChatTrigger) {
            profile.process(ScoreTracker.ScoreID.ABNORMAL_CHAT_MESSAGE);
        }

        plugin.getLogHelper().debug("[CONNECTION ANALYZER] Chat percent for " + nickname + " is " + percent + " (similar: " + similarCount + "/" + entries.size() + ")");
    }

    public void onPing(String ip) {
        userDataService.getProfile(ip).trackPing();
    }

    @Override
    public void onDisconnect(String ip, String name) {
    }

    @Override
    public CheckType getType() {
        return CheckType.CONNECTION_ANALYZE;
    }

    @Override
    public boolean isEnabled() {
        return ConfigManger.isConnectionAnalyzeEnabled;
    }

    @Override
    public long getCacheSize() {
        return chatSuspected.size();
    }

    @Override
    public void clearCache() {
        chatSuspected.clear();
    }

    @Override
    public void removeCache(String ip) {
        chatSuspected.removeIf(i -> i.getIP().equals(ip));
    }
}
