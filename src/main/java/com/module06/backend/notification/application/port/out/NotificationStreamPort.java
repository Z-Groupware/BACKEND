package com.module06.backend.notification.application.port.out;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/*
 * 회원 개인 알림 SSE 연결 등록을 담당하는 인프라 경계. cap의 CaptionStreamPort와 동일 패턴이지만
 * 회의가 아니라 회원 단위로 구독한다 — 로그인한 사람 한 명이 브라우저 하나를 열어두면, 어느 도메인이
 * 보낸 알림이든(액션 배정, 인수인계 승인 등) 전부 이 하나의 연결로 들어온다.
 */
public interface NotificationStreamPort {

    /** 이 회원의 SSE 연결을 새로 만들어 등록하고 emitter를 반환한다. */
    SseEmitter subscribe(Long memberId);
}
