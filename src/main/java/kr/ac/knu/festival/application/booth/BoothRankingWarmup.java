package kr.ac.knu.festival.application.booth;

import kr.ac.knu.festival.domain.booth.entity.Booth;
import kr.ac.knu.festival.domain.booth.repository.BoothRepository;
import kr.ac.knu.festival.domain.waiting.entity.WaitingStatus;
import kr.ac.knu.festival.domain.waiting.repository.WaitingRepository;
import kr.ac.knu.festival.infra.redis.BoothRankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BoothRankingWarmup {

    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);

    private final BoothRepository boothRepository;
    private final WaitingRepository waitingRepository;
    private final BoothRankingRedisRepository boothRankingRedisRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmup() {
        List<Booth> booths = boothRepository.findAll();
        Map<Long, Integer> likeCounts = new HashMap<>();
        Map<Long, Long> waitingCounts = new HashMap<>();
        for (Booth booth : booths) {
            likeCounts.put(booth.getId(), booth.getLikeCount());
            waitingCounts.put(booth.getId(), 0L);
        }
        for (Object[] row : waitingRepository.countActiveByBooth(ACTIVE_STATUSES)) {
            waitingCounts.put((Long) row[0], (Long) row[1]);
        }
        boothRankingRedisRepository.setLikes(likeCounts);
        boothRankingRedisRepository.setWaitingCounts(waitingCounts);
        log.info("Booth ranking Redis warm-up completed. boothCount={}", booths.size());
    }
}
