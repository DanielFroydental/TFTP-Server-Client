package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;

public class KeyboardThread implements Runnable {
    BufferedOutputStream out;
    TftpClientEncoderDecoder encdec;
    TftpClientProtocol protocol;

    public KeyboardThread(BufferedOutputStream out, TftpClientEncoderDecoder encdec, TftpClientProtocol protocol) {
        this.out = out;
        this.encdec = encdec;
        this.protocol = protocol;
    }
 
    @Override
    public void run() {
        Scanner input = new Scanner(System.in);
        while (!protocol.shouldTerminate() && !protocol.isDisc()) {
            String command = input.nextLine();
            byte[] encodedMessage = encdec.encode(command);
            if (encodedMessage != null){
                byte[] response = protocol.process(encodedMessage);
                if (response != null){
                    try{
                        out.write(encodedMessage);
                        out.flush();
                    }
                    catch(IOException ex){
                        ex.printStackTrace();
                    }
                }
            }
            else {
                System.out.println("Error: invalid command");
            }
            
        }
        input.close();
    }
}
