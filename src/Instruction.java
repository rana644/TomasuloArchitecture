public class Instruction {
    public String raw;
    public String opCode;
    public String rd; // Destination register
    public String rs1; // Source 1 / Base register
    public String rs2; // Source 2
    public String immediateOrLabel;

    public int issueCycle = 0;
    public int startExecuteCycle = 0;
    public int endExecuteCycle = 0;
    public int writeResultCycle = 0;

    public Instruction(String rawInstruction) {
        this.raw = rawInstruction;
        parseInstruction(rawInstruction);
    }

    private void parseInstruction(String rawInstruction) {
        String parts[] = rawInstruction.split("[\\s,()]+");
        this.opCode = parts[0];

        if (opCode.startsWith("L.D") || opCode.startsWith("S.D")) {
            // Format: L.D F6, 32(R2)
            this.rd = parts[1]; // F6 (Destination/Value Register)
            this.immediateOrLabel = parts[2]; // 32
            this.rs1 = parts[3]; // R2 (Base Register)
        } else if (opCode.startsWith("ADD") || opCode.startsWith("SUB") || opCode.startsWith("MUL") || opCode.startsWith("DIV")) {
            // Format: ADD.D F6, F8, F2
            this.rd = parts[1];
            this.rs1 = parts[2];
            this.rs2 = parts[3];
        } else {
            // Simplified handling for other instruction types
            if (parts.length > 1) this.rd = parts[1];
            if (parts.length > 2) this.rs1 = parts[2];
            if (parts.length > 3) this.rs2 = parts[3];
        }
    }
}