package com.module06.backend.capture.domain.model;

/*
 * 정본 스크립트 조회(ANLZ-05)의 페이지 경계다. **직전 페이지의 마지막 발화**를 가리킨다.
 *
 * <h2>왜 두 값인가</h2>
 * 정본의 정렬은 {@code offset_ms 오름차순(NULL 은 맨 뒤) + seq 2차}다
 * (SpringDataTranscriptChunkRepository#ORDER). 커서 페이징은 **커서 키가 정렬 키와 같아야**
 * 성립한다 — 정렬은 오프셋 기준인데 커서를 seq 하나로 두면, 오프셋이 같은 발화가 여럿일 때
 * 어디까지 보냈는지를 커서가 표현하지 못해 그 구간이 통째로 빠지거나 겹쳐 나간다.
 *
 * 명세 예시의 커서(`{"seq":373}`)는 seq 단독이지만 그대로 쓰지 않았다(이슈 #248 ①).
 * 커서는 클라이언트가 해석하지 않는 불투명한 값이라, 안을 바꿔도 계약은 그대로다.
 *
 * <h2>offsetMs 가 null 이면 "꼬리 구간"이라는 뜻이다</h2>
 * 오프셋 없는 발화는 정렬의 맨 뒤에 모인다. 그 구간에 들어선 뒤로는 seq 만으로 자르면 되고,
 * null 은 그 상태를 나타내는 표시다 — "오프셋을 모르는 발화들 중 seq 가 여기까지 나갔다".
 */
public record TranscriptCursor(Integer offsetMs, int seq) {

    /* 오프셋 없는 꼬리 구간에 들어섰는가. 다음 페이지를 어느 구간에서 이어 뜰지 가른다. */
    public boolean isInNullOffsetTail() {
        return offsetMs == null;
    }
}
