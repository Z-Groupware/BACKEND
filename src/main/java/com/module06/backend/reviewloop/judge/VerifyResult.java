package com.module06.backend.reviewloop.judge;

/** P1 검증 결과 — 통과 여부 + 진단 로그(실패 시 컴파일 에러 메시지). */
public record VerifyResult(boolean ok, String log) {}
