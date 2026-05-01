package kr.ac.knu.festival.application.booth;

import kr.ac.knu.festival.domain.booth.entity.Booth;
import kr.ac.knu.festival.domain.booth.repository.BoothRepository;
import kr.ac.knu.festival.domain.waiting.entity.WaitingStatus;
import kr.ac.knu.festival.domain.waiting.repository.WaitingRepository;
import kr.ac.knu.festival.global.exception.BusinessErrorCode;
import kr.ac.knu.festival.global.exception.BusinessException;
import kr.ac.knu.festival.infra.redis.BoothRankingRedisRepository;
import kr.ac.knu.festival.infra.redis.BoothRankingRedisRepository.RedisChangeResult;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothCreateRequest;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothUpdateRequest;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothCommandService {

    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);

    private final BoothRepository boothRepository;
    private final WaitingRepository waitingRepository;
    private final PasswordEncoder passwordEncoder;
    private final BoothRankingRedisRepository boothRankingRedisRepository;
    private final BoothRankingStreamService boothRankingStreamService;

    public BoothResponse createBooth(BoothCreateRequest request) {
        Booth booth = Booth.createBooth(
                request.name(),
                request.description(),
                request.locationLat(),
                request.locationLng(),
                request.imageUrl(),
                passwordEncoder.encode(request.adminPassword())
        );
        return BoothResponse.fromEntity(boothRepository.save(booth));
    }

    public BoothResponse updateBooth(Long boothId, BoothUpdateRequest request) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        booth.updateBooth(
                request.name(),
                request.description(),
                request.locationLat(),
                request.locationLng(),
                request.imageUrl()
        );
        return BoothResponse.fromEntity(booth);
    }

    public void deleteBooth(Long boothId) {
        Booth booth = boothRepository.findByIdForUpdate(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        long activeCount = waitingRepository.countByBoothIdAndStatusIn(boothId, ACTIVE_STATUSES);
        if (activeCount > 0) {
            throw new BusinessException(BusinessErrorCode.BOOTH_HAS_ACTIVE_WAITINGS);
        }
        boothRepository.delete(booth);
    }

    public void changeBoothPassword(Long boothId, String newRawPassword) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        booth.changeAdminPassword(passwordEncoder.encode(newRawPassword));
    }

    public BoothResponse likeBooth(Long boothId, String anonymousIdHash) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        RedisChangeResult result = boothRankingRedisRepository.addLike(boothId, anonymousIdHash);
        if (!result.available()) {
            boothRepository.incrementLike(boothId);
            boothRankingStreamService.markDirty();
            Booth updatedBooth = boothRepository.findById(boothId)
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
            return BoothResponse.fromEntity(updatedBooth);
        }
        if (result.changed()) {
            boothRankingStreamService.markDirty();
        }
        int likeCount = boothRankingRedisRepository.getLikeCount(boothId, booth.getLikeCount());
        return BoothResponse.fromEntity(booth, likeCount);
    }

    public BoothResponse unlikeBooth(Long boothId, String anonymousIdHash) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        if (anonymousIdHash == null) {
            return BoothResponse.fromEntity(booth, boothRankingRedisRepository.getLikeCount(boothId, booth.getLikeCount()));
        }
        RedisChangeResult result = boothRankingRedisRepository.removeLike(boothId, anonymousIdHash);
        if (!result.available()) {
            return BoothResponse.fromEntity(booth);
        }
        if (result.changed()) {
            boothRankingStreamService.markDirty();
        }
        int likeCount = boothRankingRedisRepository.getLikeCount(boothId, booth.getLikeCount());
        return BoothResponse.fromEntity(booth, likeCount);
    }
}
