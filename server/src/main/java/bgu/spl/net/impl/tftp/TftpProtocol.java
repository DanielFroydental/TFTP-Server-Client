package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder.opcodes;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

// Assuming you have a class that handles tracking logged-in users and more

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    static class holder {
        static ConcurrentHashMap<Integer, Boolean> ids_login = new ConcurrentHashMap<>();
        static ConcurrentHashMap<byte[], Integer> loggedIn_userNames_ids = new ConcurrentHashMap<>();
    }

    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    private byte[] userName;
    private boolean isProcessingData;
    // private FileInputStream fileInputStream;
    private ByteBuffer fileBuffer;
    private short blockNum;
    private short lastBlockNum;
    private FileOutputStream fileOutputStream;
    private byte[] currFileName;
    private final String pathToFiles = "Files" + File.separator;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        holder.ids_login.put(connectionId, false);
        // connections.connect(connectionId, null)
        userName = null;
        isProcessingData = false;
        blockNum = 0;
        lastBlockNum = 0;
    }

    @Override
    public void process(byte[] message) {
        short opcode = bytesToShort(new byte[] { message[0], message[1] }); // Read the opcode from the first 2 bytes,
                                                                            // translate it to short
        if (userName == null && opcode != 7) {
            connections.send(connectionId, createErrorMsg((short) 6, "User not logged in."));
            return;
        }
        switch (opcode) {
            case 1:
                RRQ(message);
                break;
            case 2:
                WRQ(message);
                break;
            case 3:
                DATA(message);
                break;
            case 4:
                ACK(message);
                break;
            case 5:
                ERROR(message);
                break;
            case 6:
                DIRQ();
                break;
            case 7:
                LOGRQ(message);
                break;
            case 8:
                DELRQ(message);
                break;
            // case 9: // BCAST is only server to client
            // break;
            case 10:
                DISC();
                break;
            default:
                break;
        }

    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    /**
     * Converts a byte array to a short value.
     *
     * @param byteArr the byte array to convert
     * @return the converted short value
     */
    private short bytesToShort(byte[] byteArr) {
        return (short) (((short) byteArr[0]) << 8 | (short) (byteArr[1]) & 0x00ff);
    }

    private void RRQ(byte[] message) {
        byte[] fileNameInBytes = Arrays.copyOfRange(message, 2, message.length);
        String filePath = pathToFiles + new String(fileNameInBytes);
        boolean exists = Files.exists(Paths.get(filePath));
        if (!exists) {
            connections.send(connectionId, createErrorMsg((short) 1, "File not found."));
            return;
        }
        try {
            isProcessingData = true;
            byte[] fileData = Files.readAllBytes(Paths.get(filePath));
            fileBuffer = ByteBuffer.wrap(fileData);
            lastBlockNum = (short) ((fileData.length / 512) + 1); // The last block number is the number of blocks in
                                                                  // the file
            sendData();
        } catch (IOException e) {
            connections.send(connectionId, createErrorMsg((short) 2, "Access violation."));
        }
    }

    /**
     * Sends the data from the file buffer to the connected client.
     * The data is sent in packets of maximum size 512 bytes.
     * If there is more data remaining in the file buffer, another packet will be
     * sent after ACK is received.
     */
    public void sendData() {
        int length = Math.min(fileBuffer.remaining(), 512);
        byte[] currBlock = new byte[length];
        if (length > 0) {
            fileBuffer.get(currBlock, 0, length); // Writes the next length bytes of the file into currBlock
        }
        blockNum++;
        connections.send(connectionId, createDataMsg(currBlock, blockNum));
    }

    private void WRQ(byte[] message) {
        byte[] fileName = Arrays.copyOfRange(message, 2, message.length);
        String filePath = pathToFiles + new String(fileName);
        boolean exists = Files.exists(Paths.get(filePath));
        if (exists) {
            connections.send(connectionId, createErrorMsg((short) 5, "File already exists."));
            return;
        }
        try {
            isProcessingData = true;
            fileOutputStream = new FileOutputStream(new File(filePath));
            currFileName = fileName;
            connections.send(connectionId, createAckMsg((short) 0));
        } catch (IOException e) {
            connections.send(connectionId, createErrorMsg((short) 2, "Access violation."));
        }
    }

    private void DATA(byte[] message) {
        short packetSize = bytesToShort(new byte[] { message[2], message[3] });
        short currBlockNum = bytesToShort(new byte[] { message[4], message[5] });

        try {
            fileOutputStream.write(Arrays.copyOfRange(message, 6, message.length));
            connections.send(connectionId, createAckMsg(currBlockNum));
            if (packetSize < 512) {
                fileOutputStream.close();
                fileOutputStream = null;
                isProcessingData = false;
                BCAST(currFileName, false); // Broadcast the file addition
                currFileName = null;
            }

        } catch (IOException e) {
            connections.send(connectionId, createErrorMsg((short) 2, "Access violation."));
            try {
                fileOutputStream.close();
            } catch (IOException ex) {
            }
            fileOutputStream = null;
            isProcessingData = false;
            currFileName = null;
        }
    }

    private void ACK(byte[] message) {
        short ackBlockNum = bytesToShort(new byte[] { message[2], message[3] });
        // If the client is currently sending data, send the next packet or reset the
        // fields if there is no more data to send
        if (isProcessingData) {
            if (ackBlockNum < lastBlockNum) {
                sendData();
            }
            // If there is no more data to send, reset the fields that are used for sending
            // data
            else {
                fileBuffer.clear();
                blockNum = 0;
                isProcessingData = false;
                lastBlockNum = 0;
            }
        }
    }

    // Received IFF the client had failed during the sending of a file. Reset the
    // fields that are used for data moving
    private void ERROR(byte[] message) {
        isProcessingData = false;
        fileBuffer.clear();
        blockNum = 0;
        lastBlockNum = 0;
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
            }
            fileOutputStream = null;
        }
    }

    private void DIRQ() {
        int lengthOfFileNames = 0;
        File folder = new File(pathToFiles);
        File[] listOfFiles = folder.listFiles();

        // Calculate the length of the file names
        for (File file : listOfFiles) {
            byte[] fileNameInBytes = file.getName().getBytes();
            lengthOfFileNames += fileNameInBytes.length + 1; // +1 for the 0 byte
        }
        // Initialize the file buffer empty
        fileBuffer = ByteBuffer.allocate(lengthOfFileNames);
        try {
            // Copy the file names to the file buffer , separated by 0 bytes
            for (String fileName : folder.list()) {
                byte[] fileNameInBytes = fileName.getBytes();
                fileBuffer.put(fileNameInBytes);
                fileBuffer.put((byte) 0);
            }
            // Flip the file buffer to prepare it for reading and change the state to
            // sending data
            fileBuffer.flip();
            isProcessingData = true;
            blockNum = 0;
            lastBlockNum = (short) ((lengthOfFileNames / 512) + 1);
            sendData();
        } catch (Exception e) {
            connections.send(connectionId, createErrorMsg((short) 2, "Access violation."));
        }
    }

    /**
     * Handles the LOGRQ (Login Request) message.
     * If the username is not already logged in and it's the first time the user
     * logs in, login the user.
     * If the user is already logged in or the username is taken, send an error
     * message.
     * 
     * @param message The LOGRQ message received.
     */
    private void LOGRQ(byte[] message) {
        byte[] usernameBytes = Arrays.copyOfRange(message, 2, message.length);
        // If the username is not already logged in,and it's the first time the user
        // logs in, login the user
        if (userName == null &&
                !holder.loggedIn_userNames_ids.keySet().stream().anyMatch(key -> Arrays.equals(key, usernameBytes))) {
            userName = usernameBytes;
            holder.ids_login.put(connectionId, true); // TODO: Check if the user is already logged in
            holder.loggedIn_userNames_ids.put(usernameBytes, connectionId);
            connections.send(connectionId, createAckMsg((short) 0));
        }
        // If the user is already logged in, or the username is taken, send an error
        // message
        else {
            connections.send(connectionId, createErrorMsg((short) 7, "User already logged in."));
        }
    }

    private void DELRQ(byte[] message) {
        byte[] fileNameInBytes = Arrays.copyOfRange(message, 2, message.length);
        String filePath = pathToFiles + new String(fileNameInBytes);
        boolean exists = Files.exists(Paths.get(filePath));
        if (!exists) {
            connections.send(connectionId, createErrorMsg((short) 1, "File not found."));
            return;
        }
        // If the file exists, delete it and send a BCAST message to all logged-in
        // clients
        try {
            File file = new File(filePath);
            file.delete();
            connections.send(connectionId, createAckMsg((short) 0));
            BCAST(fileNameInBytes, true);

        } catch (Exception e) {
            connections.send(connectionId, createErrorMsg((short) 2, "Access violation."));
        }
    }

    /**
     * Broadcasts a message to all logged-in clients.
     * 
     * @param fileName   the name of the file to be broadcasted
     * @param wasRemoved indicates whether the file was removed or added
     */
    private void BCAST(byte[] fileName, boolean wasRemoved) {
        byte[] bcastMsg = new byte[fileName.length + 4];
        // Set the opcode to BCAST and the action to 0 for file removed or 1 for file
        // added
        bcastMsg[0] = 0;
        bcastMsg[1] = 9;
        if (wasRemoved) {
            bcastMsg[2] = 0;
        } else {
            bcastMsg[2] = 1;
        }
        // Copy the file name to the broadcast message
        for (int i = 0; i < fileName.length; i++) {
            bcastMsg[i + 3] = fileName[i];
        }
        // Add a 0 byte at the end of the BCAST message
        bcastMsg[bcastMsg.length - 1] = 0;
        // Send the broadcast message to all logged-in clients
        for (int id : holder.ids_login.keySet()) {
            if (holder.ids_login.get(id)) {
                connections.send(id, bcastMsg);
            }
        }
    }

    /**
     * Disconnects the client from the server.
     */
    private void DISC() {
        connections.send(connectionId, createAckMsg((short) 0)); // send an acknowledgment message to the client to
                                                                 // confirm the disconnection
        connections.disconnect(connectionId);
        holder.ids_login.remove(connectionId);
        holder.loggedIn_userNames_ids.remove(userName);
    }

    /**
     * Creates an error message byte array with the specified error code and error
     * message.
     *
     * @param errorCode the error code to be included in the error message
     * @param errorMsg  the error message to be included in the error message
     * @return the error message byte array
     */
    private byte[] createErrorMsg(short errorCode, String errorMsg) {
        byte[] errorMsgBytes = errorMsg.getBytes();
        int outputSize = 2 + 2 + errorMsgBytes.length + 1;
        byte[] output = new byte[outputSize];
        byte[] errorCodeBytes = shortToBytes(errorCode);
        // Set the opcode to ERROR and the error code to the specified error code
        output[0] = 0;
        output[1] = 5;
        output[2] = errorCodeBytes[0];
        output[3] = errorCodeBytes[1];
        // copy the error message to the output array
        for (int i = 0; i < errorMsgBytes.length; i++) {
            output[i + 4] = errorMsgBytes[i];
        }
        output[outputSize - 1] = (byte) 0; // 0 for the last byte of the error message
        return output;
    }

    /**
     * Creates an acknowledgment message (ACK) for the specified block number.
     *
     * @param blockNum The block number to be included in the ACK message.
     * @return The ACK message as a byte array.
     */
    private byte[] createAckMsg(short blockNum) {
        byte[] blockNumBytes = shortToBytes(blockNum);
        return new byte[] { 0, 4, blockNumBytes[0], blockNumBytes[1] };
    }

    /**
     * Creates a data message with the specified data and block number.
     * 
     * @param data     The data to be included in the message.
     * @param blockNum The block number of the message.
     * @return The byte array representing the data message.
     */
    private byte[] createDataMsg(byte[] data, short currBlockNum) {
        int outputSize = 2 + 2 + 2 + data.length;
        byte[] output = new byte[outputSize];
        byte[] packetSize = shortToBytes((short) data.length);
        byte[] blockNumber = shortToBytes(currBlockNum);

        // Set the opcode to DATA, the packet size to the size of the data, and the
        // block number to the specified block number
        output[0] = 0;
        output[1] = 3;
        output[2] = packetSize[0];
        output[3] = packetSize[1];
        output[4] = blockNumber[0];
        output[5] = blockNumber[1];
        // copy the error message to the output array
        for (int i = 0; i < data.length; i++) {
            output[i + 6] = data[i];
        }
        return output;
    }

    /**
     * Converts a short number to a byte array.
     *
     * @param num the short number to be converted
     * @return the byte array representation of the short number
     */
    private byte[] shortToBytes(short num) {
        return new byte[] { (byte) (num >> 8), (byte) (num & 0xff) };
    }
}
