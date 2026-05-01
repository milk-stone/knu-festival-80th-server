package kr.ac.knu.festival.application.waiting;

import kr.ac.knu.festival.domain.booth.entity.Booth;
import kr.ac.knu.festival.domain.booth.repository.BoothRepository;
import kr.ac.knu.festival.domain.waiting.entity.Waiting;
import kr.ac.knu.festival.domain.waiting.entity.WaitingStatus;
import kr.ac.knu.festival.domain.waiting.repository.WaitingRepository;
import kr.ac.knu.festival.global.exception.BusinessErrorCode;
import kr.ac.knu.festival.global.exception.BusinessException;
import kr.ac.knu.festival.application.booth.BoothRankingStreamService;
import kr.ac.knu.festival.infra.redis.BoothRankingRedisRepository;
import kr.ac.knu.festival.infra.security.PhoneLookupHasher;
import kr.ac.knu.festival.infra.security.PhoneNumberEncryptor;
import kr.ac.knu.festival.infra.sms.SmsSender;
import kr.ac.knu.festival.presentation.waiting.dto.request.WaitingCreateRequest;
import kr.ac.knu.festival.presentation.waiting.dto.request.WaitingInsertRequest;
import kr.ac.knu.festival.presentation.waiting.dto.request.WaitingReorderRequest;
import kr.ac.knu.festival.presentation.waiting.dto.response.WaitingRegisterResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@Transactional
public class WaitingCommandService {

    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);
    private static final int MINUTES_PER_TEAM = 5;

    private final BoothRepository boothRepository;
    private final WaitingRepository waitingRepository;
    private final PhoneNumberEncryptor phoneNumberEncryptor;
    private final PhoneLookupHasher phoneLookupHasher;
    private final SmsSender smsSender;
    private final SmsStatusUpdater smsStatusUpdater;
    private final ThreadPoolTaskExecutor smsExecutor;
    private final BoothRankingRedisRepository boothRankingRedisRepository;
    private final BoothRankingStreamService boothRankingStreamService;

    public WaitingCommandService(
            BoothRepository boothRepository,
            WaitingRepository waitingRepository,
            PhoneNumberEncryptor phoneNumberEncryptor,
            PhoneLookupHasher phoneLookupHasher,
            SmsSender smsSender,
            SmsStatusUpdater smsStatusUpdater,
            @Qualifier("smsExecutor") ThreadPoolTaskExecutor smsExecutor,
            BoothRankingRedisRepository boothRankingRedisRepository,
            BoothRankingStreamService boothRankingStreamService
    ) {
        this.boothRepository = boothRepository;
        this.waitingRepository = waitingRepository;
        this.phoneNumberEncryptor = phoneNumberEncryptor;
        this.phoneLookupHasher = phoneLookupHasher;
        this.smsSender = smsSender;
        this.smsStatusUpdater = smsStatusUpdater;
        this.smsExecutor = smsExecutor;
        this.boothRankingRedisRepository = boothRankingRedisRepository;
        this.boothRankingStreamService = boothRankingStreamService;
    }

    public WaitingRegisterResponse registerWaiting(Long boothId, WaitingCreateRequest request) {
        // 부스 행을 SELECT FOR UPDATE 로 잡아 같은 부스의 등록·중복검사·채번을 직렬화한다.
        Booth booth = boothRepository.findByIdForUpdate(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        if (!booth.isWaitingOpen()) {
            throw new BusinessException(BusinessErrorCode.WAITING_REGISTRATION_CLOSED);
        }

        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
        String lookupHash = phoneLookupHasher.hash(normalizedPhone);
        String encryptedPhone = phoneNumberEncryptor.encrypt(normalizedPhone);

        waitingRepository.findFirstActiveByBoothAndPhoneLookupHash(boothId, lookupHash, ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw new BusinessException(BusinessErrorCode.DUPLICATE_WAITING);
                });

        int nextNumber = waitingRepository.findMaxWaitingNumberByBoothId(boothId) + 1;
        int nextSortOrder = waitingRepository.findMaxSortOrderByBoothId(boothId) + 1;

        Waiting saved = waitingRepository.save(Waiting.createWaiting(
                booth, nextNumber, nextSortOrder,
                request.name(), request.partySize(), encryptedPhone, lookupHash
        ));

        long currentWaitingTeams = waitingRepository.countByBoothIdAndStatusIn(boothId, ACTIVE_STATUSES);
        int estimatedWaitMinutes = Math.max(0, (int) (currentWaitingTeams - 1)) * MINUTES_PER_TEAM;
        incrementWaitingCountAfterCommit(boothId);
        return WaitingRegisterResponse.of(saved, booth, currentWaitingTeams, estimatedWaitMinutes);
    }

    public void callWaiting(Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WAITING_NOT_FOUND));
        waiting.markCalled();
        sendCallSmsAsync(waiting.getId(), waiting.getPhoneNumber(), waiting.getBooth().getName(), waiting.getWaitingNumber());
    }

    public void enterWaiting(Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WAITING_NOT_FOUND));
        WaitingStatus previousStatus = waiting.getStatus();
        waiting.markEntered();
        decrementWaitingCountIfBecameInactiveAfterCommit(waiting.getBooth().getId(), previousStatus, waiting.getStatus());
    }

    public void cancelWaiting(Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WAITING_NOT_FOUND));
        WaitingStatus previousStatus = waiting.getStatus();
        waiting.markCancelled();
        decrementWaitingCountIfBecameInactiveAfterCommit(waiting.getBooth().getId(), previousStatus, waiting.getStatus());
    }

    public void cancelWaitingByOwner(Long waitingId, String phoneLast4) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WAITING_NOT_FOUND));
        if (!phoneNumberEncryptor.matchesLast4(waiting.getPhoneNumber(), phoneLast4)) {
            throw new BusinessException(BusinessErrorCode.PHONE_VERIFICATION_FAILED);
        }
        WaitingStatus previousStatus = waiting.getStatus();
        waiting.markCancelled();
        decrementWaitingCountIfBecameInactiveAfterCommit(waiting.getBooth().getId(), previousStatus, waiting.getStatus());
    }

    public void skipWaiting(Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WAITING_NOT_FOUND));
        WaitingStatus previousStatus = waiting.getStatus();
        waiting.markSkipped();
        decrementWaitingCountIfBecameInactiveAfterCommit(waiting.getBooth().getId(), previousStatus, waiting.getStatus());
    }

    public WaitingRegisterResponse insertWaiting(Long boothId, WaitingInsertRequest request) {
        Booth booth = boothRepository.findByIdForUpdate(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));

        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
        String lookupHash = phoneLookupHasher.hash(normalizedPhone);
        String encryptedPhone = phoneNumberEncryptor.encrypt(normalizedPhone);

        waitingRepository.findFirstActiveByBoothAndPhoneLookupHash(boothId, lookupHash, ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw new BusinessException(BusinessErrorCode.DUPLICATE_WAITING);
                });

        int insertAt = request.insertAfterSortOrder();
        waitingRepository.shiftSortOrdersUp(boothId, insertAt); // 단일 UPDATE 로 일괄 시프트

        int nextNumber = waitingRepository.findMaxWaitingNumberByBoothId(boothId) + 1;
        Waiting saved = waitingRepository.save(Waiting.createWaiting(
                booth, nextNumber, insertAt + 1,
                request.name(), request.partySize(), encryptedPhone, lookupHash
        ));

        long currentWaitingTeams = waitingRepository.countByBoothIdAndStatusIn(boothId, ACTIVE_STATUSES);
        int estimatedWaitMinutes = Math.max(0, (int) (currentWaitingTeams - 1)) * MINUTES_PER_TEAM;
        incrementWaitingCountAfterCommit(boothId);
        return WaitingRegisterResponse.of(saved, booth, currentWaitingTeams, estimatedWaitMinutes);
    }

    public void reorderWaiting(Long waitingId, WaitingReorderRequest request) {
        Waiting target = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WAITING_NOT_FOUND));
        Long boothId = target.getBooth().getId();
        // 부스 락으로 같은 부스의 reorder/insert 직렬화
        boothRepository.findByIdForUpdate(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));

        int oldOrder = target.getSortOrder();
        int newOrder = request.newSortOrder();
        if (oldOrder == newOrder) {
            return;
        }

        List<Waiting> all = waitingRepository.findAllByBoothIdOrderBySortOrderAsc(boothId);
        for (Waiting w : all) {
            if (w.getId().equals(target.getId())) {
                continue;
            }
            int order = w.getSortOrder();
            if (oldOrder < newOrder && order > oldOrder && order <= newOrder) {
                w.updateSortOrder(order - 1);
            } else if (newOrder < oldOrder && order >= newOrder && order < oldOrder) {
                w.updateSortOrder(order + 1);
            }
        }
        target.updateSortOrder(newOrder);
    }

    public void toggleBoothWaiting(Long boothId, boolean open) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BOOTH_NOT_FOUND));
        booth.toggleWaitingOpen(open);
    }

    public void resendSms(Long waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.WAITING_NOT_FOUND));
        if (waiting.getStatus() != WaitingStatus.CALLED) {
            throw new BusinessException(BusinessErrorCode.INVALID_WAITING_STATUS_TRANSITION);
        }
        waiting.markSmsFailed();
        sendCallSmsAsync(waiting.getId(), waiting.getPhoneNumber(), waiting.getBooth().getName(), waiting.getWaitingNumber());
    }

    private void sendCallSmsAsync(Long waitingId, String encryptedPhone, String boothName, int waitingNumber) {
        String plainPhone = phoneNumberEncryptor.decrypt(encryptedPhone);
        if (plainPhone == null) {
            log.error("Phone decrypt failed for waiting {}", waitingId);
            return;
        }
        String message = "[%s] %d번 손님, 입장 차례입니다. 5분 내 도착 부탁드립니다.".formatted(boothName, waitingNumber);
        CompletableFuture.runAsync(() -> {
            boolean success = smsSender.send(plainPhone, message);
            if (success) {
                // 별도 빈을 통해 호출 → Spring AOP 프록시로 진입해 @Transactional 적용
                smsStatusUpdater.markSent(waitingId);
            }
        }, smsExecutor).exceptionally(ex -> {
            log.warn("SMS dispatch failed for waiting {}: {}", waitingId, ex.getMessage());
            return null;
        });
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_PHONE_NUMBER);
        }
        return phoneNumber.replaceAll("\\D", "");
    }

    private void incrementWaitingCountAfterCommit(Long boothId) {
        afterCommit(() -> {
            boothRankingRedisRepository.incrementWaitingCount(boothId);
            boothRankingStreamService.markDirty();
        });
    }

    private void decrementWaitingCountIfBecameInactiveAfterCommit(
            Long boothId,
            WaitingStatus previousStatus,
            WaitingStatus currentStatus
    ) {
        if (!previousStatus.isActive() || currentStatus.isActive()) {
            return;
        }
        afterCommit(() -> {
            boothRankingRedisRepository.decrementWaitingCount(boothId);
            boothRankingStreamService.markDirty();
        });
    }

    private void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
