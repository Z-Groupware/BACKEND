package com.module06.backend.meeting.infrastructure.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.module06.backend.meeting.application.port.out.ProjectQueryPort;

/*
 * 프로젝트 도메인의 정식 조회 Port가 연결되기 전까지 애플리케이션 컨텍스트를 명시적으로 유지하는 어댑터다.
 *
 * 존재하는 것처럼 응답하는 조용한 fallback을 만들지 않고, 호출되면 미연동 상태를 즉시 드러낸다.
 */
@Component
public class PendingProjectQueryAdapter implements ProjectQueryPort {

    /* 프로젝트 담당 도메인의 조회 계약이 아직 연결되지 않았음을 명시적으로 알린다. */
    @Override
    public boolean existsActiveProject(Long companyId, Long projectId) {
        /* 잘못된 통과보다 빠른 실패를 선택해 타 회사 프로젝트가 예약에 연결되는 일을 막는다. */
        throw new UnsupportedOperationException(
                "ProjectQueryPort 연동 대기 중입니다. C(project) 도메인의 회사 범위 조회 구현이 필요합니다."
        );
    }

    /* 프로젝트 담당 도메인의 표시 정보 배치 조회 계약이 아직 연결되지 않았음을 알린다. */
    @Override
    public List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds) {
        /* 임의 태그나 색상을 반환하지 않고 C도메인의 실제 조회 Adapter 연결을 요구한다. */
        throw new UnsupportedOperationException(
                "ProjectQueryPort 연동 대기 중입니다. C(project) 도메인의 프로젝트 배치 조회 구현이 필요합니다."
        );
    }
}
