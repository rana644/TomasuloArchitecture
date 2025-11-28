class Instruction {
    String raw;
    String operation;
    String rd;
    String rs1;
    String rs2;
    int immediate;
    
    int issueTime = 0;
    int execStartTime = 0;
    int execEndTime = 0;
    int writeTime = 0;
    
    public Instruction(String line) {
        this.raw = line;
        parse(line);
    }
    
    private void parse(String line) {
        String[] parts = line.split("[\\s,()]+");
        operation = parts[0];
        
        if (operation.equals("L.D") || operation.equals("S.D")) {
            rd = parts[1];
            immediate = Integer.parseInt(parts[2]);
            rs1 = parts[3];
        } else {
            rd = parts[1];
            rs1 = parts[2];
            rs2 = parts[3];
        }
    }
}