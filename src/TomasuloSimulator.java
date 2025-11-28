import java.io.*;
import java.util.*;

public class TomasuloSimulator {
    
    private List<Instruction> instructions;
    private RegisterFile registerFile;
    private Memory memory;
    private List<ReservationStation> addSubStations;
    private List<ReservationStation> mulDivStations;
    private List<LoadBuffer> loadBuffers;
    private List<StoreBuffer> storeBuffers;
    private Map<String, Integer> latencies;
    private int cycle;
    private int pc;
    
    public TomasuloSimulator() {
        instructions = new ArrayList<Instruction>();
        registerFile = new RegisterFile();
        memory = new Memory(4096); // 4KB memory
        addSubStations = new ArrayList<ReservationStation>();
        mulDivStations = new ArrayList<ReservationStation>();
        loadBuffers = new ArrayList<LoadBuffer>();
        storeBuffers = new ArrayList<StoreBuffer>();
        latencies = new HashMap<String, Integer>();
        cycle = 0;
        pc = 0;
        
        // Initialize stations
        for (int i = 1; i <= 3; i++) {
            addSubStations.add(new ReservationStation("Add" + i));
        }
        for (int i = 1; i <= 2; i++) {
            mulDivStations.add(new ReservationStation("Mul" + i));
        }
        for (int i = 1; i <= 3; i++) {
            loadBuffers.add(new LoadBuffer("Load" + i));
        }
        for (int i = 1; i <= 2; i++) {
            storeBuffers.add(new StoreBuffer("Store" + i));
        }
        
        // Set latencies
        latencies.put("ADD.D", 2);
        latencies.put("SUB.D", 2);
        latencies.put("MUL.D", 10);
        latencies.put("DIV.D", 40);
        latencies.put("L.D", 2);
        latencies.put("S.D", 2);
    }
    
