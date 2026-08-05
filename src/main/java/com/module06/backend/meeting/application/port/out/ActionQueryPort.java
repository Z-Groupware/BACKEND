package com.module06.backend.meeting.application.port.out;

/*
 * 선택 입력인 relatedActionId가 요청 회사의 실제 액션인지 검증하는 아웃바운드 포트다.
 *
 * 액션 엔티티는 C도메인이 소유하므로 회의 도메인은 식별자 존재 여부만 묻는다.
 */
public interface ActionQueryPort {

    /* 요청 회사에 속한 액션이 존재하는지 확인한다. */
    boolean existsAction(Long companyId, Long actionId);
}
