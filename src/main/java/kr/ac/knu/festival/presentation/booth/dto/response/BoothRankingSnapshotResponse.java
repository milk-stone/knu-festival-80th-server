package kr.ac.knu.festival.presentation.booth.dto.response;

import java.util.List;

public record BoothRankingSnapshotResponse(
        String sort,
        List<BoothRankingItemResponse> items
) {
}
