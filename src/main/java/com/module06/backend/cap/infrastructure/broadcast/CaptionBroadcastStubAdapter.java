package com.module06.backend.cap.infrastructure.broadcast;

import com.module06.backend.cap.application.port.out.CaptionBroadcastPort;
import com.module06.backend.cap.domain.model.CaptionChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/* comment.
    CaptionBroadcastPort의 스텁 구현 — 실제 SSE emitter 레지스트리(CAP-13)가 배선되기 전까지
    브로드캐스트를 로그로만 남긴다. RecordingAssemblyStubAdapter와 동일한 "실 어댑터 전 스텁" 패턴.
*/
@Component
public class CaptionBroadcastStubAdapter implements CaptionBroadcastPort {

    private static final Logger log = LoggerFactory.getLogger(CaptionBroadcastStubAdapter.class);

    @Override
    public void broadcast(Long meetingId, List<CaptionChunk> newChunks) {
        log.info("자막 브로드캐스트(stub) — meetingId={}, count={}. 실제 SSE push는 CAP-13에서 수행.",
                meetingId, newChunks.size());
    }
}
