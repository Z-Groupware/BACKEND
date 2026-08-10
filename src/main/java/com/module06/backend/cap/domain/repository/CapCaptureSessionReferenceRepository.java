package com.module06.backend.cap.domain.repository;

/* comment.
    D(회의) 소유 capture_session 테이블 읽기 전용 조회 계약(MeetingReferenceRepository와 동일 패턴).

    이 세션 행 자체가 없을 수 있다 — capture_session은 CAP-01(회의 입장) 시점에 D가 만드는데,
    아직 그 경로를 안 탄 회의이거나 D 쪽 배선이 늦으면 없을 수 있다. 없으면 "일시정지 아님"으로
    본다(isPaused=false) — 세션 존재를 이 관문의 필수 전제로 두면, D 쪽 사정으로 행이 없을 때
    정상적인 녹음까지 막히게 된다.
*/
public interface CapCaptureSessionReferenceRepository {

    /** 이 회의의 캡처 세션이 지금 일시정지(PAUSED) 상태인지. 세션이 없으면 false. */
    boolean isPaused(Long meetingId);
}
