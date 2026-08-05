package com.module06.backend.meeting.infrastructure.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.application.port.out.ProjectQueryPort;

/*
 * C 프로젝트 도메인의 공개 조회 계약을 D 회의 도메인의 출력 Port에 연결하는 어댑터다.
 *
 * 회의 서비스가 프로젝트 엔티티나 저장소를 직접 참조하지 않도록 두 도메인의 읽기 모델을
 * 이 경계에서 변환하며, 회사 범위와 soft delete 정책은 프로젝트 도메인의 구현에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class ProjectQueryAdapter implements ProjectQueryPort {

    /* 프로젝트 도메인이 소유하는 활성 검증 및 배치 표시 정보 조회 계약이다. */
    private final com.module06.backend.project.application.port.ProjectQueryPort projectQueryPort;

    /* MEET-01이 요청한 회사 범위에서 예약 가능한 활성 프로젝트인지 확인한다. */
    @Override
    public boolean existsActiveProject(Long companyId, Long projectId) {
        /* 활성·회사·soft delete 판정은 원본 데이터를 소유한 프로젝트 도메인에 위임한다. */
        return projectQueryPort.existsActiveProject(companyId, projectId);
    }

    /* MEET-03 예정 회의 카드에 필요한 프로젝트 표시 정보를 한 번에 조회한다. */
    @Override
    public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
        /* C의 배치 결과를 D가 공개한 읽기 모델로 변환해 프로젝트 구현 타입의 전파를 막는다. */
        return projectQueryPort.findProjects(companyId, projectIds).stream()
                .map(project -> new ProjectSnapshot(
                        project.projectId(),
                        project.tag(),
                        project.name(),
                        project.color()
                ))
                .toList();
    }
}
