package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

public class ListeningThread implements Runnable{
    BufferedOutputStream out;
    BufferedInputStream in;
    TftpClientEncoderDecoder encdec;
    TftpClientProtocol protocol;

    public ListeningThread(BufferedOutputStream out,BufferedInputStream in, TftpClientEncoderDecoder encdec, TftpClientProtocol protocol) {
        this.out = out;
        this.in = in;
        this.encdec = encdec;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try{
            int read;
            while (!protocol.shouldTerminate()  && (read = in.read()) >= 0) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                
                if (nextMessage != null) {
                    byte[] response = protocol.process(nextMessage);
                    if (response != null) {
                        out.write(response);
                        out.flush();
                    }
                }
            }
        } catch(IOException e){
            e.printStackTrace();
        }
    }

}


