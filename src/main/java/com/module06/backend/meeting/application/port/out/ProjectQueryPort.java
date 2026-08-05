package com.module06.backend.meeting.application.port.out;

/*
 * MEET-01이 프로젝트 존재와 회사 소속을 검증하는 아웃바운드 포트다.
 *
 * 실제 구현은 프로젝트 도메인이 공개하는 조회 계약과 연결돼야 한다.
 */
public interface ProjectQueryPort {

    /* 요청 회사에 속하고 삭제되지 않은 프로젝트인지 확인한다. */
    boolean existsActiveProject(Long companyId, Long projectId);
}
