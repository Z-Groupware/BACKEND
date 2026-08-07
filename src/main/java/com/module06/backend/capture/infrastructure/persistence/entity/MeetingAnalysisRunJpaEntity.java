package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * meeting_analysis_run(V5.16) 매핑이다. 회의 하나의 **마지막 실행 번호**만 갖는다.
 *
 * 식별자를 직접 정한다(@GeneratedValue 가 없다). 회의 하나에 한 행이므로 회의 id 가 곧 키이고,
 * 대리키를 두면 "회의당 한 행"을 UNIQUE 로 한 번 더 지켜야 한다.
 *
 * 스키마 주인은 Flyway 다. 이 엔티티는 기존 테이블을 검증하고 읽고 쓰는 역할만 한다.
 */
@Entity
@Table(name = "meeting_analysis_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingAnalysisRunJpaEntity {

    @Id
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "run_seq", nullable = false)
    private long runSeq;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private MeetingAnalysisRunJpaEntity(Long meetingId, LocalDateTime now) {
        this.meetingId = meetingId;
        this.runSeq = 0L;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /* 아직 한 번도 분석하지 않은 회의의 첫 행. 번호는 0 이고, 발급은 issueNext 가 한다. */
    public static MeetingAnalysisRunJpaEntity initial(Long meetingId, LocalDateTime now) {
        return new MeetingAnalysisRunJpaEntity(meetingId, now);
    }

    /*
     * 다음 실행 번호를 발급한다.
     *
     * 값을 되돌려주는 이유 — 발급과 조회를 나누면 그 사이에 다른 실행이 또 발급해 우리가
     * 남의 번호를 자기 것으로 들고 가게 된다. 그 순간 오래된 실행이 최신으로 위장한다.
     */
    public long issueNext(LocalDateTime now) {
        this.runSeq += 1;
        this.updatedAt = now;
        return this.runSeq;
    }
}
