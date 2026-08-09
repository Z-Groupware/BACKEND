package com.module06.backend.cap.application.port.out;

import com.module06.backend.cap.domain.model.CaptionChunk;

import java.util.List;

/* comment.
    CAP-11(자막 저장)과 CAP-13(자막 실시간 구독 SSE) 사이의 경계. 저장 직후 새로 저장된 조각만 참석자
    전원에게 실시간으로 밀어준다. 실제 SSE emitter 레지스트리는 CAP-13 작업에서 만들어지므로, 그 전까지는
    스텁(로그만)으로 개발한다(MeetingRecordingSttPort·RecordingAssemblyPort와 동일 패턴).
    재전송으로 건너뛴 중복 조각은 넘기지 않는다 — 이미 화면에 떠 있는 자막을 다시 브로드캐스트할 필요는 없다.
*/
public interface CaptionBroadcastPort {

    /** 새로 저장된 자막 조각을 이 회의의 구독자 전원에게 브로드캐스트한다(best-effort). */
    void broadcast(Long meetingId, List<CaptionChunk> newChunks);
}
