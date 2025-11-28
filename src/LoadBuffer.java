class LoadBuffer {
    String name;
    boolean busy = false;
    String operation;
    int address = 0;
    double vBase = 0.0;
    String qBase = null;
    boolean baseReady = false;
    int cyclesLeft = 0;
    int lastDepClearCycle = 0;
    int effectiveAddress = 0;
    double loadedValue = 0.0;
    Instruction instruction;
    
    public LoadBuffer(String name) {
        this.name = name;
    }
    
    public void clear() {
        busy = false;
        operation = null;
        address = 0;
        vBase = 0.0;
        qBase = null;
        baseReady = false;
        cyclesLeft = 0;
        lastDepClearCycle = 0;
        effectiveAddress = 0;
        loadedValue = 0.0;
        instruction = null;
    }

}