package com.vishavbangotra.fraud_detection_system.streaming;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LiveFeedService {

    public enum Stream { SCORED, FLAGGED }

    static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    private final List<SseEmitter> scoredEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> flaggedEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter register(Stream stream) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> bucket = bucketFor(stream);
        bucket.add(emitter);

        Runnable remove = () -> bucket.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            emitter.complete();
            remove.run();
        });
        emitter.onError(e -> remove.run());

        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            bucket.remove(emitter);
        }
        return emitter;
    }

    public void broadcastScored(ScoredTransaction txn) {
        send(scoredEmitters, "scored", txn);
    }

    public void broadcastFlagged(ScoredTransaction txn) {
        send(flaggedEmitters, "flagged", txn);
    }

    private void send(List<SseEmitter> emitters, String event, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping dead SSE emitter on event {}: {}", event, e.getMessage());
                emitters.remove(emitter);
            }
        }
    }

    private List<SseEmitter> bucketFor(Stream stream) {
        return switch (stream) {
            case SCORED -> scoredEmitters;
            case FLAGGED -> flaggedEmitters;
        };
    }

    int scoredEmitterCount() { return scoredEmitters.size(); }
    int flaggedEmitterCount() { return flaggedEmitters.size(); }
}
