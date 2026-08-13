package com.module06.backend.cap.domain.repository;

import java.util.Optional;

/* comment.
    D(회의) 소유 capture_session 테이블 읽기 전용 조회 계약(MeetingReferenceRepository와 동일 패턴).

    status를 String으로 받는다 — D 소유 CaptureSessionStatus(enum)에 cap이 의존하지 않기 위함이다
    (CapSttBlockReferenceEntity가 status를 String으로 읽는 것과 같은 원칙). 값 자체는
    capture_session.status ENUM('ACTIVE','PAUSED','ENDED') 그대로 넘어온다.
*/
public interface CapCaptureSessionReferenceRepository {

    /** 이 회의의 캡처 세션 상태(ACTIVE/PAUSED/ENDED). 세션 행이 없으면 empty. */
    Optional<String> findStatus(Long meetingId);

    /** 이 회의의 캡처 세션 id(capture_session.id). 세션이 없으면 empty. */
    Optional<Long> findSessionId(Long meetingId);
}
