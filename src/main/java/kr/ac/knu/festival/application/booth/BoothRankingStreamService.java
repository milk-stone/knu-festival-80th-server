package kr.ac.knu.festival.application.booth;

import kr.ac.knu.festival.presentation.booth.dto.response.BoothRankingSnapshotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoothRankingStreamService {

    private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    private final BoothRankingService boothRankingService;
    private final ConcurrentHashMap<BoothRankingSort, ConcurrentHashMap<String, SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicBoolean rankingDirty = new AtomicBoolean(false);

    public SseEmitter subscribe(BoothRankingSort sort) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        String emitterId = UUID.randomUUID().toString();
        emitters.computeIfAbsent(sort, ignored -> new ConcurrentHashMap<>()).put(emitterId, emitter);
        emitter.onCompletion(() -> remove(sort, emitterId));
        emitter.onTimeout(() -> remove(sort, emitterId));
        emitter.onError(ignored -> remove(sort, emitterId));
        sendSnapshot(sort, emitterId, emitter);
        return emitter;
    }

    public void markDirty() {
        rankingDirty.set(true);
    }

    @Scheduled(fixedDelay = 500L)
    public void broadcastChangedRankings() {
        if (!rankingDirty.getAndSet(false)) {
            return;
        }
        broadcast(BoothRankingSort.LIKES);
        broadcast(BoothRankingSort.WAITING_ASC);
    }

    @Scheduled(fixedDelay = 30_000L)
    public void sendHeartbeat() {
        for (BoothRankingSort sort : emitters.keySet()) {
            ConcurrentHashMap<String, SseEmitter> sortEmitters = emitters.get(sort);
            if (sortEmitters == null) {
                continue;
            }
            Set<String> emitterIds = Set.copyOf(sortEmitters.keySet());
            for (String emitterId : emitterIds) {
                SseEmitter emitter = sortEmitters.get(emitterId);
                if (emitter == null) {
                    continue;
                }
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(sort, emitterId);
                }
            }
        }
    }

    private void broadcast(BoothRankingSort sort) {
        ConcurrentHashMap<String, SseEmitter> sortEmitters = emitters.get(sort);
        if (sortEmitters == null || sortEmitters.isEmpty()) {
            return;
        }
        BoothRankingSnapshotResponse snapshot = boothRankingService.getSnapshot(sort);
        Set<String> emitterIds = Set.copyOf(sortEmitters.keySet());
        for (String emitterId : emitterIds) {
            SseEmitter emitter = sortEmitters.get(emitterId);
            if (emitter == null) {
                continue;
            }
            try {
                emitter.send(SseEmitter.event().name("ranking").data(snapshot));
            } catch (IOException | IllegalStateException e) {
                remove(sort, emitterId);
            }
        }
    }

    private void sendSnapshot(BoothRankingSort sort, String emitterId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("ranking").data(boothRankingService.getSnapshot(sort)));
        } catch (IOException | IllegalStateException e) {
            remove(sort, emitterId);
        }
    }

    private void remove(BoothRankingSort sort, String emitterId) {
        ConcurrentHashMap<String, SseEmitter> sortEmitters = emitters.get(sort);
        if (sortEmitters != null) {
            sortEmitters.remove(emitterId);
        }
    }
}
