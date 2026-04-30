package kr.ac.knu.festival.presentation.booth.dto.response;

import kr.ac.knu.festival.domain.booth.entity.Booth;

import java.math.BigDecimal;

public record BoothListResponse(
        Long boothId,
        String name,
        String description,
        BigDecimal locationLat,
        BigDecimal locationLng,
        int likeCount,
        String imageUrl,
        boolean waitingOpen,
        long currentWaitingTeams
) {
    public static BoothListResponse fromEntity(Booth booth, long currentWaitingTeams) {
        return fromEntity(booth, currentWaitingTeams, booth.getLikeCount());
    }

    public static BoothListResponse fromEntity(Booth booth, long currentWaitingTeams, int likeCount) {
        return new BoothListResponse(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getLocationLat(),
                booth.getLocationLng(),
                likeCount,
                booth.getImageUrl(),
                booth.isWaitingOpen(),
                currentWaitingTeams
        );
    }
}
