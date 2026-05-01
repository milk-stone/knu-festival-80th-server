package kr.ac.knu.festival.presentation.booth.dto.response;

import kr.ac.knu.festival.domain.booth.entity.Booth;

import java.math.BigDecimal;

public record BoothResponse(
        Long boothId,
        String name,
        String description,
        BigDecimal locationLat,
        BigDecimal locationLng,
        int likeCount,
        String imageUrl,
        boolean waitingOpen
) {
    public static BoothResponse fromEntity(Booth booth) {
        return fromEntity(booth, booth.getLikeCount());
    }

    public static BoothResponse fromEntity(Booth booth, int likeCount) {
        return new BoothResponse(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getLocationLat(),
                booth.getLocationLng(),
                likeCount,
                booth.getImageUrl(),
                booth.isWaitingOpen()
        );
    }
}
