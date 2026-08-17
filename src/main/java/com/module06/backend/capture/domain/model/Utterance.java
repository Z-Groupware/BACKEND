package com.module06.backend.capture.domain.model;

/*
 * 계층에 넘기는 발화 하나다. transcript_chunk 한 행에서 온다.
 *
 * 이름을 startOffsetMs 로 두는 이유 — 테이블 컬럼은 offset_ms 이지만 그것이 시작 오프셋이고
 * (V5.3 주석), API 표면과 Python 쪽 계약은 startOffsetMs 다. 공용 테이블인 transcript_chunk 를
 * rename 하면 다른 도메인 코드가 깨지므로 매핑을 여기서 흡수한다. 이 클래스가 그 경계다.
 *
 * speakerMemberId 는 null 일 수 있다. L1 화자 귀속의 판정 포기는 오류가 아니라 정상 동작이고
 * (V5.3 주석), 그 null 을 숨기면 안 된다 — 화자 미정인 1인칭 발화("제가 할게요")의 담당자는
 * 미정이어야 하는데, 임의로 채우면 엉뚱한 사람에게 일이 배정된다.
 *
 * endOffsetMs 도 null 일 수 있다(V5.3 에서 NULL 허용으로 추가됐다). L1 이 자막과 ±1.5초
 * 시간창을 겹칠 때 쓰는 값이고, 없으면 발화를 길이 0 으로 보고 시작 지점만 쓴다.
 *
 * speakerLabel 은 STT 화자 분리 라벨이다(V5.23). **speakerMemberId 와 혼동하면 안 된다** —
 * 이건 사람이 아니라 {@code 3:spk_0} 같은 군집 번호이고, "이 구간과 저 구간이 같은 목소리다"
 * 까지만 말한다. 누구인지는 SpeakerLabelAnchorResolver 가 별도로 정하고, 그 결과가
 * speakerMemberId 에 들어온다. 라벨이 있는데 화자가 null 인 것은 **정상이다** — 구분은 됐지만
 * 아직 그 라벨에 닻을 못 내린 상태다.
 *
 * ⚠ endOffsetMs · speakerLabel 은 **Python 계층에 보내지 않는다.** 계층 쪽 Utterance 스키마가
 * extra="forbid" 라 모르는 필드를 422 로 거절한다. 둘 다 L1(코드 계층) 전용이고, 어댑터가
 * UtteranceDto 로 옮겨 담을 때 빠진다(AiLayerHttpAdapter).
 */
public record Utterance(
        Long utteranceId,
        Long speakerMemberId,
        Integer startOffsetMs,
        Integer endOffsetMs,
        String text,
        String speakerLabel
) {
}
