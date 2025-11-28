import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TomasuloSimulator {
    public List<Instruction> program = new ArrayList<>();
    public int pc = 0;
    public int cycle = 1;

    // Reservation Stations (Fixed sizes)
    public List<ReservationStation> intRS = new ArrayList<>();
    public List<ReservationStation> fpAddRS = new ArrayList<>();
    public List<ReservationStation> fpMulRS = new ArrayList<>();
    public List<LoadStoreBuffer> loadBuffers = new ArrayList<>();
    public List<LoadStoreBuffer> storeBuffers = new ArrayList<>();
    
    public RegisterFile regFile = new RegisterFile();
    private Map<String, Integer> latencyMap = new HashMap<>();
    private Map<String, Integer> labels = new HashMap<>();

    public TomasuloSimulator() {
        // Initialize RS lists
        for (int i = 0; i < 2; i++) intRS.add(new ReservationStation("Int" + (i + 1)));
        for (int i = 0; i < 3; i++) fpAddRS.add(new ReservationStation("Add" + (i + 1)));
        for (int i = 0; i < 2; i++) fpMulRS.add(new ReservationStation("Mul" + (i + 1)));
        for (int i = 0; i < 3; i++) loadBuffers.add(new LoadStoreBuffer("Load" + (i + 1)));
        for (int i = 0; i < 2; i++) storeBuffers.add(new LoadStoreBuffer("Store" + (i + 1)));
        
        // Correct Latencies (Standard MIPS/Tomasulo)
        latencyMap.put("ADD.D", 2); latencyMap.put("SUB.D", 2);
        latencyMap.put("MUL.D", 10); latencyMap.put("DIV.D", 40);
        latencyMap.put("L.D", 2); latencyMap.put("S.D", 1);
        latencyMap.put("DADD", 1); latencyMap.put("DSUB", 1);
        latencyMap.put("BRANCH", 1);
    }

    // --- PROGRAM LOADING & PRINTING (Your original methods, slightly trimmed) ---
    public boolean loadProgram(String filePath) {
        // ... (Your program loading logic remains here) ...
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int instructionIndex = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.endsWith(":")) {
                    String label = line.substring(0, line.length() - 1);
                    labels.put(label, instructionIndex); 
                } else {
                    program.add(new Instruction(line));
                    instructionIndex++;
                }
            }
            System.out.println("Program loaded successfully. Total instructions: " + program.size());
            return true;
        } catch (IOException e) {
            System.err.println("Error loading program file: " + e.getMessage());
            return false;
        }
    }
    
    private void printInstructionStatus() {
        System.out.println("\n=== ? Instruction Status Table ===");
        System.out.printf("%-20s | %-6s | %-6s | %-6s | %-6s%n", 
                             "Instruction", "Issue", "Exec S", "Exec E", "Write");
        System.out.println("----------------------------------------------------------");
        
        for (Instruction instr : program) {
            System.out.printf("%-20s | %-6s | %-6s | %-6s | %-6s%n",
                instr.raw, 
                instr.issueCycle > 0 ? instr.issueCycle : "-", 
                instr.startExecuteCycle > 0 ? instr.startExecuteCycle : "-",
                instr.endExecuteCycle > 0 ? instr.endExecuteCycle : "-", 
                instr.writeResultCycle > 0 ? instr.writeResultCycle : "-");
        }
    }
    
    // ... (Your printReservationStations and printRegisterFile methods go here) ...
    private void printReservationStations() { /* ... */ }
    private void printRegisterFile() { /* ... */ }

    // --- ISSUE STAGE ---
    private void issue() {
        if (pc >= program.size()) return;

        Instruction instr = program.get(pc);
        String op = instr.opCode;
        
        List<? extends ReservationStation> targetList = getRSListForOp(op);
        ReservationStation freeRS = targetList.stream().filter(rs -> !rs.busy).findFirst().orElse(null);

        if (freeRS == null) {
            // System.out.println("STALL: " + instr.raw + " (No free RS/Buffer)");
            return; 
        }

        // --- Renaming and Setup ---
        freeRS.clear();
        freeRS.busy = true;
        freeRS.op = op;
        freeRS.instruction = instr;
        freeRS.remainingCycles = getLatency(op);
        
        String rs1 = instr.rs1;
        String rs2 = instr.rs2;
        String rd = instr.rd;

        // Set Vj/Qj
        if (rs1 != null) {
            String QjTag = regFile.rrsStatus.getOrDefault(rs1, "");
            if (!QjTag.isEmpty()) { freeRS.Qj = QjTag; } 
            else { freeRS.Vj = regFile.getValue(rs1); }
        }

        // Set Vk/Qk
        if (rs2 != null) {
            String QkTag = regFile.rrsStatus.getOrDefault(rs2, "");
            if (!QkTag.isEmpty()) { freeRS.Qk = QkTag; } 
            else { freeRS.Vk = regFile.getValue(rs2); }
        }
        
        // Handle Store value (Vv/Qv)
        if (op.startsWith("S.D") && freeRS instanceof LoadStoreBuffer) {
            LoadStoreBuffer storeBuf = (LoadStoreBuffer) freeRS;
            String QvTag = regFile.rrsStatus.getOrDefault(rd, ""); // RD holds the source value for S.D
            if (!QvTag.isEmpty()) { storeBuf.Qv = QvTag; }
            else { storeBuf.Vv = regFile.getValue(rd); }
            freeRS.Dest = instr.rs1; // Dest is the base reg for address calculation
        } else if (!op.startsWith("S")) {
            freeRS.Dest = rd; 
        }
        
        // Update RRS only for result-writing instructions
        if (freeRS.Dest != null && !op.startsWith("S") && !op.startsWith("B")) {
             regFile.rrsStatus.put(freeRS.Dest, freeRS.name);
        }

        // --- Finalize Issue ---
        instr.issueCycle = cycle;
        pc++;
        // System.out.println("ISSUED: " + instr.raw + " -> " + freeRS.name);
    }

    // --- EXECUTE STAGE (The Correct Timing Logic) ---
    private void execute() {
        List<ReservationStation> allStations = getAllActiveRS();
        
        for (ReservationStation rs : allStations) {
            if (!rs.busy) continue;
            
            // 1. CHECK OPERANDS READY
            boolean operandsReady = rs.Qj.isEmpty() && rs.Qk.isEmpty();
            
            if (rs.op.startsWith("S.D")) {
                LoadStoreBuffer lsb = (LoadStoreBuffer) rs;
                operandsReady = operandsReady && lsb.Qv.isEmpty();
            }
            
            // 2. START EXECUTION (CRITICAL FIX)
            if (operandsReady && rs.instruction.startExecuteCycle == 0) {
                
                // Rule: Start = Max(Issue Cycle, Last Dependency Clear Cycle) + 1
                int triggerCycle = Math.max(rs.instruction.issueCycle, rs.lastDependencyClearCycle);
                
                // CRITICAL: Must start exactly 1 cycle AFTER the trigger event.
                rs.instruction.startExecuteCycle = triggerCycle + 1;
                
                // System.out.println("SCHEDULED: " + rs.instruction.raw + " for C" + rs.instruction.startExecuteCycle);
            }
            
            // 3. CONTINUE EXECUTION (Deduct cycles only if we are in or past the scheduled start cycle)
            if (rs.instruction.startExecuteCycle > 0 && 
                rs.instruction.startExecuteCycle <= cycle && 
                rs.remainingCycles > 0) {
                rs.remainingCycles--;
            }
            
            // 4. MARK EXECUTION COMPLETE
            if (rs.remainingCycles == 0 && rs.instruction.endExecuteCycle == 0) {
                rs.instruction.endExecuteCycle = cycle;
            }
        }
    }
    
    // --- WRITE RESULT STAGE (CRITICAL DEPENDENCY TRACKING) ---
    private void writeResult() {
        List<ReservationStation> allStations = getAllActiveRS();
        
        // 1. Find the winner based on remainingCycles == 0 and oldest issueCycle
        ReservationStation winnerRS = allStations.stream()
            .filter(rs -> rs.remainingCycles == 0 && rs.instruction.writeResultCycle == 0)
            .min(Comparator.comparingInt(rs -> rs.instruction.issueCycle))
            .orElse(null);

        if (winnerRS == null) return; 

        double result = computeResult(winnerRS);
        String tag = winnerRS.name;
        
        // 2. Broadcast Result (CDB) - Update all dependent RS entries
        if (!winnerRS.op.startsWith("S")) { // Stores don't broadcast results
            for (ReservationStation rs : allStations) {
                boolean dependencyCleared = false;

                if (rs.Qj.equals(tag)) { rs.Vj = result; rs.Qj = ""; dependencyCleared = true; }
                if (rs.Qk.equals(tag)) { rs.Vk = result; rs.Qk = ""; dependencyCleared = true; }
                
                if (rs instanceof LoadStoreBuffer) {
                    LoadStoreBuffer lsb = (LoadStoreBuffer) rs;
                    if (lsb.Qv.equals(tag)) { lsb.Vv = result; lsb.Qv = ""; dependencyCleared = true; }
                }
                
                // CRITICAL FIX: Record the cycle when the dependency was resolved
                if (dependencyCleared) {
                    rs.lastDependencyClearCycle = cycle; 
                }
            }
        }

        // 3. Update Register File & Clear RRS
        if (winnerRS.Dest != null && !winnerRS.op.startsWith("S") && !winnerRS.op.startsWith("B")) {
            regFile.setValue(winnerRS.Dest, result);
            
            String currentTag = regFile.rrsStatus.get(winnerRS.Dest);
            if (currentTag != null && currentTag.equals(tag)) {
                regFile.rrsStatus.put(winnerRS.Dest, "");
            }
        }
        
        // 4. Finalize
        winnerRS.instruction.writeResultCycle = cycle;
        winnerRS.clear();
        // System.out.println("WRITE RESULT: " + winnerRS.instruction.raw + " -> C" + cycle);
    }
    
    // --- RUN LOOP ---
    public void run() {
        System.out.println("\n--- Starting Simulation ---");
        
        while ((pc < program.size() || anyBusy()) && cycle < 100) { 
            // System.out.println("\n===== 🕰️ CYCLE " + cycle + " =====");

            // 1. Write Result (Resolves dependencies and sets lastDependencyClearCycle)
            writeResult(); 

            // 2. Execute (Uses lastDependencyClearCycle to determine Exec S)
            execute(); 

            // 3. Issue 
            issue();
            
            // printInstructionStatus();
            
            cycle++;
        }
        System.out.println("\n--- Simulation Ended ---");
        printInstructionStatus();
    }
    
    // --- HELPERS ---
    private List<ReservationStation> getAllActiveRS() {
        List<ReservationStation> allStations = new ArrayList<>();
        allStations.addAll(intRS);
        allStations.addAll(fpAddRS);
        allStations.addAll(fpMulRS);
        allStations.addAll(loadBuffers);
        allStations.addAll(storeBuffers);
        return allStations.stream().filter(rs -> rs.busy).collect(Collectors.toList());
    }
    
    private List<? extends ReservationStation> getRSListForOp(String op) {
        if (op.startsWith("ADD") || op.startsWith("SUB")) return fpAddRS;
        if (op.startsWith("MUL") || op.startsWith("DIV")) return fpMulRS;
        if (op.startsWith("L.D")) return loadBuffers;
        if (op.startsWith("S.D")) return storeBuffers;
        return intRS; 
    }
    
    private int getLatency(String op) {
        return latencyMap.getOrDefault(op, 1);
    }

    private double computeResult(ReservationStation rs) {
        // Simplified computation, should be done in EXECUTE for a real system
        if (rs.op.startsWith("ADD")) return rs.Vj + rs.Vk;
        if (rs.op.startsWith("SUB")) return rs.Vj - rs.Vk;
        if (rs.op.startsWith("MUL")) return rs.Vj * rs.Vk;
        if (rs.op.startsWith("DIV")) return rs.Vj / rs.Vk;
        if (rs.op.startsWith("L.D")) return 1.0; // Mock memory access result
        return 0.0;
    }

    private boolean anyBusy() {
        return intRS.stream().anyMatch(rs -> rs.busy) ||
               fpAddRS.stream().anyMatch(rs -> rs.busy) ||
               fpMulRS.stream().anyMatch(rs -> rs.busy) ||
               loadBuffers.stream().anyMatch(rs -> rs.busy) ||
               storeBuffers.stream().anyMatch(rs -> rs.busy);
    }
    
    public static void main(String[] args) {
        // You MUST create a file named "program1.txt" with the instructions:
        /*
        L.D F6, 32(R2)
        L.D F2, 44(R3)
        MUL.D F0, F2, F4
        SUB.D F8, F2, F6
        DIV.D F10, F0, F6
        ADD.D F6, F8, F2
        */
        
        TomasuloSimulator sim = new TomasuloSimulator();
        
        if (sim.loadProgram("program1.txt")) {
            // Set initial register values used in the trace
            sim.regFile.setValue("R2", 100.0);
            sim.regFile.setValue("R3", 200.0); 
            sim.regFile.setValue("F4", 2.0); 
            sim.run();
        }
    }
}