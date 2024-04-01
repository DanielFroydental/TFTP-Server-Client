package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;

public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"127.0.0.1", "7777"};
        }
        if (args.length < 2) {
            System.out.println("you must supply two arguments: ip, port");
            System.exit(1);
        }
        try (Socket sock = new Socket(args[0], Integer.parseInt(args[1]));
            BufferedInputStream in = new BufferedInputStream((sock.getInputStream()));
            BufferedOutputStream out = new BufferedOutputStream((sock.getOutputStream()))) {

            TftpClientEncoderDecoder encdec = new TftpClientEncoderDecoder();
            TftpClientProtocol protocol = new TftpClientProtocol();
            Thread keyboardThread = new Thread(new KeyboardThread(out, encdec, protocol));
            Thread listeningThread = new Thread(new ListeningThread(out, in, encdec, protocol));
            keyboardThread.start();
            listeningThread.start();

            keyboardThread.join();
            listeningThread.join();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
