package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;


public class TftpClientProtocol implements MessagingProtocol<byte[]> {

    private boolean shouldTerminate;
    private boolean isProcessingData;
    private FileOutputStream outputStream;
    private FileInputStream inputStream;
    private boolean isDirq = false;
    private ByteBuffer dirqBuffer;
    private String currFileName;
    private boolean isDisc = false;
    private boolean needToPrintWRQ;

    private final String pathToFiles = "." + File.separator;

    @Override
    public byte[] process(byte[] message) {
        short opcode = bytesToShort(new byte[]{message[0], message[1]}); // Read the opcode from the first 2 bytes, translate it to short
        switch(opcode) {
            case 1: return RRQ(message);
            case 2: return WRQ(message);
            case 3: return DATA(message);
            case 4: return ACK(message);
            case 5: return ERROR(message);
            case 6: return DIRQ(message);
            case 7: return message;
            case 8: return message;
            case 9: return BCAST(message);
            case 10: return DISC(message);

            default: return null; //TODO: maybe return something else


        }
    }

    private byte[] RRQ(byte[] message) {
        try{
            byte[] fileName = Arrays.copyOfRange(message, 2, message.length - 1); //-1 because of the 0 byte
            String filePath = pathToFiles + new String(fileName);
            if(Files.exists(Paths.get(filePath))) {
                ERROR(createErrorMsg((short) 5, "File already exists"));
                return null;
            }
            outputStream = new FileOutputStream(new String(fileName));
            isProcessingData = true;
            currFileName = new String(fileName);
            return message;
            
        } catch (IOException e) {
            isProcessingData = false;
            outputStream = null;
            ERROR(createErrorMsg((short) 2, "Access violation"));
        }
        return null;
    }

    private byte[] WRQ(byte[] message) {
        byte[] fileName = Arrays.copyOfRange(message, 2, message.length - 1); //-1 because of the 0 byte
        currFileName = new String(fileName);
        String filePath = pathToFiles + currFileName;
        if(!Files.exists(Paths.get(filePath))) {
            ERROR(createErrorMsg((short) 1, "File not found"));
            currFileName = null;
            return null;
        }
        try {
            inputStream = new FileInputStream(currFileName);
            isProcessingData = true;
            return message;
        } catch (IOException e) {
            isProcessingData = false;
            inputStream = null;
            currFileName = null;
            ERROR(createErrorMsg((short) 1, "File not found")); 
        }
        return null;
    }

    private byte[] DATA(byte[] message) {

        if(isDirq){
            return DATA_DIRQ(message);
        }
        
        short packetSize = bytesToShort(new byte[]{message[2], message[3]});
        short currBlockNum = bytesToShort(new byte[]{message[4], message[5]});

        try{
            outputStream.write(Arrays.copyOfRange(message, 6, message.length));
            // If the packet size is less than 512, it is the last packet:
            if (packetSize < 512) {
                System.out.println("RRQ " + currFileName + " complete");
                currFileName = null;
                outputStream.close();
                outputStream = null;
                isProcessingData = false;
            }
            return createAckMsg(currBlockNum);
        } catch (IOException e) {
            try {
                outputStream.close();
            } catch (IOException ex) {}
            outputStream = null;
            isProcessingData = false;
            return ERROR(createErrorMsg((short) 2, "Access violation")); 
        }
    }

    private byte[] DATA_DIRQ(byte[] message) {
        short packetSize = bytesToShort(new byte[]{message[2], message[3]});
        short currBlockNum = bytesToShort(new byte[]{message[4], message[5]});
        byte[] data = Arrays.copyOfRange(message, 6, message.length);
        dirqBuffer.put(data);
     
        if (packetSize < 512) {
            printDirq();
            isDirq = false;
            dirqBuffer.clear();
            dirqBuffer = null;
        }
        return createAckMsg(currBlockNum);
    }

    /**
     * Prints the directory query response.
     */
    private void printDirq() {
        byte[] data = dirqBuffer.array();
        int i = 0;
        while (i < data.length) {
            if(i != data.length - 1 && data[i] == 0 && data[i + 1] == 0){
                break;
            }
            int j = i;
            while (data[j] != 0) {
                j++;
            }
            System.out.println(new String(Arrays.copyOfRange(data, i, j)));
            i = j + 1;
        }
    }


