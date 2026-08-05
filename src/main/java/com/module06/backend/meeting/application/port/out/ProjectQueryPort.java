package com.module06.backend.meeting.application.port.out;

import java.util.List;

/*
 * MEET-01이 프로젝트 존재와 회사 소속을 검증하는 아웃바운드 포트다.
 *
 * 실제 구현은 프로젝트 도메인이 공개하는 조회 계약과 연결돼야 한다.
 */
public interface ProjectQueryPort {

    /* 요청 회사에 속하고 삭제되지 않은 프로젝트인지 확인한다. */
    boolean existsActiveProject(Long companyId, Long projectId);

    /* 예정 회의 카드에 필요한 프로젝트 표시 정보를 회사 범위에서 일괄 조회한다. */
    List<ProjectSnapshot> findProjects(Long companyId, List<Long> projectIds);

    /* MEET-03 카드에 필요한 프로젝트 식별자와 표시 정보다. */
    record ProjectSnapshot(Long projectId, String tag, String name, String color) {
    }
}
