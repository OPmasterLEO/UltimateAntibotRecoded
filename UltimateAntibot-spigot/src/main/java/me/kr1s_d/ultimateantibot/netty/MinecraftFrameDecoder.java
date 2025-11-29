package me.kr1s_d.ultimateantibot.netty;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class MinecraftFrameDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();
        int length = VarInt.peekVarInt(in);
        if (length == Integer.MIN_VALUE) {
            in.resetReaderIndex();
            return;
        }
        // read actual length
        int packetLength = VarInt.readVarInt(in);
        if (in.readableBytes() < packetLength) {
            in.resetReaderIndex();
            return;
        }

        // slice packet data
        ByteBuf packetData = in.readRetainedSlice(packetLength);
        int packetId = VarInt.readVarInt(packetData);
        out.add(new MinecraftPacket(packetId, packetData, true));
    }
}
