package me.kr1s_d.ultimateantibot.common.objects.profile;

import me.kr1s_d.ultimateantibot.common.utils.MessageManager;

public enum BlackListReason {
    CHECK_FAILS(MessageManager.reasonCheck),
    TOO_MUCH_NAMES(MessageManager.reasonTooManyNicks),
    TOO_MUCH_JOINS(MessageManager.reasonTooManyJoins),
    ADMIN(MessageManager.reasonAdmin),
    @Deprecated
    STRANGE_PLAYER(MessageManager.reasonAdmin),
    STRANGE_PLAYER_INVALID_NAME(MessageManager.reasonStrangePlayer + " [name]"),
    STRANGE_PLAYER_INVALID_PROTOCOL(MessageManager.reasonStrangePlayer + " [protocol]"),
    STRANGE_PLAYER_REGISTER(MessageManager.reasonStrangePlayer + " [register]"),
    STRANGE_PLAYER_PACKET(MessageManager.reasonStrangePlayer + " [packet]"),
    STRANGE_PLAYER_CONNECTION(MessageManager.reasonStrangePlayer + " [connection]"),
    STRANGE_PLAYER_SLOW_JOIN(MessageManager.reasonStrangePlayer + " [slow-join]"),
    VPN(MessageManager.reasonVPN);

    private final String reason;

    BlackListReason(String reason){
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
