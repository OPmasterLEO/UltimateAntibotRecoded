package me.kr1s_d.ultimateantibot.netty;

import io.netty.buffer.ByteBuf;

public final class MinecraftPacket {
    private final int packetId;
    private final ByteBuf payload;
    private final boolean inbound;

    public MinecraftPacket(int packetId, ByteBuf payload, boolean inbound) {
        this.packetId = packetId;
        this.payload = payload;
        this.inbound = inbound;
    }

    public int getPacketId() { return packetId; }
    public ByteBuf getPayload() { return payload; }
    public boolean isInbound() { return inbound; }
}
