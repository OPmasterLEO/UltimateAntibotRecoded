package me.kr1s_d.ultimateantibot.common.core.thread;

import me.kr1s_d.ultimateantibot.common.IAntiBotPlugin;

public class AnimationThread {
    private static final String[] FRAMES = {"▛", "▜", "▟", "▙"};
    private volatile String currentEmote;
    private int counter;

    public AnimationThread(IAntiBotPlugin antiBotPlugin){
        antiBotPlugin.getLogHelper().debug("Enabled AnimationThread!");
        this.counter = 0;
        this.currentEmote = FRAMES[0];
        antiBotPlugin.scheduleRepeatingTask(this::updateEmote, true, 125L);
    }

    private void updateEmote() {
        currentEmote = FRAMES[counter];
        counter = (counter + 1) & 3;
    }


    public String getEmote() {
        return currentEmote;
    }
}
