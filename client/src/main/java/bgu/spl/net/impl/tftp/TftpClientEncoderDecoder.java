package bgu.spl.net.impl.tftp;

import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpClientEncoderDecoder implements MessageEncoderDecoder<byte[]>{

    byte[] bytes = new byte[1 << 10];
    int len = 0;
    short opcode = -1;
    short dataLength = -1;


    public static enum opcodes{
        NO_OPCODE(-1),
        RRQ(1),
        WRQ(2),
        DATA(3),
        ACK(4),
        ERROR(5),
        DIRQ(6),
        LOGRQ(7),
        DELRQ(8),
        BCAST(9),
        DISC(10);

        private final int value;
        private opcodes(int value){
            this.value = value;
        }

        private short getValue(){
            return (short) value;
        }

        private static short getValue(String name) {
            if(name.equals("RRQ")) return RRQ.getValue();
            if(name.equals("WRQ")) return WRQ.getValue();
            if(name.equals("DATA")) return DATA.getValue();
            if(name.equals("ACK")) return ACK.getValue();
            if(name.equals("ERROR")) return ERROR.getValue();
            if(name.equals("DIRQ")) return DIRQ.getValue();
            if(name.equals("LOGRQ")) return LOGRQ.getValue();
            if(name.equals("DELRQ")) return DELRQ.getValue();
            if(name.equals("BCAST")) return BCAST.getValue();
            if(name.equals("DISC")) return DISC.getValue();
            return NO_OPCODE.getValue();
        }
    }


    @Override
    public byte[] decodeNextByte(byte nextByte) {

        if (opcode != -1 && opcode != opcodes.DATA.value && opcode != opcodes.ACK.value){ // push all the bytes to the buffer until the end of the packet that ends with 0
            if(nextByte == (byte) 0) {
                if ((len >= 4) || (opcode != opcodes.ERROR.value && opcode != opcodes.BCAST.value)) {
                    return popBytes();
                }
            }
        }
        pushByte(nextByte);
        
        // Read the opcode from the first 2 bytes, translate it to short and act accordingly
        if (len == 2) {
            byte[] opCodeBytes = {bytes[0], bytes[1]};
            opcode = bytesToShort(opCodeBytes);

            // If the packet is of type DIRQ or DISC, this is the end of the packet
            if (opcode == opcodes.DIRQ.value || opcode == opcodes.DISC.value) {
                return popBytes();
            }
        }
        // If the packet is DATA, find the length of the data and return the packet iff we reach that length (+6 for the rest of the packet)
        if(opcode == opcodes.DATA.value) {
            // Get the length of the data from the 2nd and 3rd bytes
            if(len == 4){
                byte[] dataLengthBytes = {bytes[2], bytes[3]};
                dataLength = bytesToShort(dataLengthBytes);
            }
            // If we reached the end of the packet, return the packet
            if (len == 6 + dataLength) {
                return popBytes();
            }
        }
        // If the packet is ACK, wait to read the block number and then return the packet
        if (opcode == opcodes.ACK.value) {
            if(len == 4){
            return popBytes();
            }
        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        //TODO: implement this
        return message;
    }

    /*
     * Encodes a message from the keyboard thread.
     */
    public byte[] encode(String message) {
        short currOpcode = -1;
        byte[] output = null;

        if(message.startsWith("RRQ") || message.startsWith("WRQ")){
            if (message.length() <= 4){
                return null;
            }
            currOpcode = opcodes.getValue(message.substring(0, 3));
            byte[] fileName = message.substring(4).getBytes();
            output = encodeCaseOne(currOpcode, fileName);
        }
        else if(message.startsWith("LOGRQ") || message.startsWith("DELRQ")) {
            if (message.length() <= 6){
                return null;
            }
            currOpcode = opcodes.getValue(message.substring(0, 5));
            byte[] userName = message.substring(6).getBytes();
            output = encodeCaseOne(currOpcode, userName);
        }
        else if(message.startsWith("DIRQ") || message.startsWith("DISC")) {
            if (message.length() > 4){
                return null;
            }
            currOpcode = opcodes.getValue(message.substring(0, 4));
            output = shortToBytes(currOpcode);
        }   
        return output;
    }

    private byte[] encodeCaseOne(short opcode, byte[] name) {
        byte[] output = new byte[2 + name.length + 1];
        byte[] opCodeBytes = shortToBytes(opcode);
        output[0] = opCodeBytes[0];
        output[1] = opCodeBytes[1];
        for(int i = 0; i < name.length; i++) {
            output[i + 2] = name[i];
        }
        output[output.length - 1] = (byte) 0;
        return output;
    }

    /**
     * Pushes a byte into the bytes array.
     *
     * @param nextByte the byte to be pushed into the bytes array
     */
    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    /**
     * Retrieves and clears the bytes stored in the encoder/decoder.
     * Resets the len, opcode and dataLength fields.
     * 
     * @return the bytes stored in the encoder/decoder in a new array of the exact length.
     */
    private byte[] popBytes() {
        byte[] result = Arrays.copyOf(bytes, len);
        len = 0;
        opcode = -1;
        dataLength = -1; 
        return result;
    }


    /**
     * Converts a byte array to a short value.
     *
     * @param byteArr the byte array to convert
     * @return the converted short value
     */
    private short bytesToShort(byte[] byteArr){
        return ( short ) ((( short ) byteArr [0]) << 8 | ( short ) ( byteArr [1]) );
    }


    /**
     * Converts a short number to a byte array.
     *
     * @param num the short number to be converted
     * @return a byte array representing the short number
     */
    private byte[] shortToBytes(short num){
        return new byte[]{(byte) (num >> 8), (byte) (num & 0xff)};
    }
}
