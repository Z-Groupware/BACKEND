package com.module06.backend.reviewloop.judge;

/**
 * 판단 안건이 선택됐을 때 실행할 액션.
 * FALSE_POSITIVE : 오탐 → 교훈(lesson) 기록 후 통과
 * AUTO_FIX       : 진짜 위반 → 자동수정 루프
 * POLICY_REVIEW  : 정책 불명확 → 카탈로그(rules.yaml) 수정 제안
 */
public enum PanelAction {
    FALSE_POSITIVE,
    AUTO_FIX,
    POLICY_REVIEW
}
