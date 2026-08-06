package com.module06.backend.capture.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.capture.domain.model.SpeakerSource;

/*
 * transcript_chunk 매핑이다.
 *
 * ⚠ **공용 테이블이다.** 다른 도메인도 이 테이블을 쓴다(V5.3 주석). 그래서 필요한 컬럼만
 * 매핑한다 — 전체를 매핑하면 다른 담당자가 컬럼을 바꿀 때 이쪽이 같이 깨진다.
 *
 * ⚠ **쓰기는 화자 두 컬럼에만 한다**({@link #attributeSpeaker}). speaker_member_id ·
 * speaker_source 는 V5.3 이 L1 화자 귀속을 위해 추가한 컬럼이고 그 계층이 A 소유다
 * (명세 「L1 화자 귀속 (rms·명단으로 코드 판정) — Spring」). 나머지 컬럼(content · offset_ms ·
 * seq)은 적재하는 쪽 소유라 세터를 두지 않는다 — 두면 이쪽이 정본을 고칠 수 있게 되고,
 * 그건 다른 도메인이 쓴 값을 조용히 덮는 경로가 된다.
 *
 * offset_ms 가 **시작** 오프셋이다. start_offset_ms 를 새로 만들지 않은 이유는 공용 테이블
 * rename 이 다른 도메인 코드를 깨뜨리기 때문이고, API 표면의 startOffsetMs 매핑은
 * 도메인 모델(Utterance)로 넘어가는 이 자리에서 흡수한다.
 */
@Entity
@Table(name = "transcript_chunk")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranscriptChunkJpaEntity {

    /* V1 baseline 에 AUTO_INCREMENT 가 없다 — 값을 넣는 쪽(STT 적재)이 채운다. 여기선 읽기만 한다. */
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "content", nullable = false)
    private String content;

    /* 발화 시작 오프셋. CAP-01 의 startedAtEpochMs 기준 경과 ms. */
    @Column(name = "offset_ms")
    private Integer offsetMs;

    /* 발화 종료 오프셋. L1 이 자막과 ±1.5초 시간창을 겹칠 때 쓴다. NULL 허용(V5.3). */
    @Column(name = "end_offset_ms")
    private Integer endOffsetMs;

    /*
     * L1 화자 귀속 결과. NULL 은 **판정 포기이고 정상 동작이다**(V5.3 주석).
     * 이 null 을 임의의 값으로 채우면 화자 미정인 1인칭 발화가 엉뚱한 사람의 액션이 된다.
     */
    @Column(name = "speaker_member_id")
    private Long speakerMemberId;

    /* 판정 근거. speaker_member_id 와 항상 함께 채워지거나 함께 NULL 이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "speaker_source")
    private SpeakerSource speakerSource;

    /*
     * L1 판정을 기록한다. 이 두 컬럼이 이 엔티티가 쓰는 전부다.
     *
     * 둘을 **함께** 넣는다. 화자만 채우고 근거를 비워 두면 "판정했는데 근거를 모른다"가 되어,
     * 나중에 오귀속이 발견됐을 때 어느 경로(본인 스트림 / 소거법)를 조일지 알 수 없다.
     *
     * 재판정으로 화자가 바뀌는 것은 정상이다 — 자막이 더 도착한 뒤 다시 돌면 판정이 달라질 수
     * 있고, 그때 이전 값을 유지하면 새 근거를 무시한 채 옛 판정이 남는다.
     */
    public void attributeSpeaker(Long speakerMemberId, SpeakerSource speakerSource) {
        this.speakerMemberId = speakerMemberId;
        this.speakerSource = speakerSource;
    }
}
