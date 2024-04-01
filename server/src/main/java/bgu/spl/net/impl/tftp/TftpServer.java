package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.ServerForTftp;

public class TftpServer {

        public static void main(String[] args) {
            // you can use any server... 
            ServerForTftp.threadPerClient(
                    7777, //port
                    () -> new TftpProtocol(), //protocol factory
                    TftpEncoderDecoder::new //message encoder decoder factory
            ).serve();
        }
}
