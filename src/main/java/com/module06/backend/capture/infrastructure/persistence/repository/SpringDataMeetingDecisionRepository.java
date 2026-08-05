package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingDecisionJpaEntity;

public interface SpringDataMeetingDecisionRepository extends JpaRepository<MeetingDecisionJpaEntity, Long> {

    List<MeetingDecisionJpaEntity> findByMeetingIdOrderByTopicSeqAscSortOrderAsc(Long meetingId);

    /*
     * 재실행 시 이전 산출물을 지운다. 남기면 두 번 돌린 회의에 항목이 두 배로 쌓이고,
     * 사용자는 같은 결정을 두 번 보게 된다.
     */
    void deleteByMeetingId(Long meetingId);
}
