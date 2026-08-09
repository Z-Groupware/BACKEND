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
     * AI 산출물로 덮는다. **편집 표시도 함께 지운다**(2026-08-09 확정, ANLZ-04).
     *
     * 재실행은 요약·항목을 통째로 교체한다(MeetingSummaryPersistenceAdapter#replace). 그래서
     * 사람이 고친 문장은 어차피 AI 것으로 되돌아가는데, 편집 표시만 남겨 두면 **내용은 AI
     * 것인데 화면은 "사람이 수정함"으로 보이는** 상태가 된다. 그 조합이 제일 나쁘다 — 사용자는
     * 자기 수정이 살아 있다고 믿는다.
     *
     * 라벨은 잃지 않는다. 사람이 무엇을 어떻게 고쳤는지는 review_log 에 append-only 로 남아
     * 있고, 그게 이 수정의 본체다(V5.9). 여기서 지우는 것은 "지금 이 요약이 편집본인가"라는
     * 표시뿐이다.
     *
     * 사람 수정을 재실행이 아예 못 덮게 하는 선택지(항목별 보존)는 버렸다 — 일부는 AI, 일부는
     * 사람인 요약이 만들어져 어느 것이 이번 분석 결과인지 알 수 없게 된다.
     */
    public void overwrite(String overview, String modelName, String promptVersion) {
        this.overview = overview;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.editedByMemberId = null;
        this.editedAt = null;
    }

    /* ANLZ-04 · 사람이 고쳤다는 표시. 누가·언제를 함께 남긴다 — 하나만 남기면 뜻이 반쪽이다. */
    public void markEdited(long editorMemberId, LocalDateTime at) {
        this.editedByMemberId = editorMemberId;
        this.editedAt = at;
    }
}
