package com.module06.backend.reviewloop.judge;

/**
 * 사람 검토에서 나온 판정의 종류.
 * FALSE_POSITIVE : Judge가 위반이라 했지만 실제론 아니었음(오판) — 예: 투영은 예외인데 flag함
 * CONFIRMED      : Judge가 옳았음 — 사람이 지적을 받아들여 수정함. 오탐률의 분모(판정 횟수)를 채운다.
 * MISSED         : Judge가 놓친 진짜 위반 — 사람이 뒤늦게 발견
 *
 * 프롬프트에는 FALSE_POSITIVE·MISSED(=Judge의 실수)만 주입한다. CONFIRMED는 정확도 집계용이라 제외.
 */
public enum LessonKind {
    FALSE_POSITIVE,
    CONFIRMED,
    MISSED
}
