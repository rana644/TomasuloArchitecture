import java.util.HashMap;
import java.util.Map;

public class RegisterFile {
    private Map<String, Double> values = new HashMap<>();
    public Map<String, String> rrsStatus = new HashMap<>(); // Register Name -> Reservation Station Tag
    private boolean barrierActive = false; // For enforcing sequential execution of Int/Branch

    public RegisterFile() {
        // Initialize F0-F31 and R1-R31 with default values and clear RRS
        for (int i = 0; i <= 31; i++) {
            values.put("F" + i, 0.0);
            values.put("R" + i, 0.0);
            rrsStatus.put("F" + i, "");
            rrsStatus.put("R" + i, "");
        }
    }

    public double getValue(String reg) {
        return values.getOrDefault(reg, 0.0);
    }

    public void setValue(String reg, double value) {
        values.put(reg, value);
    }
    
    public boolean isBarrierActive() {
        return barrierActive;
    }

    public void setBarrierActive(boolean active) {
        this.barrierActive = active;
    }
}