    private byte[] ACK(byte[] message) {
        byte[] blockNum = {message[2], message[3]};
        System.out.println("ACK " + bytesToShort(blockNum));
        if (needToPrintWRQ){
            System.out.println("WRQ " + currFileName + " completed.");
            needToPrintWRQ = false;
            currFileName = null;

        }
        if (isDisc){
            shouldTerminate = true;
            return null;
        }
        short ackBlockNum = bytesToShort(new byte[]{message[2], message[3]});
        // If the client is currently sending data, send the next packet or reset the fields if there is no more data to send
        if(isProcessingData) {
            byte[] data = new byte[512];
            try{
                int bytesRead = inputStream.read(data, 0, 512);
                if (bytesRead < 512) {
                    data = Arrays.copyOfRange(data, 0, bytesRead);
                    isProcessingData = false;
                    inputStream.close();
                    inputStream = null;
                    needToPrintWRQ = true;
                }
                return createDataMsg(data, ++ackBlockNum);
            } catch (IOException e) {
                isProcessingData = false;
                try {
                    inputStream.close();
                } catch (IOException ex) {}
                inputStream = null;
                return ERROR(createErrorMsg((short) 2, "Access violation")); 
            }
        }
        return null;
    }



    private byte[] ERROR(byte[] message) {
        byte[] errorNumber = new byte[]{message[2], message[3]};
        byte[] errorMsg = Arrays.copyOfRange(message, 4, message.length);
        System.out.println("Error " + bytesToShort(errorNumber) + " " + new String(errorMsg));

        //If the error is file not found or access violation, close the output stream and stop processing the data
        if(isProcessingData) {
            isProcessingData = false;
            try {
                outputStream.close();
                inputStream.close();
            } catch (Exception e) {}

            //If i created a new file in the RRQ, Delete it.
            if(currFileName != null && Files.exists(Paths.get(pathToFiles + new String(currFileName)))) {
                try {
                    File file = new File((pathToFiles + currFileName));
                    file.delete();
                } catch (Exception e) {}
            }
            inputStream = null;
            outputStream = null;
        }
        return null;
    }

    private byte[] DIRQ(byte[] message) {
        isDirq = true;
        dirqBuffer = ByteBuffer.allocate(1 << 16);
        return message;
    }

    private byte[] BCAST(byte[] message) {
        byte delOrAdd = message[2];
        byte[] fileName = Arrays.copyOfRange(message, 3, message.length);
        String delOrDeleted = (delOrAdd) == (byte) 0 ? "del" : "add";
        System.out.println("BCAST " + delOrDeleted + " " + new String(fileName));
        return null;
    }

    private byte[] DISC(byte[] message){
        isDisc = true;        
        return message;
    }


    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private byte[] createAckMsg(short blockNum) {
        byte[] blockNumBytes = shortToBytes(blockNum);
        return new byte[] {0, 4, blockNumBytes[0], blockNumBytes[1]};
    }

    private byte[] createDataMsg(byte[] data, short currBlockNum) {
        int outputSize =2+2+2+ data.length; 
        byte[] output = new byte[outputSize];
        byte[] packetSize = shortToBytes((short)data.length);
        byte[] blockNumber = shortToBytes(currBlockNum);
        
        // Set the opcode to DATA, the packet size to the size of the data, and the block number to the specified block number
        output[0] = 0; output[1] = 3;
        output[2] = packetSize[0];
        output[3] = packetSize[1];
        output[4] = blockNumber[0]; 
        output[5] = blockNumber[1];
        // copy the error message to the output array
        for (int i = 0; i < data.length; i++) {
            output[i+6] = data[i];
        }
        return output; 
    }

    private byte[] createErrorMsg(short errorCode, String errorMsg) {
        byte[] errorMsgBytes = errorMsg.getBytes();
        int outputSize = 2 + 2 + errorMsgBytes.length + 1; 
        byte[] output = new byte[outputSize];
        byte[] errorCodeBytes = shortToBytes(errorCode);
        // Set the opcode to ERROR and the error code to the specified error code
        output[0] = 0; output[1] = 5;
        output[2] = errorCodeBytes[0];
        output[3] = errorCodeBytes[1];
        // copy the error message to the output array
        for (int i = 0; i < errorMsgBytes.length; i++) {
            output[i+4] = errorMsgBytes[i];
        }
        output[outputSize-1] = (byte) 0; // 0 for the last byte of the error message
        return output;
    }

    private short bytesToShort(byte[] byteArr){
        return (short) (((short) byteArr[0]) << 8 | (short) (byteArr[1]) & 0x00ff);
    }

    private byte[] shortToBytes(short num){
        return new byte[]{(byte) (num >> 8), (byte) (num )};
    }
    public boolean isDisc(){
        return isDisc;
    }
}
