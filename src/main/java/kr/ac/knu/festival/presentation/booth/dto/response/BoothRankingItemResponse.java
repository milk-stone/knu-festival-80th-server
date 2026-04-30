package kr.ac.knu.festival.presentation.booth.dto.response;

public record BoothRankingItemResponse(
        Long boothId,
        int rank,
        int likeCount,
        long currentWaitingTeams
) {
}
