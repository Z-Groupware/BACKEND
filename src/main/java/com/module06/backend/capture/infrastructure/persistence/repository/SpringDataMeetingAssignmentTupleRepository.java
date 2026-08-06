package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingAssignmentTupleJpaEntity;

public interface SpringDataMeetingAssignmentTupleRepository
        extends JpaRepository<MeetingAssignmentTupleJpaEntity, Long> {

    /* L4 재실행 시 이전 tuple 을 지운다. 남기면 같은 배정이 두 배로 쌓인다. */
    void deleteByMeetingId(Long meetingId);

    /*
     * L5 검증 대상 조회. 회사 스코프를 조건에 넣는다 — meetingId 만으로 찾으면 다른 회사
     * 회의의 배정이 검증 요청에 실려 나간다.
     *
     * 정렬을 sortOrder 로 고정하는 이유: 검증은 tuple 마다 한 번씩 부르므로 순서가 곧 호출
     * 순서이고, 중간에 실패했을 때 어디까지 봤는지가 순서에 의존한다. 정렬이 없으면 재실행이
     * 다른 순서로 돌아 그 비교가 안 된다.
     */
    List<MeetingAssignmentTupleJpaEntity> findByCompanyIdAndMeetingIdOrderBySortOrderAsc(
            Long companyId, Long meetingId);

    /*
     * 판정을 반영할 행을 찾는다. meetingId 를 함께 거는 것이 핵심이다 — id 만으로 찾으면
     * 다른 회의의 행을 고칠 수 있는 경로가 생긴다(MeetingSummaryPersistenceAdapter 와 같은 짝).
     */
    List<MeetingAssignmentTupleJpaEntity> findByMeetingIdAndIdIn(Long meetingId, Collection<Long> ids);
}
