package com.module06.backend.capture.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.module06.backend.capture.infrastructure.persistence.entity.MeetingSummaryJpaEntity;

public interface SpringDataMeetingSummaryRepository extends JpaRepository<MeetingSummaryJpaEntity, Long> {

    /*
     * 조회용 — 회사 스코프를 **조건에 넣는다.**
     *
     * meetingId 만으로 찾으면 다른 회사 회의의 요약과 근거 발화 id 가 그대로 나간다.
     * 처리 상태 같은 메타데이터가 아니라 **회의 내용**이다(CWE-639 · CodeRabbit PR #85 지적).
     * meeting_summary 에 company_id 가 있어(V5.7) 조인 없이 막을 수 있다.
     */
    Optional<MeetingSummaryJpaEntity> findByMeetingIdAndCompanyId(Long meetingId, Long companyId);

    /*
     * 쓰기용(upsert) — 회사 조건을 **일부러 넣지 않는다.**
     *
     * UNIQUE(meeting_id) 라 회의당 요약은 하나다. 여기에 회사 조건을 걸면 기존 행을 못 찾고
     * INSERT 로 가서 제약 위반으로 터진다. 회의가 어느 회사 것인지는 호출자(오케스트레이터)가
     * 이미 알고 들어온다.
     *
     * ⚠ 다만 그 호출자도 "이 회의가 그 회사 것인가"를 검증하지 않는다 — 조회 쪽과 같은 뿌리이고
     *   이슈 #100(공통 회의 소유권 검증)에서 함께 다룬다. 이 메서드는 그 검증을 대신하지 않는다.
     */
    Optional<MeetingSummaryJpaEntity> findByMeetingId(Long meetingId);
}
