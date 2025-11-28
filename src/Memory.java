class Memory {
    private byte[] data;
    
    public Memory(int size) {
        data = new byte[size];
    }
    
    // Read a double (8 bytes) from memory
    public double readDouble(int address) {
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits |= ((long)(data[address + i] & 0xFF)) << (i * 8);
        }
        return Double.longBitsToDouble(bits);
    }
    
    // Write a double (8 bytes) to memory
    public void writeDouble(int address, double value) {
        long bits = Double.doubleToRawLongBits(value);
        for (int i = 0; i < 8; i++) {
            data[address + i] = (byte)((bits >> (i * 8)) & 0xFF);
        }
    }
    
    // Read a word (4 bytes) from memory
    public int readWord(int address) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value |= (data[address + i] & 0xFF) << (i * 8);
        }
        return value;
    }
    
    // Write a word (4 bytes) to memory
    public void writeWord(int address, int value) {
        for (int i = 0; i < 4; i++) {
            data[address + i] = (byte)((value >> (i * 8)) & 0xFF);
        }
    }
    
    // Initialize memory with some values for testing
    public void initializeMemory(int address, double value) {
        writeDouble(address, value);
    }
}