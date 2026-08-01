package com.module06.backend.reviewloop.judge;

/**
 * 전역 budget 카운터 — PR당 모든 회귀 경로(재수정·재채점 등)의 라운드를 하나로 합산한다. (아티팩트 §01)
 * max(기본 6) 초과 시 어느 경로에서든 종료 → 무한 루프 방지.
 */
public class ReviewBudget {

    public static final int DEFAULT_MAX = 6;

    private final int max;
    private int spent;

    public ReviewBudget() {
        this(DEFAULT_MAX);
    }

    public ReviewBudget(int max) {
        this.max = max;
    }

    public void consume() {
        spent++;
    }

    public int spent() {
        return spent;
    }

    public int remaining() {
        return Math.max(0, max - spent);
    }

    public boolean isExhausted() {
        return spent >= max;
    }
}
