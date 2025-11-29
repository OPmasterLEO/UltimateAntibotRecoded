package me.kr1s_d.ultimateantibot.netty;

import io.netty.buffer.ByteBuf;

public final class VarInt {
    private VarInt() {}

    public static int readVarInt(ByteBuf in) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            if (!in.isReadable()) return Integer.MIN_VALUE;
            read = in.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((read & 0b10000000) != 0);

        return result;
    }

    public static int peekVarInt(ByteBuf in) {
        in.markReaderIndex();
        try {
            int r = readVarInt(in);
            return r;
        } finally {
            in.resetReaderIndex();
        }
    }
}
