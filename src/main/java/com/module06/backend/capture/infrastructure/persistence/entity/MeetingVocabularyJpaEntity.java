package com.module06.backend.capture.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.capture.domain.model.VocabularyStatus;

/*
 * meeting_vocabulary(V5.19) 매핑이다. 회의당 하나다(UNIQUE).
 *
 * <h2>재생성이 이전 결과를 지우지 않는다</h2>
 * 재생성이 도는 동안에도 **제공자에는 이전 어휘가 그대로 살아 있다.** phraseCount·builtAt 을
 * 0·null 로 비우면 화면이 "어휘 없음"으로 보여주는데 실제로는 지난 어휘가 쓰이고 있고, 사람이
 * 인식률 문제를 어휘 탓으로 잘못 짚게 된다. 그래서 status 만 PENDING 으로 바꾼다.
 */
@Entity
@Table(name = "meeting_vocabulary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingVocabularyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VocabularyStatus status;

    /* 마지막으로 **성공한** 생성의 단어 수. 재생성 중에도 남는다. */
    @Column(name = "phrase_count", nullable = false)
    private int phraseCount;

    /*
     * 제공자 리소스 이름. **계정당 어휘 개수 상한이 있어 정리가 필수**인데, 지우려면 이름을
     * 알아야 한다. STT 제출 때 참조하는 값이기도 하다 — 규칙으로 매번 다시 만들면 규칙이
     * 바뀌는 순간 예전 회의의 어휘를 못 찾는다.
     */
    @Column(name = "provider_vocabulary_name", length = 200)
    private String providerVocabularyName;

    /*
     * 재생성 중인 리소스 이름.
     *
     * **활성 이름을 덮지 않는 이유** — 재생성이 도는 동안 제공자에는 이전 어휘가 살아 있고
     * 실제로 그게 쓰인다. 새 이름으로 덮으면 이전 리소스 이름이 사라져 그것만 영영 못 지우고,
     * 재생성을 반복할수록 지울 수 없는 리소스가 쌓여 **계정 상한이 누수된다**(CodeRabbit PR #241).
     *
     * 승격(READY 확인 후 활성으로 옮기고 이전 것을 삭제)은 후속이다 — 완료 확인 경로가 아직 없다.
     * 그때까지도 두 이름이 다 남아 있어 **정리 작업이 둘 다 지울 수 있다.**
     */
    @Column(name = "pending_vocabulary_name", length = 200)
    private String pendingVocabularyName;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    /* 제공자 리소스를 정리한 시각. NULL 이면 아직 계정 상한을 쓰고 있다. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /*
     * 승격으로 밀려난 이전 활성 리소스(V5.21). 아무도 참조하지 않지만 계정 상한은 계속 쓴다 —
     * 정리 워커가 받아쓰기 종료를 확인한 뒤 지운다.
     */
    @Column(name = "stale_vocabulary_name", length = 200)
    private String staleVocabularyName;

    /* 대기 빌드의 단어 수. 제공자가 개수를 안 주므로 제출 시점에 적어 두고 승격이 옮긴다. */
    @Column(name = "pending_phrase_count")
    private Integer pendingPhraseCount;

    /* 제공자에 접수한 시각. 응답 없이 오래 걸린 빌드를 포기하는 기준이다. */
    @Column(name = "build_started_at")
    private LocalDateTime buildStartedAt;

    public static MeetingVocabularyJpaEntity pending(long meetingId) {
        MeetingVocabularyJpaEntity entity = new MeetingVocabularyJpaEntity();
        entity.meetingId = meetingId;
        entity.status = VocabularyStatus.PENDING;
        entity.phraseCount = 0;
        return entity;
    }

    /*
     * 재생성을 **선점한다.** 이미 PENDING 이면 false 다.
     *
     * 선점이 필요한 이유 — 같은 회의에 재생성 요청이 겹치면 둘 다 제공자를 불러 **어휘 리소스가
     * 중복 생성되고 계정 상한을 그만큼 갉아먹는다**(CodeRabbit PR #241). 이긴 요청만 제출하고
     * 진 요청은 진행 중인 작업을 그대로 돌려준다.
     *
     * 이전 실패 흔적(errorCode)은 지운다 — 남겨 두면 지금 만드는 중인데도 화면이 지난 실패를
     * 이번 것으로 보여준다. 반대로 phraseCount·builtAt 은 **그대로 둔다**(클래스 주석).
     */
    public boolean claimRebuild() {
        if (this.status == VocabularyStatus.PENDING && this.id != null) {
            return false;
        }
        this.status = VocabularyStatus.PENDING;
        this.errorCode = null;
        this.pendingVocabularyName = null;
        // 새 리소스를 만들기 시작했으므로 이전 것을 지운 적은 없다.
        this.deletedAt = null;
        return true;
    }

    /*
     * 제출한 리소스 이름을 **대기 칸에** 적는다. 활성 이름은 건드리지 않는다 — 그 리소스가
     * 아직 쓰이고 있고, 덮으면 지울 방법이 사라진다.
     */
    public void assignPendingName(String pendingVocabularyName, int pendingPhraseCount,
                                  LocalDateTime now) {
        this.pendingVocabularyName = pendingVocabularyName;
        /*
         * 단어 수를 **여기서** 적는다. 제공자는 개수를 돌려주지 않으므로 그 값을 아는 유일한
         * 시점이 제출이다(V5.21). phrase_count 에 바로 쓰지 않는 이유 — 그건 "마지막으로
         * 성공한" 값이고 재생성이 도는 동안에도 화면이 그 값을 보여줘야 한다.
         */
        this.pendingPhraseCount = pendingPhraseCount;
        // 응답 없이 오래 걸린 빌드를 포기하는 기준이 된다.
        this.buildStartedAt = now;
    }

    /* 제출한 지 얼마나 됐나(포기 판정). 접수 시각이 없으면 판단할 수 없다. */
    public boolean buildStartedBefore(LocalDateTime threshold) {
        return this.buildStartedAt != null && this.buildStartedAt.isBefore(threshold);
    }

    /*
     * 제공자가 어휘를 다 만들었다 — **대기 이름을 활성으로 승격한다.**
     *
     * <h2>이전 활성 이름을 돌려준다</h2>
     * 승격되면 이전 리소스는 아무도 참조하지 않는데 제공자 계정에는 남아 상한을 갉아먹는다.
     * 지우는 것은 제공자 호출이라 엔티티가 할 수 없으므로, **지울 이름을 호출자에게 넘긴다.**
     * 여기서 그냥 버리면 그 리소스는 이름이 사라져 영영 못 지운다(V5.19 주석이 경고한 누수다).
     *
     * <h2>phraseCount 를 여기서 채운다</h2>
     * 이 컬럼은 지금까지 아무도 채우지 않아 늘 0 이었다 — 채우는 자리가 승격이었기 때문이다.
     * 다만 **제출한 단어 수는 제출한 쪽만 안다**(제공자는 개수를 돌려주지 않는다). 그래서
     * 제출 시점에 받아 둔 값을 그대로 쓴다.
     *
     * @param builtPhraseCount 이번에 만든 어휘의 단어 수
     * @return 지워야 할 이전 활성 리소스 이름. 첫 생성이면 null
     */
    public boolean promoteToReady(String expectedPendingName, LocalDateTime now) {
        /*
         * 폴링한 이름과 지금 대기 이름이 같을 때만 승격한다.
         *
         * 다르면 **그 사이에 재생성이 새 빌드를 접수한 것**이다. 옛 폴링 결과로 승격하면
         * 만들어지지도 않은 리소스가 활성이 되고, 그 이름이 STT 제출에 실려 나가 제공자가
         * 거절한다 — 받아쓰기 전체가 실패한다(CodeRabbit PR #353 지적).
         *
         * 이 저장소의 다른 전이가 쓰는 방식과 같다(markQueuedForRetry 의 compare-and-set).
         */
        if (this.pendingVocabularyName == null
                || !this.pendingVocabularyName.equals(expectedPendingName)) {
            return false;
        }

        /*
         * 밀려난 이전 활성 리소스를 **적어 둔다.** 여기서 버리고 호출자가 곧바로 지우면,
         * 삭제가 실패했을 때 다시 시도할 이름이 없고(V5.19 가 경고한 누수) 아직 도는 STT 잡이
         * 그 이름을 참조할 수 있다 — 정리 경로만 "받아쓰기가 끝났나"를 보므로 그 검사를
         * 건너뛰게 된다.
         *
         * 승격된 이름과 같으면 밀려난 것이 없다(같은 이름으로 다시 만든 경우).
         */
        if (this.providerVocabularyName != null
                && !this.providerVocabularyName.equals(this.pendingVocabularyName)) {
            this.staleVocabularyName = this.providerVocabularyName;
        }

        this.providerVocabularyName = this.pendingVocabularyName;
        this.pendingVocabularyName = null;
        this.status = VocabularyStatus.READY;
        this.errorCode = null;
        // 제출 시점에 적어 둔 값을 옮긴다. 제공자는 개수를 돌려주지 않는다(V5.21).
        if (this.pendingPhraseCount != null) {
            this.phraseCount = this.pendingPhraseCount;
        }
        this.pendingPhraseCount = null;
        this.buildStartedAt = null;
        this.builtAt = now;
        /*
         * 새 리소스가 활성이 됐다. 아직 아무것도 지우지 않았으므로 정리 표시를 비운다 —
         * 남아 있으면 정리 조회가 이 회의를 "이미 지웠다"로 보고 건너뛰어 상한이 누수된다.
         */
        this.deletedAt = null;
        return true;
    }

    /* 밀려난 리소스를 지웠다. 활성 정리와 달리 이름 자체를 비운다 — 참조할 곳이 없는 값이다. */
    public void clearStaleName() {
        this.staleVocabularyName = null;
    }

    /*
     * 제공자 리소스를 정리했다.
     *
     * 활성 이름은 **지우지 않는다.** 지운 뒤에도 "무엇을 지웠는지"가 남아야 하고, 그 이름이
     * 사라지면 같은 회의를 재생성할 때 이전 리소스가 이미 정리됐는지 알 수 없다.
     * 상한을 쓰고 있는지는 deleted_at 이 답한다(V5.19 주석).
     */
    public void markCleaned(LocalDateTime now) {
        this.deletedAt = now;
    }

    /*
     * 제출이 실패했다. **PENDING 으로 두지 않는다** — 그러면 화면이 영원히 "만드는 중"으로
     * 보여주고 사람이 다시 누를 수도 없다(선점이 PENDING 을 막는다).
     */
    public void markBuildFailed(String errorCode) {
        this.status = VocabularyStatus.FAILED;
        this.errorCode = errorCode;
        this.pendingVocabularyName = null;
        this.pendingPhraseCount = null;
        this.buildStartedAt = null;
    }

    /*
     * 폴링이 확인한 실패다 — **그 빌드가 아직 대기 중일 때만** 닫는다.
     *
     * 승격과 같은 이유다(promoteToReady 주석). 옛 폴링 결과로 닫으면 방금 접수된 새 빌드가
     * FAILED 가 되고, 선점이 PENDING 을 막던 것과 반대로 이번엔 **아직 만들어지는 중인
     * 리소스가 버려진다** — 그 리소스는 계정 상한을 쓰면서 아무도 참조하지 않는다.
     *
     * @return 내가 닫았으면 true
     */
    public boolean markBuildFailed(String expectedPendingName, String errorCode) {
        if (this.pendingVocabularyName == null
                || !this.pendingVocabularyName.equals(expectedPendingName)) {
            return false;
        }
        /*
         * 실패한 대기 리소스도 **밀려난 것으로 적어 둔다.** 제공자가 FAILED 로 닫은 어휘도
         * 계정 상한을 차지한다 — 지우지 않으면 실패할수록 상한이 줄어든다.
         */
        this.staleVocabularyName = this.pendingVocabularyName;
        markBuildFailed(errorCode);
        return true;
    }
}
