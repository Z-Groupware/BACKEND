package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.metering.application.result.StorageOverviewResult;
import com.module06.backend.metering.application.usecase.DeleteProjectStorageUseCase;
import com.module06.backend.metering.application.usecase.GetStorageOverviewUseCase;
import com.module06.backend.metering.presentation.api.dto.response.StorageOverviewResponse;
import com.module06.backend.project.domain.model.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("저장소 관리 화면 Controller")
class CompanyStorageControllerTest {

    @Test
    @DisplayName("principal의 companyId로 조회하고 tag 기준 응답을 돌려준다")
    void returnsOverviewScopedToPrincipalCompany() {
        Long[] requestedCompanyId = new Long[1];
        GetStorageOverviewUseCase useCase = companyId -> {
            requestedCompanyId[0] = companyId;
            return new StorageOverviewResult(new BigDecimal("34.9"), new BigDecimal("6.8"), List.of(
                    new StorageOverviewResult.ProjectStorageItem("eng", "영어팀", 24L,
                            new BigDecimal("9.1"), new BigDecimal("1.4"),
                            LocalDate.of(2026, 8, 7), ProjectStatus.IN_PROGRESS)));
        };
        CompanyStorageController controller = new CompanyStorageController(useCase, (companyId, tag) -> {
        });

        ApiResponse<StorageOverviewResponse> response = controller.getStorage(1L);

        assertThat(requestedCompanyId[0]).isEqualTo(1L);
        assertThat(response.getData().voiceGb()).isEqualByComparingTo(new BigDecimal("34.9"));
        assertThat(response.getData().sttGb()).isEqualByComparingTo(new BigDecimal("6.8"));
        assertThat(response.getData().projects()).hasSize(1);
        assertThat(response.getData().projects().get(0).tag()).isEqualTo("eng");
        assertThat(response.getData().projects().get(0).meetingCount()).isEqualTo(24L);
    }

    @Test
    @DisplayName("삭제 요청은 principal companyId와 tag를 usecase에 전달하고 data 없는 성공 응답을 돌려준다")
    void deleteProjectStorageDelegatesCompanyIdAndTag() {
        Long[] requestedCompanyId = new Long[1];
        String[] requestedTag = new String[1];
        DeleteProjectStorageUseCase deleteUseCase = (companyId, tag) -> {
            requestedCompanyId[0] = companyId;
            requestedTag[0] = tag;
        };
        CompanyStorageController controller = new CompanyStorageController(companyId ->
                new StorageOverviewResult(BigDecimal.ZERO, BigDecimal.ZERO, List.of()), deleteUseCase);

        ApiResponse<Void> response = controller.deleteProjectStorage(7L, "eng");

        assertThat(requestedCompanyId[0]).isEqualTo(7L);
        assertThat(requestedTag[0]).isEqualTo("eng");
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isNull();
    }
}
