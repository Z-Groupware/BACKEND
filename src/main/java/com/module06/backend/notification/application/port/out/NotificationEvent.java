package com.module06.backend.notification.application.port.out;

/*
 * 알림 채널을 타는 이벤트의 최소 형태 — type(예: "ACTION_ASSIGNED", "HANDOVER_APPROVED")은 클라이언트가
 * 분기하는 식별자, payload는 그 종류에 맞는 임의의 JSON 직렬화 가능 객체다. 이 인프라는 type/payload의
 * 의미를 모른다 — 실제 발행은 각 도메인(action, handover 등)이 이 포트를 호출해서 한다.
 */
public record NotificationEvent(String type, Object payload) {
}
