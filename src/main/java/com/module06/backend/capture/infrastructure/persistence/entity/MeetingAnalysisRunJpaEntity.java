package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

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
 *
 * <h2>Persistable 을 구현하는 이유 — 안 하면 첫 행 경합이 조용히 통과한다</h2>
 * 식별자를 직접 정하는 엔티티는 {@code SimpleJpaRepository.save()} 에서 **언제나 merge 로
 * 간다.** {@code isNew()} 의 기본 판정이 "id != null" 인데 {@link #initial} 이 이미 id 를
 * 채워 두기 때문이다. 그리고 merge 는 그 순간 다시 SELECT 를 해서, **먼저 커밋된 행이 있으면
 * INSERT 가 아니라 UPDATE 로 나간다.**
 *
 * 그러면 실행 번호 발급의 경합이 이렇게 끝난다 — 두 실행이 "행 없음"을 보고 둘 다 1 을
 * 만들고, 진 쪽의 save 가 UPDATE 로 바뀌어 PK 충돌 없이 성공한다. **둘 다 1 번을 들고 가고,
 * 서로 자기가 최신이라고 판정한다.** #134 가 막으려던 상태가 그대로 재현된다
 * (AnalysisRunOrderingPersistenceTest 가 CI 에서 간헐적으로 잡아내던 실패가 이것이다).
 *
 * 그래서 새 인스턴스는 persist 로 가도록 못박는다. 그러면 경합에서 진 쪽이 PK 충돌로 떨어지고,
 * {@link com.module06.backend.capture.infrastructure.persistence.adapter.AnalysisRunPersistenceAdapter}
 * 가 의도대로 물러난다.
 */
@Entity
@Table(name = "meeting_analysis_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingAnalysisRunJpaEntity implements Persistable<Long> {

    @Id
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "run_seq", nullable = false)
    private long runSeq;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * 아직 저장된 적이 없는 인스턴스인가. 컬럼이 아니라 **이 객체의 상태**다.
     *
     * DB 에서 읽어 온 것과 우리가 방금 만든 것을 구분할 방법이 이것뿐이다 — id 는 양쪽 다
     * 채워져 있어서 기본 판정("id != null")이 둘을 구분하지 못한다.
     */
    @Transient
    private boolean isNew = true;

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

    @Override
    public Long getId() {
        return meetingId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /*
     * 읽어 왔거나(PostLoad) 방금 넣었으면(PostPersist) 더 이상 새 것이 아니다.
     *
     * PostLoad 가 없으면 기존 행을 읽어 번호를 올린 뒤 save 할 때 persist 로 가서 PK 충돌이
     * 난다 — 두 번째 실행부터 발급이 통째로 실패한다.
     */
    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
