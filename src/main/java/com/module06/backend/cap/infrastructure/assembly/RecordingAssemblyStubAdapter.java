package com.module06.backend.cap.infrastructure.assembly;

import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/* comment.
    RecordingAssemblyPort의 스텁 구현 — 실제 조립 인프라(ffmpeg·S3 병렬 다운로드·duration 복구·
    parts 삭제)가 배선되기 전까지 트리거를 로그로만 남긴다. CapObjectStorageStubAdapter와 동일한
    "실 어댑터 나오기 전 스텁" 패턴. 실 파이프라인이 생기면 이 빈을 교체한다.
*/
@Component
public class RecordingAssemblyStubAdapter implements RecordingAssemblyPort {

    private static final Logger log = LoggerFactory.getLogger(RecordingAssemblyStubAdapter.class);

    @Override
    public void startAssembly(Long meetingId, int lastSegmentSeq, int lastSeq) {
        log.info("녹음 조립 트리거(stub) — meetingId={}, lastSegmentSeq={}, lastSeq={}. "
                + "실제 조립·duration 복구·parts 삭제는 후속 인프라에서 수행.", meetingId, lastSegmentSeq, lastSeq);
    }
}
