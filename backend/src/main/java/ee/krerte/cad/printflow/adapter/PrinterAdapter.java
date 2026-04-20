package ee.krerte.cad.printflow.adapter;

import ee.krerte.cad.printflow.entity.Printer;

/**
 * Üle kõigi printer-protokollide (Bambu MQTT, Moonraker JSON-RPC,
 * OctoPrint REST, Prusa Connect, jne). V1-s on olemas ainult
 * {@link MockPrinterAdapter}.
 */
public interface PrinterAdapter {
    /**
     * Viimane teadaolev staatus (cached). Task-scheduler teeb `refresh()`
     * eraldi kõne.
     */
    AdapterStatus status(Printer p);

    /** Saada job-GCode printerile. Tagastab adapter-specific job-id. */
    String dispatch(Printer p, byte[] gcode, String jobName);

    /** Pause, resume, cancel. */
    void pause(Printer p);
    void resume(Printer p);
    void cancel(Printer p);

    /** Refresh temperatures / progress (heartbeat). */
    AdapterStatus refresh(Printer p);

    /** Millist printer.adapterType see adapter tunneb ära? */
    String supportsType();
}
