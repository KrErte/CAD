package ee.krerte.cad.printflow.adapter;

import ee.krerte.cad.printflow.entity.Printer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simuleeritud printeriadapter. Hoiab mällu print-oleku + progressi. Iga
 * `refresh()` kõne lükkab edasi 0.1% progressi (või lõpetab, kui progress
 * jõudis 100%).
 *
 * Seda kasutame demo + testide jaoks, kuni Bambu/Moonraker-adapterid on
 * olemas.
 */
@Component
public class MockPrinterAdapter implements PrinterAdapter {

    /**
     * Mälus olev per-printeri state (status, progress, temps).
     * Thread-safe, et scheduler paralleelselt saaks heartbeat'e teha.
     */
    private final ConcurrentHashMap<Long, MockState> state = new ConcurrentHashMap<>();

    @Override
    public String supportsType() { return "MOCK"; }

    @Override
    public AdapterStatus status(Printer p) {
        MockState s = state.computeIfAbsent(p.getId(), k -> MockState.idle());
        return s.toStatus();
    }

    @Override
    public String dispatch(Printer p, byte[] gcode, String jobName) {
        MockState s = state.computeIfAbsent(p.getId(), k -> MockState.idle());
        s.adapterJobId = "mock-" + UUID.randomUUID();
        s.status = "PRINTING";
        s.progressPct = 0;
        s.bedTempC = new BigDecimal("60.0");
        s.hotendTempC = new BigDecimal("210.0");
        s.gcodeBytes = gcode == null ? 0 : gcode.length;
        s.jobName = jobName;
        // Simuleerime töö kestust: ~2 minutit full-bar tagasiminekuks
        // (refresh() lisab iga kõnega 2% — scheduler tick iga 30s → ~25min)
        return s.adapterJobId;
    }

    @Override
    public void pause(Printer p) {
        MockState s = state.get(p.getId());
        if (s != null && "PRINTING".equals(s.status)) s.status = "PAUSED";
    }

    @Override
    public void resume(Printer p) {
        MockState s = state.get(p.getId());
        if (s != null && "PAUSED".equals(s.status)) s.status = "PRINTING";
    }

    @Override
    public void cancel(Printer p) {
        MockState s = state.get(p.getId());
        if (s != null) {
            s.status = "IDLE";
            s.progressPct = 0;
            s.adapterJobId = null;
        }
    }

    @Override
    public AdapterStatus refresh(Printer p) {
        MockState s = state.computeIfAbsent(p.getId(), k -> MockState.idle());
        if ("PRINTING".equals(s.status)) {
            // 5–12% per heartbeat, natuke varieeruv
            s.progressPct = Math.min(100, s.progressPct + ThreadLocalRandom.current().nextInt(5, 12));
            // väikesed temperatuuride kõikumised
            s.bedTempC = new BigDecimal(String.format(java.util.Locale.US, "%.1f",
                    60.0 + ThreadLocalRandom.current().nextDouble(-0.8, 0.8)));
            s.hotendTempC = new BigDecimal(String.format(java.util.Locale.US, "%.1f",
                    210.0 + ThreadLocalRandom.current().nextDouble(-1.5, 1.5)));
            if (s.progressPct >= 100) {
                s.status = "IDLE";
                s.progressPct = 0;
                s.adapterJobId = null;
                s.completedJob = true;
            }
        }
        return s.toStatus();
    }

    /** Kas viimase refresh'i tagajärjel läks töö DONE-i? (JobScheduler kasutab) */
    public boolean consumeCompletionFlag(Long printerId) {
        MockState s = state.get(printerId);
        if (s == null) return false;
        boolean done = s.completedJob;
        s.completedJob = false;
        return done;
    }

    private static class MockState {
        String status;
        Integer progressPct;
        BigDecimal bedTempC;
        BigDecimal hotendTempC;
        String adapterJobId;
        String jobName;
        int gcodeBytes;
        boolean completedJob;

        static MockState idle() {
            MockState s = new MockState();
            s.status = "IDLE";
            s.progressPct = 0;
            s.bedTempC = new BigDecimal("22.0");
            s.hotendTempC = new BigDecimal("22.0");
            return s;
        }

        AdapterStatus toStatus() {
            AdapterStatus a = new AdapterStatus();
            a.status = status;
            a.progressPct = progressPct;
            a.bedTempC = bedTempC;
            a.hotendTempC = hotendTempC;
            a.currentAdapterJobId = adapterJobId;
            return a;
        }
    }
}
