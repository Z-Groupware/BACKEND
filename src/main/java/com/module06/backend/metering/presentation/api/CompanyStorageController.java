package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.metering.application.usecase.DeleteProjectStorageUseCase;
import com.module06.backend.metering.application.usecase.GetStorageOverviewUseCase;
import com.module06.backend.metering.presentation.api.dto.response.StorageOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 저장소 관리 화면(/manage/storage) 전용 컨트롤러 — company 도메인이 따로 없어 이 화면을 소유한
// metering 패키지에 둔다(CompanyStoragePlanService와 같은 패키지, 같은 owner/admin 스코프).
@Tag(name = "CompanyStorage", description = "저장소 관리(/manage/storage) API")
@RestController
@RequestMapping("/api/companies/me")
public class CompanyStorageController {

    private final GetStorageOverviewUseCase getStorageOverviewUseCase;
    private final DeleteProjectStorageUseCase deleteProjectStorageUseCase;

    public CompanyStorageController(GetStorageOverviewUseCase getStorageOverviewUseCase,
                                    DeleteProjectStorageUseCase deleteProjectStorageUseCase) {
        this.getStorageOverviewUseCase = getStorageOverviewUseCase;
        this.deleteProjectStorageUseCase = deleteProjectStorageUseCase;
    }

    @Operation(
            summary = "저장소 현황 조회",
            description = "회사 전체 + 프로젝트별(지금 녹음이 남아있는 프로젝트만) 음성·자막·요약 저장 "
                    + "용량을 조회한다. Owner/Admin만 가능하다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @GetMapping("/storage")
    public ApiResponse<StorageOverviewResponse> getStorage(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        return ApiResponse.success("저장소 현황을 조회했습니다.",
                StorageOverviewResponse.from(getStorageOverviewUseCase.getOverview(companyId)));
    }

    @Operation(
            summary = "프로젝트별 저장 기록 삭제",
            description = "완료(DONE)된 프로젝트의 음성, 자막, 요약 저장 기록을 삭제합니다. Owner/Admin만 가능합니다."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @DeleteMapping("/storage/projects/{tag}")
    public ApiResponse<Void> deleteProjectStorage(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable String tag) {
        deleteProjectStorageUseCase.deleteByTag(companyId, tag);
        return ApiResponse.successWithoutData("프로젝트 저장 기록을 삭제했습니다.");
    }
}
