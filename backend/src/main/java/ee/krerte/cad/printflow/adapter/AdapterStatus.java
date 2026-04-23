package ee.krerte.cad.printflow.adapter;

import java.math.BigDecimal;

public class AdapterStatus {
    /** "IDLE" | "PRINTING" | "PAUSED" | "ERROR" | "OFFLINE" */
    public String status;

    public Integer progressPct;
    public BigDecimal bedTempC;
    public BigDecimal hotendTempC;
    public String currentAdapterJobId;
    public String message;

    public static AdapterStatus offline() {
        AdapterStatus s = new AdapterStatus();
        s.status = "OFFLINE";
        s.progressPct = 0;
        return s;
    }

    public static AdapterStatus idle() {
        AdapterStatus s = new AdapterStatus();
        s.status = "IDLE";
        s.progressPct = 0;
        s.bedTempC = new BigDecimal("22.0");
        s.hotendTempC = new BigDecimal("22.0");
        return s;
    }
}