    public void loadProgram(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                instructions.add(new Instruction(line));
            }
        }
        reader.close();
        System.out.println("Loaded " + instructions.size() + " instructions");
    }
    
    public void setRegister(String reg, double value) {
        registerFile.setValue(reg, value);
    }
    
    public void initializeMemory(int address, double value) {
        memory.initializeMemory(address, value);
    }
    
    public double readMemory(int address) {
        return memory.readDouble(address);
    }
    
    public void run() {
        System.out.println("\n=== Starting Simulation ===\n");
        
        while (!isComplete() && cycle < 1000) {
            cycle++;
            writeBack();
            execute();
            issue();
        }
        
        printResults();
    }
    
    private void issue() {
        if (pc >= instructions.size()) return;
        
        Instruction instr = instructions.get(pc);
        String op = instr.operation;
        
        // Find free station
        Object station = null;
        if (op.equals("ADD.D") || op.equals("SUB.D")) {
            station = findFreeStation(addSubStations);
        } else if (op.equals("MUL.D") || op.equals("DIV.D")) {
            station = findFreeStation(mulDivStations);
        } else if (op.equals("L.D")) {
            station = findFreeLoad();
        } else if (op.equals("S.D")) {
            station = findFreeStore();
        }
        
        if (station == null) return;
        
        // ADDRESS CLASH DETECTION for Load/Store instructions
        if (op.equals("L.D") || op.equals("S.D")) {
            // Calculate the effective address for this instruction
            String baseReg = instr.rs1;
            int offset = instr.immediate;
            int effectiveAddr = (int)registerFile.getValue(baseReg) + offset;
            
            if (op.equals("L.D")) {
                // Load: Check if any STORE buffer has the same address
                if (hasAddressClashWithStores(effectiveAddr)) {
                    return; // Cannot issue, address clash with pending store
                }
            } else if (op.equals("S.D")) {
                // Store: Check if any LOAD or STORE buffer has the same address
                if (hasAddressClashWithLoads(effectiveAddr) || hasAddressClashWithStores(effectiveAddr)) {
                    return; // Cannot issue, address clash with pending load/store
                }
            }
        }
        
        if (station instanceof LoadBuffer) {
            LoadBuffer lb = (LoadBuffer) station;
            lb.busy = true;
            lb.operation = op;
            lb.instruction = instr;
            lb.cyclesLeft = latencies.get(op);
            lb.address = instr.immediate;
            
            String baseReg = instr.rs1;
            String baseTag = registerFile.getTag(baseReg);
            if (baseTag != null) {
                lb.qBase = baseTag;
            } else {
                lb.vBase = registerFile.getValue(baseReg);
                lb.baseReady = true;
            }
            
            registerFile.setTag(instr.rd, lb.name);
            
        } else if (station instanceof StoreBuffer) {
            StoreBuffer sb = (StoreBuffer) station;
            sb.busy = true;
            sb.operation = op;
            sb.instruction = instr;
            sb.cyclesLeft = latencies.get(op);
            sb.address = instr.immediate;
            
            String baseReg = instr.rs1;
            String baseTag = registerFile.getTag(baseReg);
            if (baseTag != null) {
                sb.qBase = baseTag;
            } else {
                sb.vBase = registerFile.getValue(baseReg);
                sb.baseReady = true;
            }
            
            String valueReg = instr.rd;
            String valueTag = registerFile.getTag(valueReg);
            if (valueTag != null) {
                sb.qValue = valueTag;
            } else {
                sb.vValue = registerFile.getValue(valueReg);
                sb.valueReady = true;
            }
            
        } else if (station instanceof ReservationStation) {
            ReservationStation rs = (ReservationStation) station;
            rs.busy = true;
            rs.operation = op;
            rs.instruction = instr;
            rs.cyclesLeft = latencies.get(op);
            
            String src1 = instr.rs1;
            String tag1 = registerFile.getTag(src1);
            if (tag1 != null) {
                rs.qj = tag1;
            } else {
                rs.vj = registerFile.getValue(src1);
                rs.vjReady = true;
            }
            
            String src2 = instr.rs2;
            String tag2 = registerFile.getTag(src2);
            if (tag2 != null) {
                rs.qk = tag2;
            } else {
                rs.vk = registerFile.getValue(src2);
                rs.vkReady = true;
            }
            
            registerFile.setTag(instr.rd, rs.name);
        }
        
        instr.issueTime = cycle;
        pc++;
    }
    
    private void execute() {
        executeStations(addSubStations);
        executeStations(mulDivStations);
        executeLoads();
        executeStores();
    }
    
    private void executeStations(List<ReservationStation> stations) {
        for (ReservationStation rs : stations) {
            if (!rs.busy) continue;
            if (!rs.vjReady || !rs.vkReady) continue;
            
            // Only start execution if dependencies cleared in a previous cycle
            if (rs.instruction.execStartTime == 0) {
                if (rs.lastDepClearCycle == 0 || cycle > rs.lastDepClearCycle) {
                    rs.instruction.execStartTime = cycle;
                } else {
                    continue; // Wait one more cycle
                }
            }
            
            if (rs.cyclesLeft > 0) {
                rs.cyclesLeft--;
                if (rs.cyclesLeft == 0) {
                    rs.instruction.execEndTime = cycle;
                }
            }
        }
    }
    
    private void executeLoads() {
        for (LoadBuffer lb : loadBuffers) {
            if (!lb.busy) continue;
            if (!lb.baseReady) continue;
            
            if (lb.instruction.execStartTime == 0) {
                if (lb.lastDepClearCycle == 0 || cycle > lb.lastDepClearCycle) {
                    lb.instruction.execStartTime = cycle;
                    // Calculate effective address when execution starts
                    lb.effectiveAddress = (int)lb.vBase + lb.address;
                } else {
                    continue;
                }
            }
            
            if (lb.cyclesLeft > 0) {
                lb.cyclesLeft--;
                if (lb.cyclesLeft == 0) {
                    lb.instruction.execEndTime = cycle;
                    // Perform the actual load from memory
                    lb.loadedValue = memory.readDouble(lb.effectiveAddress);
                }
            }
        }
    }
    
    private void executeStores() {
        for (StoreBuffer sb : storeBuffers) {
            if (!sb.busy) continue;
            if (!sb.baseReady || !sb.valueReady) continue;
            
            if (sb.instruction.execStartTime == 0) {
                if (sb.lastDepClearCycle == 0 || cycle > sb.lastDepClearCycle) {
                    sb.instruction.execStartTime = cycle;
                    // Calculate effective address when execution starts
                    sb.effectiveAddress = (int)sb.vBase + sb.address;
                } else {
                    continue;
                }
            }
            
            if (sb.cyclesLeft > 0) {
                sb.cyclesLeft--;
                if (sb.cyclesLeft == 0) {
                    sb.instruction.execEndTime = cycle;
                    // Perform the actual store to memory
                    memory.writeDouble(sb.effectiveAddress, sb.vValue);
                }
            }
        }
    }
    
    private void writeBack() {
        writeBackStations(addSubStations);
        writeBackStations(mulDivStations);
        writeBackLoads();
        writeBackStores();
    }
    
    private void writeBackStations(List<ReservationStation> stations) {
        for (ReservationStation rs : stations) {
            if (!rs.busy) continue;
            if (rs.cyclesLeft != 0) continue;
            if (rs.instruction.execEndTime != cycle - 1) continue;
            
            double result = computeResult(rs);
            broadcast(rs.name, result);
            
            String dest = rs.instruction.rd;
            registerFile.setValue(dest, result);
            if (rs.name.equals(registerFile.getTag(dest))) {
                registerFile.setTag(dest, null);
            }
            
            rs.instruction.writeTime = cycle;
            rs.clear();
        }
    }
    
    private void writeBackLoads() {
        for (LoadBuffer lb : loadBuffers) {
            if (!lb.busy) continue;
            if (lb.cyclesLeft != 0) continue;
            if (lb.instruction.execEndTime != cycle - 1) continue;
            
            // Use the value loaded from memory
            double result = lb.loadedValue;
            broadcast(lb.name, result);
            
            String dest = lb.instruction.rd;
            registerFile.setValue(dest, result);
            if (lb.name.equals(registerFile.getTag(dest))) {
                registerFile.setTag(dest, null);
            }
            
            lb.instruction.writeTime = cycle;
            lb.clear();
        }
    }
    
    private void writeBackStores() {
        for (StoreBuffer sb : storeBuffers) {
            if (!sb.busy) continue;
            if (sb.cyclesLeft != 0) continue;
            if (sb.instruction.execEndTime != cycle - 1) continue;
            
            sb.instruction.writeTime = cycle;
            sb.clear();
        }
    }
    
    private void broadcast(String tag, double value) {
        for (ReservationStation rs : addSubStations) {
            if (tag.equals(rs.qj)) {
                rs.vj = value;
                rs.qj = null;
                rs.vjReady = true;
                rs.lastDepClearCycle = cycle;
            }
            if (tag.equals(rs.qk)) {
                rs.vk = value;
                rs.qk = null;
                rs.vkReady = true;
                rs.lastDepClearCycle = cycle;
            }
        }
        
        for (ReservationStation rs : mulDivStations) {
            if (tag.equals(rs.qj)) {
                rs.vj = value;
                rs.qj = null;
                rs.vjReady = true;
                rs.lastDepClearCycle = cycle;
            }
            if (tag.equals(rs.qk)) {
                rs.vk = value;
                rs.qk = null;
                rs.vkReady = true;
                rs.lastDepClearCycle = cycle;
            }
        }
        
        for (LoadBuffer lb : loadBuffers) {
            if (tag.equals(lb.qBase)) {
                lb.vBase = value;
                lb.qBase = null;
                lb.baseReady = true;
                lb.lastDepClearCycle = cycle;
            }
        }
        
        for (StoreBuffer sb : storeBuffers) {
            if (tag.equals(sb.qBase)) {
                sb.vBase = value;
                sb.qBase = null;
                sb.baseReady = true;
                sb.lastDepClearCycle = cycle;
            }
            if (tag.equals(sb.qValue)) {
                sb.vValue = value;
                sb.qValue = null;
                sb.valueReady = true;
                sb.lastDepClearCycle = cycle;
            }
        }
    }
    
    private double computeResult(ReservationStation rs) {
        String op = rs.operation;
        if (op.equals("ADD.D")) return rs.vj + rs.vk;
        if (op.equals("SUB.D")) return rs.vj - rs.vk;
        if (op.equals("MUL.D")) return rs.vj * rs.vk;
        if (op.equals("DIV.D")) return rs.vj / rs.vk;
        return 0.0;
    }
    
    private ReservationStation findFreeStation(List<ReservationStation> stations) {
        for (ReservationStation rs : stations) {
            if (!rs.busy) return rs;
        }
        return null;
    }
    
    private LoadBuffer findFreeLoad() {
        for (LoadBuffer lb : loadBuffers) {
            if (!lb.busy) return lb;
        }
        return null;
    }
    
    private StoreBuffer findFreeStore() {
        for (StoreBuffer sb : storeBuffers) {
            if (!sb.busy) return sb;
        }
        return null;
    }
    
    // Check if effective address clashes with any busy store buffer
    private boolean hasAddressClashWithStores(int address) {
        for (StoreBuffer sb : storeBuffers) {
            if (sb.busy) {
                // We need to compute the store's effective address
                // If base is ready, we can compute it
                if (sb.baseReady) {
                    int storeAddr = (int)sb.vBase + sb.address;
                    if (storeAddr == address) {
                        return true; // Address clash detected
                    }
                } else {
                    // Base not ready yet, conservatively assume clash
                    // (or we could wait until base is ready)
                    return true;
                }
            }
        }
        return false;
    }
    
    // Check if effective address clashes with any busy load buffer
    private boolean hasAddressClashWithLoads(int address) {
        for (LoadBuffer lb : loadBuffers) {
            if (lb.busy) {
                // We need to compute the load's effective address
                if (lb.baseReady) {
                    int loadAddr = (int)lb.vBase + lb.address;
                    if (loadAddr == address) {
                        return true; // Address clash detected
                    }
                } else {
                    // Base not ready yet, conservatively assume clash
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isComplete() {
        if (pc < instructions.size()) return false;
        
        for (ReservationStation rs : addSubStations) {
            if (rs.busy) return false;
        }
        for (ReservationStation rs : mulDivStations) {
            if (rs.busy) return false;
        }
        for (LoadBuffer lb : loadBuffers) {
            if (lb.busy) return false;
        }
        for (StoreBuffer sb : storeBuffers) {
            if (sb.busy) return false;
        }
        
        return true;
    }
    
    private void printResults() {
        System.out.println("\n=== Instruction Status Table ===");
        System.out.printf("%-20s | %-6s | %-6s | %-6s | %-6s%n",
            "Instruction", "Issue", "Exec S", "Exec E", "Write");
        System.out.println("----------------------------------------------------------");
        
        for (Instruction instr : instructions) {
            System.out.printf("%-20s | %-6d | %-6s | %-6s | %-6s%n",
                instr.raw,
                instr.issueTime,
                instr.execStartTime > 0 ? instr.execStartTime : "-",
                instr.execEndTime > 0 ? instr.execEndTime : "-",
                instr.writeTime > 0 ? instr.writeTime : "-");
        }
    }
    
    public static void main(String[] args) {
        try {
            TomasuloSimulator sim = new TomasuloSimulator();
            sim.loadProgram("program1.txt");
            
            // Initialize registers
            sim.setRegister("R2", 100.0);
            sim.setRegister("F1", 1.0);
            sim.setRegister("F3", 3.0);
            sim.setRegister("F5", 5.0);
            
            // Initialize memory at addresses that will be accessed
            sim.initializeMemory(100, 10.0);  // For L.D F6, 0(R2) where R2=100
            sim.initializeMemory(108, 20.0);  // For L.D F2, 20(R2) where R2=100
            sim.initializeMemory(116, 30.0);  // For L.D F2, 0(R3) where R3=200
            
            sim.run();
            
            // Show memory after stores (if any)
            System.out.println("\n=== Memory Contents (selected addresses) ===");
            System.out.printf("Address 100: %.2f%n", sim.readMemory(100));
            System.out.printf("Address 108: %.2f%n", sim.readMemory(108));
            System.out.printf("Address 116: %.2f%n", sim.readMemory(116));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}