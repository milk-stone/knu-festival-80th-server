package kr.ac.knu.festival.application.booth;

import kr.ac.knu.festival.domain.booth.entity.Booth;
import kr.ac.knu.festival.domain.booth.entity.Menu;
import kr.ac.knu.festival.domain.booth.repository.BoothRepository;
import kr.ac.knu.festival.domain.booth.repository.MenuRepository;
import kr.ac.knu.festival.domain.waiting.entity.WaitingStatus;
import kr.ac.knu.festival.domain.waiting.repository.WaitingRepository;
import kr.ac.knu.festival.global.exception.BusinessErrorCode;
import kr.ac.knu.festival.global.exception.BusinessException;
import kr.ac.knu.festival.infra.redis.BoothRankingRedisRepository;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothDetailResponse;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothListResponse;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothQueryService {

    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);
    private final BoothRepository boothRepository;
    private final MenuRepository menuRepository;
    private final WaitingRepository waitingRepository;
    private final BoothRankingService boothRankingService;
    private final BoothRankingRedisRepository boothRankingRedisRepository;

    public List<BoothListResponse> getBooths(String sort) {
        return boothRankingService.getBooths(sort);
    }

    public BoothDetailResponse getBooth(Long boothId) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        List<Menu> menus = menuRepository.findAllByBoothIdOrderByIdAsc(boothId);
        long activeWaiting = waitingRepository.countByBoothIdAndStatusIn(boothId, ACTIVE_STATUSES);
        int likeCount = boothRankingRedisRepository.getLikeCount(boothId, booth.getLikeCount());
        return BoothDetailResponse.of(booth, menus, activeWaiting, likeCount);
    }

    public List<BoothMapResponse> getBoothsForMap() {
        return boothRepository.findAll().stream()
                .map(BoothMapResponse::fromEntity)
                .toList();
    }

}
