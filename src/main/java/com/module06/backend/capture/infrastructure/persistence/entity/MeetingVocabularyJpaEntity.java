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

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "built_at")
    private LocalDateTime builtAt;

    /* 제공자 리소스를 정리한 시각. NULL 이면 아직 계정 상한을 쓰고 있다. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static MeetingVocabularyJpaEntity pending(long meetingId) {
        MeetingVocabularyJpaEntity entity = new MeetingVocabularyJpaEntity();
        entity.meetingId = meetingId;
        entity.status = VocabularyStatus.PENDING;
        entity.phraseCount = 0;
        return entity;
    }

    /*
     * 재생성을 접수한다.
     *
     * 이전 실패 흔적(errorCode)은 지운다 — 남겨 두면 지금 만드는 중인데도 화면이 지난 실패를
     * 이번 것으로 보여준다. 반대로 phraseCount·builtAt 은 **그대로 둔다**(클래스 주석).
     */
    public void markRebuilding() {
        this.status = VocabularyStatus.PENDING;
        this.errorCode = null;
        // 새 리소스를 만들기 시작했으므로 이전 것을 지운 적은 없다.
        this.deletedAt = null;
    }

    /* 제공자에 제출한 리소스 이름을 적어 둔다. 이 값이 없으면 나중에 지울 수 없다. */
    public void assignProviderName(String providerVocabularyName) {
        this.providerVocabularyName = providerVocabularyName;
    }
}
