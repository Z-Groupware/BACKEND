package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * meeting_summary(V5.7) 매핑이다. 회의당 1건(UNIQUE(meeting_id)).
 *
 * ⚠ 소유권 미결 — 이 테이블을 A(이태연)와 D(모성진) 중 누가 쓰는지 아직 정해지지 않았다
 * (V5.7 주석). 포트 뒤에 두었으므로 소유가 넘어가도 오케스트레이터는 바뀌지 않는다.
 *
 * company_id 는 의도적 반정규화다(baseline meeting 과 같은 방식). 테넌트 스코프 조회가
 * 회의 테이블 조인 없이 되어야 한다.
 */
@Entity
@Table(name = "meeting_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingSummaryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "overview", columnDefinition = "TEXT")
    private String overview;

    @Column(name = "edited_by_member_id")
    private Long editedByMemberId;

    /* NULL 이면 AI 생성 원본 그대로다. ANLZ-04 로 사람이 고치면 채워진다. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "model_name", length = 60)
    private String modelName;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    public static MeetingSummaryJpaEntity of(long companyId, long meetingId, String overview,
                                             String modelName, String promptVersion) {
        MeetingSummaryJpaEntity entity = new MeetingSummaryJpaEntity();
        entity.companyId = companyId;
        entity.meetingId = meetingId;
        entity.overview = overview;
        entity.modelName = modelName;
        entity.promptVersion = promptVersion;
        return entity;
    }

    /*
     * AI 산출물로 덮는다. edited_by_member_id·edited_at 은 건드리지 않는다 —
     * 사람이 손댔다는 사실은 재실행으로 지워질 값이 아니다. 다만 재실행이 사람 수정을
     * 실제로 덮어써도 되는지는 ANLZ-04 가 붙을 때 정한다(이 슬라이스 범위 밖).
     */
    public void overwrite(String overview, String modelName, String promptVersion) {
        this.overview = overview;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
    }
}
