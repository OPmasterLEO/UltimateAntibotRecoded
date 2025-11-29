package me.kr1s_d.ultimateantibot.common.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;
import me.kr1s_d.ultimateantibot.common.IService;
import me.kr1s_d.ultimateantibot.common.UnderAttackMethod;
import me.kr1s_d.ultimateantibot.common.checks.CheckType;
import me.kr1s_d.ultimateantibot.common.checks.impl.ConnectionAnalyzerCheck;
import me.kr1s_d.ultimateantibot.common.helper.LogHelper;
import me.kr1s_d.ultimateantibot.common.objects.profile.BlackListReason;
import me.kr1s_d.ultimateantibot.common.objects.profile.ConnectionProfile;
import me.kr1s_d.ultimateantibot.common.objects.profile.meta.ScoreTracker;
import me.kr1s_d.ultimateantibot.common.utils.ConfigManger;
import me.kr1s_d.ultimateantibot.common.utils.FileUtil;
import me.kr1s_d.ultimateantibot.common.utils.MessageManager;
import me.kr1s_d.ultimateantibot.common.utils.SerializeUtil;

public class UserDataService implements IService {
    private final IAntiBotPlugin plugin;
    private final LogHelper logHelper;
    private Cache<String, ConnectionProfile> profiles;
    private final List<ConnectionProfile> onlineProfiles;

    public UserDataService(IAntiBotPlugin plugin) {
        this.plugin = plugin;
        this.logHelper = plugin.getLogHelper();
        this.profiles = Caffeine.newBuilder()
            .build();
        this.onlineProfiles = new CopyOnWriteArrayList<>();
    }

    @Override
    public void load() {
        try {
            String encodedConnections = FileUtil.getEncodedBase64("profiles.dat", FileUtil.UABFolder.DATA);
            if (encodedConnections != null) {
                List<ConnectionProfile> serialized = SerializeUtil.deserialize(encodedConnections, ArrayList.class);
                if (serialized != null) {
                    for (ConnectionProfile profile : serialized) {
                        if(profile == null || profile.isNull()) continue;
                        profiles.put(profile.getIP(), profile);
                    }
                }
            }

            int count = 0;
            for (Map.Entry<String, ConnectionProfile> map : profiles.asMap().entrySet()) {
                if(map.getValue().getDaysFromLastJoin() >= 30) {
                    profiles.invalidate(map.getKey());
                    count++;
                }
            }

            profiles.cleanUp();
            plugin.getLogHelper().info("&c" + profiles.estimatedSize() + " &fconnection profiles loaded!");
            plugin.getLogHelper().info("&c" + count + " &fconnection profiles removed for inactivity!");
        } catch (Exception e) {
            FileUtil.renameFile("profiles.dat", FileUtil.UABFolder.DATA, "corrupted-old.dat");
            logHelper.error("Unable to load serialized files! If error persists contact support please!");
        }
    }

    @Override
    public void unload() {
        List<ConnectionProfile> profiles = new ArrayList<>();
        this.profiles.asMap().forEach((key, value) -> {
            value.checkMetadata();
            profiles.add(value);
        });
        FileUtil.writeBase64("profiles.dat", FileUtil.UABFolder.DATA, profiles);
    }

    public void registerJoin(String ip, String nickname) {
        ConnectionProfile profile = getProfile(ip);
        profile.trackJoin(nickname);
        onlineProfiles.add(profile);
        profile.process(ScoreTracker.ScoreID.JOIN_NO_PING);
        CheckService.getCheck(CheckType.CONNECTION_ANALYZE, ConnectionAnalyzerCheck.class).checkJoined();
    }

    public void registerQuit(String ip) {
        ConnectionProfile profile = profiles.getIfPresent(ip);
        if(profile == null) return;
        profile.trackDisconnect();
        onlineProfiles.remove(profile);
    }

    @UnderAttackMethod
    public void resetFirstJoin(String ip) {
        ConnectionProfile profile = profiles.getIfPresent(ip);
        if(profile == null) return;
        profile.setFirstJoin(true);
    }

    @UnderAttackMethod
    public boolean isFirstJoin(String ip, String nickname) {
        ConnectionProfile profile = getProfile(ip);
        if(profile == null) return true;
        if(profile.isFirstJoin()) {
            profile.process(ScoreTracker.ScoreID.IS_FIST_JOIN, true);
            profile.setFirstJoin(false);
            return true;
        }

        return false;
    }

    @UnderAttackMethod
    public ConnectionProfile getProfile(String ip) {
        return profiles.get(ip, k -> new ConnectionProfile(ip));
    }

    public void tickProfiles() {
        getConnectedProfiles().forEach(ConnectionProfile::tickMinute);
    }

    public void checkBots() {
        if(!ConfigManger.isConnectionAnalyzeEnabled) {
            return;
        }

        int need = ConfigManger.connectionAnalyzeBlacklistTrigger;
        int ordinal = ConfigManger.connectionAnalyzeBlacklistFrom.ordinal();
        int cnt = 0;
        for (ConnectionProfile p : onlineProfiles) {
            if (p.getConnectionScore().ordinal() >= ordinal) {
                if (++cnt >= need) {
                    disconnectProfiles(ConfigManger.connectionAnalyzeBlacklistFrom, true);
                    plugin.getAntiBotManager().enableSlowAntiBotMode();
                    return;
                }
            }
        }
    }

    public int size() {
        return (int) profiles.estimatedSize();
    }

    @UnderAttackMethod
    public List<ConnectionProfile> getConnectedProfilesByScore(ConnectionProfile.ConnectionScore score) {
        int ordinal = score.ordinal();
        List<ConnectionProfile> result = new ArrayList<>();
        for (ConnectionProfile p : onlineProfiles) {
            if (p.getConnectionScore().ordinal() >= ordinal) result.add(p);
        }
        return result;
    }

    @UnderAttackMethod
    public void disconnectProfiles(ConnectionProfile.ConnectionScore score, boolean blacklist) {
        int ordinal = score.ordinal();
        for (ConnectionProfile profile : onlineProfiles) {
            if (profile.getConnectionScore().ordinal() >= ordinal) {
                profile.setFirstJoin(true);
                plugin.disconnect(profile.getIP(), MessageManager.getSafeModeMessage());
                if (blacklist) {
                    plugin.getAntiBotManager().getBlackListService().blacklist(profile.getIP(), BlackListReason.STRANGE_PLAYER_CONNECTION, profile.getCurrentNickName());
                }
            }
        }
    }

    public List<ConnectionProfile> getProfiles() {
        List<ConnectionProfile> profiles = new ArrayList<>();
        this.profiles.asMap().forEach((key, value) -> profiles.add(value));
        return profiles;
    }

    @UnderAttackMethod
    public List<ConnectionProfile> getLastJoinedAndConnectedProfiles(int minutes) {
        long limit = TimeUnit.MINUTES.toSeconds(minutes);
        List<ConnectionProfile> result = new ArrayList<>();
        for (ConnectionProfile p : onlineProfiles) {
            if (p.getSecondsFromLastJoin() <= limit) result.add(p);
        }
        return result;
    }

    @UnderAttackMethod
    public List<ConnectionProfile> getConnectedProfiles() {
        return onlineProfiles;
    }
}
