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
 */
public record Utterance(
        Long utteranceId,
        Long speakerMemberId,
        Integer startOffsetMs,
        String text
) {
}
