package ro.ai.bridge;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.Nullable;

/**
 * Conexiune falsa: personajul nu are client, deci tot ce trimite serverul catre el
 * se arunca. Restul jucatorilor il vad normal, pentru ca serverul il trateaza ca
 * pe orice jucator conectat (lista de jucatori, spawn, pozitie, chat).
 *
 * Metodele sunt declarate fara @Override intentionat: daca o semnatura difera
 * intre versiuni, codul tot compileaza si metoda ramane doar nefolosita.
 */
public class FakeClientConnection extends Connection {
    public FakeClientConnection(PacketFlow flow) {
        super(flow);
    }

    public void setReadOnly() {
    }

    public void send(Packet<?> packet) {
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
    }

    public void handleDisconnection() {
    }

    public void setListenerForServerboundHandshake(PacketListener listener) {
    }

    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) {
    }
}
