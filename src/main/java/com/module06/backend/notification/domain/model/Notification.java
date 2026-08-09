package com.module06.backend.notification.domain.model;

import java.time.LocalDateTime;

/*
 * 저장되는 알림 한 건. companyId/memberId는 필수다 — 이 값이 없으면 어느 테넌트·누구의 알림인지
 * 특정할 수 없어 테넌트 격리 자체가 성립하지 않는다(모성진 요청 — "저장·조회 시 companyId를
 * 실제 조건으로 적용"). 이 값들은 발행 도메인(예: meeting)이 이미 검증한 이벤트에서 그대로
 * 온 것을 신뢰한다 — 이 도메인이 다시 조회해서 검증하지 않는다.
 *
 * meetingId는 지금 존재하는 알림 종류(MEETING_CREATED/MEETING_REMINDER/MEETING_CANCELED)가
 * 전부 회의 알림이라 그 뜻 그대로 쓴다 — 범용 참조로 억지 확장하지 않는다. 나중에 회의 아닌
 * 알림 종류가 생기면 그 담당자가 별도 컬럼(예: actionId)과 도메인 필드를 추가한다.
 */
public class Notification {

    private final Long id;
    private final Long companyId;
    private final Long memberId;
    private final String type;
    private final Long meetingId;
    private final String message;
    private final LocalDateTime readAt;
    private final LocalDateTime createdAt;

    private Notification(Long id, Long companyId, Long memberId, String type, Long meetingId, String message,
                         LocalDateTime readAt, LocalDateTime createdAt) {
        requireId(companyId, "companyId");
        requireId(memberId, "memberId");
        requireText(type, "type");
        requireText(message, "message");
        this.id = id;
        this.companyId = companyId;
        this.memberId = memberId;
        this.type = type;
        this.meetingId = meetingId;
        this.message = message;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    /** 신규 알림 — 저장 시도 전 도메인 객체로 만들 때 사용. 항상 미확인(readAt=null) 상태로 시작한다. */
    public static Notification create(Long companyId, Long memberId, String type, Long meetingId, String message) {
        return new Notification(null, companyId, memberId, type, meetingId, message, null, null);
    }

    /** DB에서 읽어온 값으로 복원. */
    public static Notification restore(Long id, Long companyId, Long memberId, String type, Long meetingId,
                                       String message, LocalDateTime readAt, LocalDateTime createdAt) {
        return new Notification(id, companyId, memberId, type, meetingId, message, readAt, createdAt);
    }

    private static void requireId(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 는 필수입니다.");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 는 필수입니다.");
        }
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getMemberId() { return memberId; }
    public String getType() { return type; }
    public Long getMeetingId() { return meetingId; }
    public String getMessage() { return message; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
