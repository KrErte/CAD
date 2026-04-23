package ee.krerte.cad.gallery;

import ee.krerte.cad.auth.DesignRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** Print order & instant quote system. Pricing: material cost + print time cost + shipping. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // Pricing constants
    private static final double PLA_PER_G = 0.025; // 25 €/kg
    private static final double PETG_PER_G = 0.030; // 30 €/kg
    private static final double PRINT_PER_MIN = 0.02; // 0.02 €/min machine time
    private static final double BASE_FEE = 3.50; // base handling fee
    private static final Map<String, Double> SHIPPING =
            Map.of("EE", 3.99, "LV", 5.99, "LT", 5.99, "FI", 7.99, "SE", 9.99, "DE", 9.99);
    private static final double SHIPPING_DEFAULT = 12.99;

    private final PrintOrderRepository orderRepo;
    private final DesignRepository designRepo;

    public OrderController(PrintOrderRepository orderRepo, DesignRepository designRepo) {
        this.orderRepo = orderRepo;
        this.designRepo = designRepo;
    }

    private Long uid() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /** Instant price quote — no auth needed for calculation. */
    public record QuoteRequest(
            double weightG,
            int printTimeMin,
            String material,
            int infillPct,
            int quantity,
            String country) {}

    @PostMapping("/quote")
    public ResponseEntity<?> quote(@RequestBody QuoteRequest req) {
        double materialRate = "PETG".equalsIgnoreCase(req.material()) ? PETG_PER_G : PLA_PER_G;
        double materialCost = req.weightG() * materialRate * (req.infillPct() / 20.0);
        double timeCost = req.printTimeMin() * PRINT_PER_MIN;
        double shipping =
                SHIPPING.getOrDefault(
                        req.country() != null ? req.country().toUpperCase() : "EE",
                        SHIPPING_DEFAULT);
        double unitPrice = BASE_FEE + materialCost + timeCost;
        double totalPrice = unitPrice * req.quantity() + shipping;

        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("material_cost_eur", round2(materialCost));
        quote.put("time_cost_eur", round2(timeCost));
        quote.put("base_fee_eur", BASE_FEE);
        quote.put("unit_price_eur", round2(unitPrice));
        quote.put("quantity", req.quantity());
        quote.put("shipping_eur", shipping);
        quote.put("total_eur", round2(totalPrice));
        quote.put("currency", "EUR");
        quote.put(
                "estimated_days",
                req.country() != null && req.country().equalsIgnoreCase("EE") ? "2-3" : "5-7");
        return ResponseEntity.ok(quote);
    }

    /** Place a print order. */
    public record PlaceOrderRequest(
            Long designId,
            String material,
            int infillPct,
            int quantity,
            String color,
            String shippingName,
            String shippingAddress,
            String shippingCity,
            String shippingZip,
            String shippingCountry,
            String notes) {}

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest req) {
        Long userId = uid();
        var design = designRepo.findById(req.designId()).orElse(null);
        if (design == null || !design.getUserId().equals(userId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Disaini ei leitud"));
        }

        // Calculate price from design weight (rough estimate from STL size)
        double estimatedWeight = design.getSizeBytes() / 100.0; // very rough
        double materialRate = "PETG".equalsIgnoreCase(req.material()) ? PETG_PER_G : PLA_PER_G;
        double materialCost = estimatedWeight * materialRate;
        double shipping =
                SHIPPING.getOrDefault(
                        req.shippingCountry() != null ? req.shippingCountry().toUpperCase() : "EE",
                        SHIPPING_DEFAULT);
        double total = (BASE_FEE + materialCost) * req.quantity() + shipping;

        PrintOrder order = new PrintOrder();
        order.setUserId(userId);
        order.setDesignId(req.designId());
        order.setMaterial(req.material() != null ? req.material() : "PLA");
        order.setInfillPct(req.infillPct() > 0 ? req.infillPct() : 20);
        order.setQuantity(req.quantity() > 0 ? req.quantity() : 1);
        order.setColor(req.color() != null ? req.color() : "must");
        order.setShippingName(req.shippingName());
        order.setShippingAddress(req.shippingAddress());
        order.setShippingCity(req.shippingCity());
        order.setShippingZip(req.shippingZip());
        order.setShippingCountry(req.shippingCountry() != null ? req.shippingCountry() : "EE");
        order.setPriceEur(BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP));
        order.setNotes(req.notes());
        orderRepo.save(order);

        return ResponseEntity.ok(
                Map.of(
                        "order_id",
                        order.getId(),
                        "price_eur",
                        order.getPriceEur(),
                        "status",
                        "pending",
                        "message",
                        "Tellimus vastu võetud! Saadame kinnituse meilile."));
    }

    /** List my orders. */
    @GetMapping
    public List<Map<String, Object>> myOrders() {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(uid()).stream()
                .map(
                        o -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", o.getId());
                            m.put("design_id", o.getDesignId());
                            m.put("material", o.getMaterial());
                            m.put("quantity", o.getQuantity());
                            m.put("color", o.getColor());
                            m.put("price_eur", o.getPriceEur());
                            m.put("status", o.getStatus());
                            m.put("created_at", o.getCreatedAt().toString());
                            return m;
                        })
                .toList();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
