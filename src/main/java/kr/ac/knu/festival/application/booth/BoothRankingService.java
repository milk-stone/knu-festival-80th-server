package kr.ac.knu.festival.application.booth;

import kr.ac.knu.festival.domain.booth.entity.Booth;
import kr.ac.knu.festival.domain.booth.repository.BoothRepository;
import kr.ac.knu.festival.domain.waiting.entity.WaitingStatus;
import kr.ac.knu.festival.domain.waiting.repository.WaitingRepository;
import kr.ac.knu.festival.infra.redis.BoothRankingRedisRepository;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothListResponse;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothRankingItemResponse;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothRankingSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothRankingService {

    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);

    private final BoothRepository boothRepository;
    private final WaitingRepository waitingRepository;
    private final BoothRankingRedisRepository boothRankingRedisRepository;

    public List<BoothListResponse> getBooths(String sortValue) {
        BoothRankingSort sort = BoothRankingSort.from(sortValue);
        List<BoothRankingRow> rows = loadRows();
        return sortRows(rows, sort).stream()
                .map(row -> BoothListResponse.fromEntity(row.booth(), row.currentWaitingTeams(), row.likeCount()))
                .toList();
    }

    public BoothRankingSnapshotResponse getSnapshot(BoothRankingSort sort) {
        List<BoothRankingRow> rows = sortRows(loadRows(), sort);
        List<BoothRankingItemResponse> items = java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> {
                    BoothRankingRow row = rows.get(index);
                    return new BoothRankingItemResponse(
                            row.booth().getId(),
                            index + 1,
                            row.likeCount(),
                            row.currentWaitingTeams()
                    );
                })
                .toList();
        return new BoothRankingSnapshotResponse(sort.value(), items);
    }

    private List<BoothRankingRow> loadRows() {
        List<Booth> booths = boothRepository.findAll();
        List<Long> boothIds = booths.stream().map(Booth::getId).toList();
        Map<Long, Integer> redisLikeCounts = boothRankingRedisRepository.getLikeCounts(boothIds);
        Map<Long, Integer> redisWaitingCounts = boothRankingRedisRepository.getWaitingCounts(boothIds);
        Map<Long, Long> dbWaitingCounts = redisWaitingCounts.size() == boothIds.size()
                ? Map.of()
                : loadActiveCountMap();

        return booths.stream()
                .map(booth -> new BoothRankingRow(
                        booth,
                        redisLikeCounts.getOrDefault(booth.getId(), booth.getLikeCount()),
                        redisWaitingCounts.containsKey(booth.getId())
                                ? redisWaitingCounts.get(booth.getId())
                                : dbWaitingCounts.getOrDefault(booth.getId(), 0L)
                ))
                .toList();
    }

    private List<BoothRankingRow> sortRows(List<BoothRankingRow> rows, BoothRankingSort sort) {
        return rows.stream()
                .sorted(comparator(sort))
                .toList();
    }

    private Comparator<BoothRankingRow> comparator(BoothRankingSort sort) {
        return switch (sort) {
            case WAITING_ASC -> Comparator.comparingLong(BoothRankingRow::currentWaitingTeams)
                    .thenComparing(Comparator.comparingInt(BoothRankingRow::likeCount).reversed())
                    .thenComparing(row -> row.booth().getName());
            case NAME_ASC -> Comparator.comparing(row -> row.booth().getName());
            case LIKES -> Comparator.comparingInt(BoothRankingRow::likeCount).reversed()
                    .thenComparingLong(BoothRankingRow::currentWaitingTeams)
                    .thenComparing(row -> row.booth().getName());
        };
    }

    private Map<Long, Long> loadActiveCountMap() {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : waitingRepository.countActiveByBooth(ACTIVE_STATUSES)) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private record BoothRankingRow(
            Booth booth,
            int likeCount,
            long currentWaitingTeams
    ) {
    }
}
