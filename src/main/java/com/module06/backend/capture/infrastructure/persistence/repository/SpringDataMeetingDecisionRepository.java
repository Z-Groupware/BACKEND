package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingDecisionJpaEntity;

public interface SpringDataMeetingDecisionRepository extends JpaRepository<MeetingDecisionJpaEntity, Long> {

    List<MeetingDecisionJpaEntity> findByMeetingIdOrderByTopicSeqAscSortOrderAsc(Long meetingId);

    /*
     * L3.5 판정을 반영할 항목을 가져온다.
     *
     * meetingId 를 조건에 **함께** 넣는다. id 만으로 찾으면 계층 응답이 실어 온 값으로 다른
     * 회의(다른 회사)의 항목을 갱신할 수 있는 경로가 열린다 — 갱신은 성공하므로 아무도
     * 오류를 못 본다. 파생 쿼리로 두는 것은 신규 @Query 금지(QUERY_002) 때문이다.
     */
    List<MeetingDecisionJpaEntity> findByMeetingIdAndIdIn(Long meetingId, Collection<Long> ids);

    /*
     * 재실행 시 이전 산출물을 지운다. 남기면 두 번 돌린 회의에 항목이 두 배로 쌓이고,
     * 사용자는 같은 결정을 두 번 보게 된다.
     */
    void deleteByMeetingId(Long meetingId);
}
