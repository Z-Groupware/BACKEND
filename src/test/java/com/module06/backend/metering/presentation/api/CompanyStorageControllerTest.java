package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.metering.application.result.StorageOverviewResult;
import com.module06.backend.metering.application.usecase.GetStorageOverviewUseCase;
import com.module06.backend.metering.presentation.api.dto.response.StorageOverviewResponse;
import com.module06.backend.project.domain.model.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 저장소 관리 화면(/manage/storage) Controller가 principal의 companyId를 유스케이스에 넘기고,
 * 결과를 FE storage/types.ts 계약(tag 기준, projectId 없음)에 맞는 공통 응답으로 변환하는지
 * 검증하는 단위 테스트다.
 */
@DisplayName("저장소 관리 화면 Controller")
class CompanyStorageControllerTest {

    @Test
    @DisplayName("principal의 companyId로 조회하고 tag 기준 응답을 돌려준다")
    void returnsOverviewScopedToPrincipalCompany() {
        Long[] requestedCompanyId = new Long[1];
        GetStorageOverviewUseCase useCase = companyId -> {
            requestedCompanyId[0] = companyId;
            return new StorageOverviewResult(new BigDecimal("34.9"), new BigDecimal("6.8"), List.of(
                    new StorageOverviewResult.ProjectStorageItem("eng", "엔지니어링", 24L,
                            new BigDecimal("9.1"), new BigDecimal("1.4"),
                            LocalDate.of(2026, 8, 7), ProjectStatus.IN_PROGRESS)));
        };
        CompanyStorageController controller = new CompanyStorageController(useCase);

        ApiResponse<StorageOverviewResponse> response = controller.getStorage(1L);

        assertThat(requestedCompanyId[0]).isEqualTo(1L);
        assertThat(response.getData().voiceGb()).isEqualByComparingTo(new BigDecimal("34.9"));
        assertThat(response.getData().sttGb()).isEqualByComparingTo(new BigDecimal("6.8"));
        assertThat(response.getData().projects()).hasSize(1);
        assertThat(response.getData().projects().get(0).tag()).isEqualTo("eng");
        assertThat(response.getData().projects().get(0).meetingCount()).isEqualTo(24L);
    }
}
