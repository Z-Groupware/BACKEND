package com.module06.backend.capture.domain.model;

/*
 * 이 블록이 **어떻게 잘렸는지**다(stt_block.cut_reason · V5.4).
 *
 * 품질 조사에 쓰는 값이지 화면 장식이 아니다. VAD 가 무음을 못 찾아 FALLBACK_OVERLAP 으로
 * 자른 블록은 **경계에서 발화가 잘렸을 수 있다** — 그 구간만 정확도가 낮을 때 이 값이 없으면
 * 프롬프트를 의심하게 되고, 실제 원인(자른 자리)에는 영영 못 닿는다(V5.4 주석).
 */
public enum SttCutReason {

    /* 700ms 이상 무음을 찾아 그 자리에서 잘랐다. 정상 경로다. */
    VAD_SILENCE,

    /* 무음을 못 찾아 겹침으로 잘랐다. **경계 발화가 잘렸을 수 있다.** */
    FALLBACK_OVERLAP,

    /* 회의 끝의 자투리. 10분을 못 채우고 끝난 마지막 블록이다. */
    TAIL,

    /* CAP-10 수동 업로드. 파일 하나가 통째로 블록 하나다 — 자른 적이 없다. */
    WHOLE_FILE
}
