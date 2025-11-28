public class LoadStoreBuffer extends ReservationStation {
    // For Stores: Vv holds the value to be stored, Qv holds the tag that will produce the value
    public double Vv = 0.0; 
    public String Qv = "";

    public LoadStoreBuffer(String name) {
        super(name);
    }

    @Override
    public void clear() {
        super.clear();
        this.Vv = 0.0;
        this.Qv = "";
    }
}