import java.util.Map;
import java.util.HashMap;

class RegisterFile {
    private Map<String, Double> values;
    private Map<String, String> tags;
    
    public RegisterFile() {
        values = new HashMap<String, Double>();
        tags = new HashMap<String, String>();
        
        for (int i = 0; i <= 31; i++) {
            values.put("F" + i, 0.0);
            values.put("R" + i, 0.0);
            tags.put("F" + i, null);
            tags.put("R" + i, null);
        }
    }
    
    public double getValue(String reg) {
        Double val = values.get(reg);
        return val != null ? val : 0.0;
    }
    
    public void setValue(String reg, double value) {
        values.put(reg, value);
    }
    
    public String getTag(String reg) {
        return tags.get(reg);
    }
    
    public void setTag(String reg, String tag) {
        tags.put(reg, tag);
    }
}