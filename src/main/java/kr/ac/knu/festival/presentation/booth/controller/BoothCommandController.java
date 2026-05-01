package kr.ac.knu.festival.presentation.booth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.ac.knu.festival.application.booth.BoothCommandService;
import kr.ac.knu.festival.application.booth.BoothQueryService;
import kr.ac.knu.festival.global.auth.AdminInfo;
import kr.ac.knu.festival.global.auth.CurrentAdmin;
import kr.ac.knu.festival.global.response.ApiResponse;
import kr.ac.knu.festival.infra.security.AnonymousIdCookieManager;
import kr.ac.knu.festival.presentation.booth.controller.docs.BoothCommandControllerDocs;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothCreateRequest;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothPasswordChangeRequest;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothUpdateRequest;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothListResponse;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class BoothCommandController implements BoothCommandControllerDocs {

    private final BoothCommandService boothCommandService;
    private final BoothQueryService boothQueryService;
    private final AnonymousIdCookieManager anonymousIdCookieManager;

    @Override
    @PostMapping("/admin/v1/booths")
    public ResponseEntity<ApiResponse<BoothResponse>> createBooth(
            @CurrentAdmin AdminInfo admin,
            @RequestBody @Valid BoothCreateRequest request
    ) {
        admin.requireSuperAdmin();
        BoothResponse result = boothCommandService.createBooth(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @Override
    @PutMapping("/admin/v1/booths/{booth-id}")
    public ResponseEntity<ApiResponse<BoothResponse>> updateBooth(
            @CurrentAdmin AdminInfo admin,
            @PathVariable("booth-id") Long boothId,
            @RequestBody @Valid BoothUpdateRequest request
    ) {
        admin.validateBoothAccess(boothId);
        return ResponseEntity.ok(ApiResponse.success(boothCommandService.updateBooth(boothId, request)));
    }

    @Override
    @DeleteMapping("/admin/v1/booths/{booth-id}")
    public ResponseEntity<ApiResponse<Void>> deleteBooth(
            @CurrentAdmin AdminInfo admin,
            @PathVariable("booth-id") Long boothId
    ) {
        admin.requireSuperAdmin();
        boothCommandService.deleteBooth(boothId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Override
    @PatchMapping("/admin/v1/booths/{booth-id}/password")
    public ResponseEntity<ApiResponse<Void>> changeBoothPassword(
            @CurrentAdmin AdminInfo admin,
            @PathVariable("booth-id") Long boothId,
            @RequestBody @Valid BoothPasswordChangeRequest request
    ) {
        admin.requireSuperAdmin();
        boothCommandService.changeBoothPassword(boothId, request.newPassword());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Override
    @GetMapping("/admin/v1/booths")
    public ResponseEntity<ApiResponse<List<BoothListResponse>>> getBoothsForAdmin(
            @CurrentAdmin AdminInfo admin,
            @RequestParam(value = "sort", required = false, defaultValue = "likes") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.success(boothQueryService.getBooths(sort)));
    }

    @Override
    @PostMapping("/api/v1/booths/{booth-id}/likes")
    public ResponseEntity<ApiResponse<BoothResponse>> likeBooth(
            @PathVariable("booth-id") Long boothId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String anonymousIdHash = anonymousIdCookieManager.getOrCreateHashedAnonymousId(request, response);
        return ResponseEntity.ok(ApiResponse.success(boothCommandService.likeBooth(boothId, anonymousIdHash)));
    }

    @Override
    @DeleteMapping("/api/v1/booths/{booth-id}/likes")
    public ResponseEntity<ApiResponse<BoothResponse>> unlikeBooth(
            @PathVariable("booth-id") Long boothId,
            HttpServletRequest request
    ) {
        String anonymousIdHash = anonymousIdCookieManager.getHashedAnonymousId(request);
        return ResponseEntity.ok(ApiResponse.success(boothCommandService.unlikeBooth(boothId, anonymousIdHash)));
    }
}
