package com.module06.backend.cap.infrastructure.assembly;

import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/* comment.
    RecordingAssemblyPort의 스텁 구현 — 로컬/테스트 전용(@Profile("!prod")). 운영은
    RecordingAssemblyS3FfmpegAdapter(ffmpeg·S3 다운로드·duration 복구·parts 삭제 실 구현)가
    대체한다. CapObjectStorageStubAdapter/CapS3ObjectStorageAdapter와 동일한 stub↔실 어댑터
    분리 패턴.
*/
@Component
@Profile("!prod")
public class RecordingAssemblyStubAdapter implements RecordingAssemblyPort {

    private static final Logger log = LoggerFactory.getLogger(RecordingAssemblyStubAdapter.class);

    @Override
    public void startAssembly(Long meetingId, int lastSegmentSeq, int lastSeq) {
        log.info("녹음 조립 트리거(stub) — meetingId={}, lastSegmentSeq={}, lastSeq={}. "
                + "로컬/테스트 전용, 실제 조립은 prod의 RecordingAssemblyS3FfmpegAdapter가 수행.",
                meetingId, lastSegmentSeq, lastSeq);
    }
}
