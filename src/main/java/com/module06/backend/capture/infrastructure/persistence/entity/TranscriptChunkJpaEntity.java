package com.module06.backend.capture.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * transcript_chunk 읽기 전용 매핑이다.
 *
 * ⚠ **공용 테이블이다.** 다른 도메인도 이 테이블을 쓴다(V5.3 주석). 그래서 여기서는
 * 쓰기를 하지 않고, 필요한 컬럼만 매핑한다 — 전체를 매핑하면 다른 담당자가 컬럼을 바꿀 때
 * 이쪽이 같이 깨진다.
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

    /*
     * L1 화자 귀속 결과. NULL 은 **판정 포기이고 정상 동작이다**(V5.3 주석).
     * 이 null 을 임의의 값으로 채우면 화자 미정인 1인칭 발화가 엉뚱한 사람의 액션이 된다.
     */
    @Column(name = "speaker_member_id")
    private Long speakerMemberId;
}
