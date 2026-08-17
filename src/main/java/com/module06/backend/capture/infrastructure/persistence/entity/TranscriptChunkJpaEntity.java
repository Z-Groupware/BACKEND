package com.module06.backend.capture.infrastructure.persistence.entity;

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

    /*
     * ⚠ 이전 주석이 "V1 baseline 에 AUTO_INCREMENT 가 없다"고 적혀 있었는데 **사실이 아니다** —
     * V1__init_schema.sql 이 PK 를 건 뒤 317 행에서 MODIFY 로 AUTO_INCREMENT 를 붙인다.
     * 적재 경로(STT 결과 → transcript_chunk)를 만들면서 확인했고, IDENTITY 로 맞춘다.
     * 그대로 두면 삽입 시 Hibernate 가 id 를 요구한다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
     * STT 화자 분리 라벨(V5.23). **사람이 아니다** — {@code 3:spk_0} 같은 군집 번호다.
     *
     * 위의 두 컬럼과 성격이 다르다. 저 둘은 L1 의 **판정 결과**라 재판정 때마다 지워지고
     * 다시 쓰이지만, 이건 적재가 넣는 **입력**이라 L1 이 건드리지 않는다 — 그래서
     * {@link #clearSpeaker} 가 이 값은 비우지 않는다. 비우면 재판정이 자기 근거를 지우는
     * 셈이고, 다음 실행은 라벨이 없어 영영 판정할 수 없게 된다.
     */
    @Column(name = "speaker_label")
    private String speakerLabel;

    /*
     * 이 발화를 만든 stt_block.block_seq(V5.3). **블록 재처리의 교체 범위**다 —
     * 블록 하나를 다시 돌리면 그 블록이 만든 행만 지우고 새로 넣는다.
     *
     * NULL 인 행은 이 경로가 붙기 전에 들어온 발화다(자막 기반 임시 적재 등). 지우지 않는다 —
     * 어느 블록이 만든 것인지 모르는 행을 블록 교체가 건드리면 남의 발화가 사라진다.
     */
    @Column(name = "stt_block_seq")
    private Integer sttBlockSeq;

    /*
     * STT 결과 한 발화를 만든다.
     *
     * <h2>seq 는 적재 순서일 뿐이다</h2>
     * 정렬은 offset_ms 가 한다(ORDER = offsetMs NULLS LAST, seq). seq 는 **오프셋이 같을 때의
     * tie-breaker** 이고 커서 페이징이 그 자리에서만 비교한다(ANLZ-05). 그래서 회의 안에서
     * 유일하면 되고, 블록을 재처리해 뒤늦게 큰 번호가 붙어도 페이징은 깨지지 않는다 — 다른
     * 오프셋끼리는 seq 를 비교하지 않는다.
     *
     * <h2>화자는 여기서 채우지 않는다 — 라벨은 채운다</h2>
     * 화자(speaker_member_id)는 적재 시점에 아무도 모른다. L1(SpeakerAttributionResolver)이
     * 나중에 attributeSpeaker 로 넣는다 — NULL 이 "아직 판정 안 함"이고 정상이다.
     *
     * 라벨(speaker_label)은 반대다. **제공자가 준 값이라 적재만이 안다** — L1 이 도는 시점에는
     * STT 응답이 이미 없다. 그래서 여기서 넣고, 그 뒤로는 아무도 고치지 않는다.
     */
    public static TranscriptChunkJpaEntity fromStt(long meetingId, int seq, String content,
                                                   int offsetMs, int endOffsetMs, int sttBlockSeq,
                                                   String speakerLabel) {
        TranscriptChunkJpaEntity entity = new TranscriptChunkJpaEntity();
        entity.meetingId = meetingId;
        entity.seq = seq;
        entity.content = content;
        entity.offsetMs = offsetMs;
        entity.endOffsetMs = endOffsetMs;
        entity.sttBlockSeq = sttBlockSeq;
        entity.speakerLabel = speakerLabel;
        return entity;
    }

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

    /*
     * 화자를 미정으로 되돌린다. **재판정이 기권한 발화를 위한 자리다.**
     *
     * 자막이 더 도착해 1·2등 차이가 3dB 아래로 좁아지면 resolver 는 기권하는데, 그때 예전
     * 판정을 남겨 두면 근거가 약해진 화자가 확정으로 굳는다. 기권은 컬럼에도 기권으로 남아야
     * 한다 — NULL 이 판정 포기이고 정상 동작이다(V5.3 주석).
     *
     * 둘을 함께 비운다. 근거만 남기면 "화자는 모르는데 판정 경로는 안다"가 되어 뜻이 없다.
     *
     * ⚠ speaker_label 은 비우지 않는다. 그건 판정이 아니라 **판정의 입력**이고, 여기서 지우면
     * 재판정이 자기 근거를 지우는 셈이 된다 — 그 발화는 다음 실행에서도 영영 판정될 수 없다.
     */
    public void clearSpeaker() {
        this.speakerMemberId = null;
        this.speakerSource = null;
    }
}
