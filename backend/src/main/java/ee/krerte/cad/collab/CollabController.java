package ee.krerte.cad.collab;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * Real-time collaborative design editing via WebSocket/STOMP. Users join a room (design session),
 * and all parameter changes are broadcast to everyone in that room.
 */
@Controller
public class CollabController {

    /** Active rooms: roomId -> set of user names */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> rooms =
            new ConcurrentHashMap<>();

    public record ParamUpdate(String user, String param, double value, long ts) {}

    public record CursorUpdate(String user, String param, String color) {}

    public record RoomEvent(String type, String user, String roomId, int userCount, long ts) {}

    /** Create a new collaboration room, returns room ID. */
    @MessageMapping("/collab/create")
    @SendTo("/topic/collab/created")
    public Map<String, String> createRoom() {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        rooms.put(roomId, new ConcurrentHashMap<>());
        return Map.of("roomId", roomId);
    }

    /** Join an existing room. */
    @MessageMapping("/collab/{roomId}/join")
    @SendTo("/topic/collab/{roomId}/events")
    public RoomEvent joinRoom(@DestinationVariable String roomId, Map<String, String> payload) {
        String user = payload.getOrDefault("user", "Anonüümne");
        rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(user, user);
        int count = rooms.get(roomId).size();
        return new RoomEvent("join", user, roomId, count, Instant.now().toEpochMilli());
    }

    /** Leave a room. */
    @MessageMapping("/collab/{roomId}/leave")
    @SendTo("/topic/collab/{roomId}/events")
    public RoomEvent leaveRoom(@DestinationVariable String roomId, Map<String, String> payload) {
        String user = payload.getOrDefault("user", "Anonüümne");
        var room = rooms.get(roomId);
        if (room != null) room.remove(user);
        int count = room != null ? room.size() : 0;
        if (count == 0) rooms.remove(roomId);
        return new RoomEvent("leave", user, roomId, count, Instant.now().toEpochMilli());
    }

    /** Broadcast parameter change to all room members. */
    @MessageMapping("/collab/{roomId}/param")
    @SendTo("/topic/collab/{roomId}/params")
    public ParamUpdate paramChange(@DestinationVariable String roomId, ParamUpdate update) {
        return update;
    }

    /** Broadcast cursor/focus position (which slider the user is touching). */
    @MessageMapping("/collab/{roomId}/cursor")
    @SendTo("/topic/collab/{roomId}/cursors")
    public CursorUpdate cursorMove(@DestinationVariable String roomId, CursorUpdate update) {
        return update;
    }
}
