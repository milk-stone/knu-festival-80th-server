package kr.ac.knu.festival.presentation.booth.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ac.knu.festival.global.auth.AdminInfo;
import kr.ac.knu.festival.global.response.ApiResponse;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothCreateRequest;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothPasswordChangeRequest;
import kr.ac.knu.festival.presentation.booth.dto.request.BoothUpdateRequest;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothListResponse;
import kr.ac.knu.festival.presentation.booth.dto.response.BoothResponse;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

@Tag(name = "부스 Command", description = "부스 등록/수정/삭제 API (관리자) + 좋아요 (사용자)")
public interface BoothCommandControllerDocs {

    @Operation(summary = "부스 등록 (슈퍼 관리자)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족")
    })
    ResponseEntity<ApiResponse<BoothResponse>> createBooth(
            @Parameter(hidden = true) AdminInfo admin, BoothCreateRequest request);

    @Operation(summary = "부스 수정")
    ResponseEntity<ApiResponse<BoothResponse>> updateBooth(
            @Parameter(hidden = true) AdminInfo admin, Long boothId, BoothUpdateRequest request);

    @Operation(summary = "부스 삭제 (슈퍼 관리자)")
    ResponseEntity<ApiResponse<Void>> deleteBooth(
            @Parameter(hidden = true) AdminInfo admin, Long boothId);

    @Operation(summary = "부스 비밀번호 변경 (슈퍼 관리자)")
    ResponseEntity<ApiResponse<Void>> changeBoothPassword(
            @Parameter(hidden = true) AdminInfo admin, Long boothId, BoothPasswordChangeRequest request);

    @Operation(summary = "관리자용 부스 목록 조회")
    ResponseEntity<ApiResponse<List<BoothListResponse>>> getBoothsForAdmin(
            @Parameter(hidden = true) AdminInfo admin, String sort);

    @Operation(summary = "부스 좋아요")
    ResponseEntity<ApiResponse<BoothResponse>> likeBooth(Long boothId, HttpServletRequest request, HttpServletResponse response);

    @Operation(summary = "부스 좋아요 취소")
    ResponseEntity<ApiResponse<BoothResponse>> unlikeBooth(Long boothId, HttpServletRequest request);
}
