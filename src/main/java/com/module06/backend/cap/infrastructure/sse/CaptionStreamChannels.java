package com.module06.backend.cap.infrastructure.sse;

// CAP-13 SSE 브로드캐스트용 Redis pub/sub 채널 이름. 회의별 채널을 동적으로 구독/해지하는 대신
// 고정 채널 두 개만 쓰고 페이로드에 meetingId를 실어, 인스턴스마다 항상 이 두 채널만 구독하면 되게 한다.
public final class CaptionStreamChannels {

    public static final String CAPTION = "cap:captions:new";
    public static final String PARTICIPANT = "cap:captions:participant";

    private CaptionStreamChannels() {
    }
}
