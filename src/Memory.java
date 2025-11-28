public class Memory {
    public byte[] mem;
    public Memory(int size) { mem = new byte[size]; }
    public byte readByte(int addr) { return mem[addr]; }
    public void writeByte(int addr, byte val) { mem[addr] = val; }
}
