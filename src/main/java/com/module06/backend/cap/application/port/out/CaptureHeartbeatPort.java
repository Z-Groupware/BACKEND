package com.module06.backend.cap.application.port.out;

/* comment.
    "지금 녹음자가 살아있는가"만 판정하는 포트 — 녹음자의 신원(personId)은 여기 담지 않는다
    (capture_upload_state.recorder_person_id가 durable한 진실이고, 이건 TTL 만료로 같이
    사라지면 안 되기 때문). complete() 호출마다 refresh, presign()에서 이어받기(canTakeover)
    판정에 isAlive를 쓴다.
*/
public interface CaptureHeartbeatPort {

    /** 하트비트 갱신 (complete() 호출마다) */
    void refresh(Long meetingId);

    /** 지금 녹음자가 살아있는지 (30초 이내 refresh가 있었는지) */
    boolean isAlive(Long meetingId);
}
