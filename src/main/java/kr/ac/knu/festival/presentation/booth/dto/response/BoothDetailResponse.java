package kr.ac.knu.festival.presentation.booth.dto.response;

import kr.ac.knu.festival.domain.booth.entity.Booth;
import kr.ac.knu.festival.domain.booth.entity.Menu;

import java.math.BigDecimal;
import java.util.List;

public record BoothDetailResponse(
        Long boothId,
        String name,
        String description,
        BigDecimal locationLat,
        BigDecimal locationLng,
        int likeCount,
        String imageUrl,
        boolean waitingOpen,
        long currentWaitingTeams,
        List<MenuResponse> menus
) {
    public static BoothDetailResponse of(Booth booth, List<Menu> menus, long currentWaitingTeams) {
        return of(booth, menus, currentWaitingTeams, booth.getLikeCount());
    }

    public static BoothDetailResponse of(Booth booth, List<Menu> menus, long currentWaitingTeams, int likeCount) {
        return new BoothDetailResponse(
                booth.getId(),
                booth.getName(),
                booth.getDescription(),
                booth.getLocationLat(),
                booth.getLocationLng(),
                likeCount,
                booth.getImageUrl(),
                booth.isWaitingOpen(),
                currentWaitingTeams,
                menus.stream().map(MenuResponse::fromEntity).toList()
        );
    }
}
