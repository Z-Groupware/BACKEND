package com.module06.backend.meeting.application.port.out;

import java.util.List;

/*
 * MEET-01이 참석자 유효성과 표시 이름을 일괄 조회하는 아웃바운드 포트다.
 *
 * 이름과 팀 이름의 원본은 구성원 도메인이므로 회의 도메인은 값을 저장하거나 캐시하지 않는다.
 */
public interface MemberQueryPort {

    /* 요청 회사에 속하고 삭제되지 않은 구성원을 식별자 목록으로 한 번에 조회한다. */
    List<MemberSnapshot> findActiveMembers(Long companyId, List<Long> memberIds);

    /* 회의 개설 응답과 향후 참석자 계약에 사용하는 구성원 읽기 모델이다. */
    record MemberSnapshot(
            Long memberId,
            String name,
            Long teamId,
            String teamName
    ) {
    }
}
