package com.module06.backend.notification.infrastructure.sse;

// 알림 브로드캐스트용 Redis pub/sub 채널 이름. 회원별 채널을 동적으로 구독/해지하는 대신 고정 채널
// 하나만 쓰고 페이로드에 수신자 memberId를 실어, 인스턴스마다 항상 이 채널만 구독하면 되게 한다
// (cap의 CaptionStreamChannels와 동일 패턴).
public final class NotificationChannels {

    public static final String NOTIFICATION = "notification:new";

    private NotificationChannels() {
    }
}
