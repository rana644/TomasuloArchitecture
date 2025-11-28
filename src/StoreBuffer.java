class StoreBuffer {
    String name;
    boolean busy = false;
    String operation;
    int address = 0;
    double vBase = 0.0;
    String qBase = null;
    boolean baseReady = false;
    double vValue = 0.0;
    String qValue = null;
    boolean valueReady = false;
    int cyclesLeft = 0;
    int lastDepClearCycle = 0;
    Instruction instruction;
    
    public StoreBuffer(String name) {
        this.name = name;
    }
    
    public void clear() {
        busy = false;
        operation = null;
        address = 0;
        vBase = 0.0;
        qBase = null;
        baseReady = false;
        vValue = 0.0;
        qValue = null;
        valueReady = false;
        cyclesLeft = 0;
        lastDepClearCycle = 0;
        instruction = null;
    }
}