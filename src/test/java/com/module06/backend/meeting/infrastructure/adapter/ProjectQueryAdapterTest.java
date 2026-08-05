package com.module06.backend.meeting.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;

/*
 * C 프로젝트 조회 계약과 D 회의 출력 Port 사이의 인자 전달 및 읽기 모델 변환을 검증한다.
 */
@DisplayName("프로젝트 조회 연동 어댑터")
class ProjectQueryAdapterTest {

    /* MEET-01의 회사·프로젝트 식별자가 C Port에 그대로 전달되는지 확인한다. */
    @Test
    @DisplayName("활성 프로젝트 검증을 회사 범위와 함께 프로젝트 도메인에 위임한다")
    void delegatesActiveProjectValidation() {
        /* C Port에 실제 전달된 회사와 프로젝트 식별자를 기록할 공간을 준비한다. */
        Long[] capturedCompanyId = new Long[1];
        Long[] capturedProjectId = new Long[1];

        /* 활성 검증 인자를 기록하고 true를 반환하는 프로젝트 도메인 대역을 만든다. */
        com.module06.backend.project.application.port.ProjectQueryPort projectPort =
                new com.module06.backend.project.application.port.ProjectQueryPort() {
                    /* 회사와 프로젝트 식별자를 기록해 D 어댑터의 위임 계약을 검증한다. */
                    @Override
                    public boolean existsActiveProject(Long companyId, Long projectId) {
                        /* 호출 인자를 테스트가 확인할 수 있도록 각각 저장한다. */
                        capturedCompanyId[0] = companyId;
                        capturedProjectId[0] = projectId;
                        return true;
                    }

                    /* 배치 조회는 활성 검증 테스트에서 사용하지 않으므로 호출되면 실패한다. */
                    @Override
                    public List<ProjectSummary> findProjects(Long companyId, List<Long> projectIds) {
                        /* 잘못된 연동 경로를 즉시 드러내기 위해 테스트 실패를 발생시킨다. */
                        throw new AssertionError("활성 프로젝트 검증에서 배치 조회를 호출하면 안 됩니다.");
                    }
                };

        /* 실제 연결 어댑터로 MEET-01 활성 프로젝트 검증을 실행한다. */
        ProjectQueryAdapter adapter = new ProjectQueryAdapter(projectPort);
        boolean result = adapter.existsActiveProject(10L, 27L);

        /* C Port의 반환값과 전달된 테넌트·프로젝트 식별자가 모두 일치해야 한다. */
        assertThat(result).isTrue();
        assertThat(capturedCompanyId[0]).isEqualTo(10L);
        assertThat(capturedProjectId[0]).isEqualTo(27L);
    }

    /* MEET-03 배치 결과가 필드 손실 없이 D의 ProjectSnapshot으로 변환되는지 확인한다. */
    @Test
    @DisplayName("프로젝트 배치 결과를 예정 회의 카드 표시 정보로 변환한다")
    void mapsProjectBatchResult() {
        /* C Port에 실제 전달된 프로젝트 식별자 목록을 기록할 공간을 준비한다. */
        AtomicReference<List<Long>> capturedProjectIds = new AtomicReference<>();

        /* 두 프로젝트 표시 정보를 반환하는 프로젝트 도메인 대역을 만든다. */
        com.module06.backend.project.application.port.ProjectQueryPort projectPort =
                new com.module06.backend.project.application.port.ProjectQueryPort() {
                    /* 활성 검증은 배치 변환 테스트에서 사용하지 않으므로 호출되면 실패한다. */
                    @Override
                    public boolean existsActiveProject(Long companyId, Long projectId) {
                        /* 잘못된 연동 경로를 즉시 드러내기 위해 테스트 실패를 발생시킨다. */
                        throw new AssertionError("프로젝트 배치 조회에서 활성 검증을 호출하면 안 됩니다.");
                    }

                    /* 요청 식별자를 기록하고 C 도메인의 프로젝트 표시 결과를 반환한다. */
                    @Override
                    public List<ProjectSummary> findProjects(Long companyId, List<Long> projectIds) {
                        /* Adapter가 전달한 배치 식별자를 외부 변경 없이 확인할 수 있도록 복사한다. */
                        capturedProjectIds.set(List.copyOf(projectIds));
                        return List.of(
                                new ProjectSummary(27L, "ZGW", "잇다", "#6C5CE7"),
                                new ProjectSummary(31L, "PAY", "결제 고도화", "#00B894")
                        );
                    }
                };

        /* 실제 연결 어댑터로 MEET-03 프로젝트 배치 표시 정보를 조회한다. */
        ProjectQueryAdapter adapter = new ProjectQueryAdapter(projectPort);
        List<ProjectSnapshot> result = adapter.findProjects(10L, List.of(27L, 31L));

        /* 요청 식별자와 C의 반환 순서가 D의 배치 계약에서도 그대로 유지돼야 한다. */
        assertThat(capturedProjectIds.get()).containsExactly(27L, 31L);
        assertThat(result).containsExactly(
                new ProjectSnapshot(27L, "ZGW", "잇다", "#6C5CE7"),
                new ProjectSnapshot(31L, "PAY", "결제 고도화", "#00B894")
        );
    }
}
