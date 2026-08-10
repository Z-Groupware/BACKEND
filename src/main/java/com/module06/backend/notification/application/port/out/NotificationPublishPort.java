package com.module06.backend.notification.application.port.out;

/*
 * 다른 도메인이 특정 회원에게 실시간 알림을 보내고 싶을 때 부르는 경계. 이 포트를 부르는 시점에 그
 * 회원이 연결돼 있는지, 몇 대의 인스턴스에 나뉘어 붙어 있는지는 호출자가 신경 쓸 필요 없다 — 발행은
 * best-effort이며(Redis 장애가 호출자의 원래 작업을 실패시키면 안 된다), 연결 안 돼 있으면 조용히 버려진다.
 */
public interface NotificationPublishPort {

    /** 이 회원에게 이 이벤트를 실시간으로 보낸다(연결 안 돼 있으면 그냥 버려짐). */
    void publish(Long memberId, NotificationEvent event);
}
