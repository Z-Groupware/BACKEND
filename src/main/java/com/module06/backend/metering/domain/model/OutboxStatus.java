package com.module06.backend.metering.domain.model;

/**
 * 토큰 사용량 outbox 항목의 전달 상태.
 *
 * <ul>
 *   <li>{@code PENDING} — 원장에 아직 반영되지 않음. 릴레이가 재시도 대상으로 훑는다.</li>
 *   <li>{@code DONE} — 원장 반영 완료. 더 이상 처리하지 않는다.</li>
 *   <li>{@code DEAD} — 한계 시도 초과. 자동 복구 포기, 관측·수동 개입 대상(dead-letter).</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    DONE,
    DEAD
}
