package ro.ai.bridge;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

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
        installChannel(this);
    }

    /**
     * Serverul are nevoie de un channel netty non-null (il foloseste pentru atribute
     * de protocol). Punem un EmbeddedChannel, exact ca modurile de tip Carpet:
     * pachetele nu pleaca nicaieri, dar serverul e multumit.
     */
    private static void installChannel(Connection connection) {
        try {
            for (Field field : Connection.class.getDeclaredFields()) {
                if (!Channel.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                if (field.get(connection) == null) {
                    field.set(connection, new EmbeddedChannel());
                }
                BridgeLog.info("canal fals instalat pe conexiunea personajului");
                return;
            }
            BridgeLog.warn("nu am gasit campul channel din Connection");
        } catch (ReflectiveOperationException | RuntimeException error) {
            BridgeLog.warn("nu pot instala canalul fals: " + error);
        }
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
