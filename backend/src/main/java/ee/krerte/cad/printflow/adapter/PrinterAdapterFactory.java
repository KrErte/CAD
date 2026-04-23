package ee.krerte.cad.printflow.adapter;

import ee.krerte.cad.printflow.entity.Printer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Valib õige adapteri printeri `adapterType` järgi. V1-s on ainult MOCK, aga signature on juba
 * valmis tuleviku integratsioonide jaoks (BAMBU, MOONRAKER, OCTOPRINT, PRUSA_CONNECT).
 */
@Component
public class PrinterAdapterFactory {

    private final Map<String, PrinterAdapter> byType;
    private final MockPrinterAdapter fallback;

    public PrinterAdapterFactory(List<PrinterAdapter> adapters, MockPrinterAdapter mock) {
        this.byType =
                adapters.stream().collect(Collectors.toMap(PrinterAdapter::supportsType, a -> a));
        this.fallback = mock;
    }

    public PrinterAdapter forPrinter(Printer p) {
        if (p.getAdapterType() == null) return fallback;
        return byType.getOrDefault(p.getAdapterType(), fallback);
    }
}
