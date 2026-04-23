package ee.krerte.cad.printflow.service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fan-out SSE-emitter pool. Iga org-i jaoks hoiame listi avatud emitter'ist; iga printeri refresh →
 * publish → kõik listenerid saavad pushi.
 */
@Component
public class PrinterEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PrinterEventPublisher.class);

    private final Map<Long, List<SseEmitter>> byOrg = new HashMap<>();

    public synchronized SseEmitter register(Long orgId) {
        SseEmitter em = new SseEmitter(0L); // timeout=0 → never
        byOrg.computeIfAbsent(orgId, k -> new CopyOnWriteArrayList<>()).add(em);
        em.onCompletion(() -> remove(orgId, em));
        em.onTimeout(() -> remove(orgId, em));
        em.onError(t -> remove(orgId, em));
        try {
            em.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException ignored) {
        }
        return em;
    }

    public synchronized void publish(Long orgId, String event, Object payload) {
        List<SseEmitter> list = byOrg.getOrDefault(orgId, List.of());
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name(event).data(payload));
            } catch (IOException e) {
                log.debug("SSE emitter kaotatud: {}", e.getMessage());
                em.complete();
            }
        }
    }

    private synchronized void remove(Long orgId, SseEmitter em) {
        List<SseEmitter> list = byOrg.get(orgId);
        if (list != null) list.remove(em);
    }
}
