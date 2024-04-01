package bgu.spl.net.srv;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder;
import bgu.spl.net.impl.tftp.TftpProtocol;

import java.io.Closeable;
import java.util.function.Supplier;

public interface ServerForTftp extends Closeable {

    /**
     * The main loop of the server, Starts listening and handling new clients.
     */
    void serve();

    /**
     *This function returns a new instance of a thread per client pattern server
     * @param port The port for the server socket
     * @param protocolFactory A factory that creats new TftpProtocols
     * @param encoderDecoderFactory A factory that creats new T
     * @return A new Thread per client server
     */
    static Server<byte[]>  threadPerClient(
            int port,
            Supplier<TftpProtocol> protocolFactory,
            Supplier<TftpEncoderDecoder> encoderDecoderFactory) {

        return new TftpBaseServer(port, protocolFactory, encoderDecoderFactory) {
            @Override
            protected void execute(TftpBlockingConnectionHandler  handler) {
                new Thread(handler).start();
            }
        };

    }


}
