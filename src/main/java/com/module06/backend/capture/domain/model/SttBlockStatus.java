package com.module06.backend.capture.domain.model;

/*
 * STT 블록 하나의 진행 상태다(stt_block.status · V5.4).
 *
 * QUEUED 를 PENDING·RUNNING 과 따로 두는 이유 — 셋 다 "아직 결과가 없다"로 끝나지만 **다음에
 * 할 일이 다르다.** PENDING 은 아직 제출하지 않은 것이라 사람이 재처리를 누를 필요가 없고,
 * QUEUED 는 제출은 됐으니 기다리면 되고, RUNNING 은 제공자가 실제로 돌리는 중이다. 뭉치면
 * 화면이 "처리 중"만 보여주고 사람이 언제 손대야 할지 알 수 없다.
 */
public enum SttBlockStatus {

    /* 블록은 만들어졌지만 아직 제출 전이다. */
    PENDING,

    /* 제공자에 제출했다. 재처리(STT-04)가 성공하면 이 상태로 돌아온다. */
    QUEUED,

    RUNNING,

    DONE,

    /*
     * 실패했다. **재처리(STT-04)의 유일한 대상이다** — 성공했거나 아직 도는 블록을 다시 돌리면
     * 같은 구간에 STT 요금이 두 번 나가고, 이미 들어온 발화 위에 같은 결과가 덮인다.
     */
    FAILED
}
