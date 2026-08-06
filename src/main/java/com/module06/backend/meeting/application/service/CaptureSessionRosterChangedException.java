package com.module06.backend.meeting.application.service;

/*
 * roster 이름 조회와 세션 저장 사이에 참석자 명단이 바뀐 내부 경합을 재시도하기 위한 예외다.
 *
 * 외부 오류 계약으로 노출하지 않고 잠금 트랜잭션을 롤백한 뒤 최신 명단으로 다시 조회한다.
 */
final class CaptureSessionRosterChangedException extends RuntimeException {

    /* 명단 변경 경합임을 로그와 디버깅에서 식별할 수 있는 고정 메시지를 사용한다. */
    CaptureSessionRosterChangedException() {
        /* 공개 ErrorCode가 아닌 애플리케이션 내부 재시도 신호로만 사용한다. */
        super("캡처 세션 시작 중 참석자 명단이 변경되었습니다.");
    }
}
