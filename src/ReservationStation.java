public class ReservationStation {
    public String name;
    public boolean busy = false;
    public String op = "";
    public double Vj = 0.0;
    public double Vk = 0.0;
    public String Qj = "";
    public String Qk = "";
    public String Dest = ""; // Target register
    public double A = 0.0; // Immediate/Address (Used for L/S base address)
    public Instruction instruction;
    public int remainingCycles = 0;
    public boolean originalQjNotEmpty = false;  // 🔥 ADD THIS
    public boolean originalQkNotEmpty = false;  // 🔥 ADD THIS
    
    // CRITICAL FIX: Tracks when the last source dependency was cleared
    public int lastDependencyClearCycle = 0; 

    public ReservationStation(String name) {
        this.name = name;
    }

    public void clear() {
        this.busy = false;
        this.op = "";
        this.Vj = 0.0;
        this.Vk = 0.0;
        this.Qj = "";
        this.Qk = "";
        this.Dest = "";
        this.A = 0.0;
        this.instruction = null;
        this.remainingCycles = 0;
        this.lastDependencyClearCycle = 0;
    }
}