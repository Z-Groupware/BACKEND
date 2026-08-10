package com.module06.backend.cap.application.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;

/*
 * RecordingAssemblyPort 호출을 비동기 경계 뒤로 감춘다.
 *
 * <h2>왜 어댑터가 아니라 여기에 @Async를 두는가</h2>
 * 이 저장소의 관례는 @Async를 어댑터가 아니라 애플리케이션 계층에 두는 것이다
 * (SttBlockCutTrigger·MeetingCompletedAnalysisTrigger 참고 — 어댑터는 전부 동기 구현체다).
 * 어댑터에 직접 붙이면 포트 계약이 구현체마다 달라 보일 수 있다(스텁은 원래도 순식간에
 * 끝나 문제가 없었는데, 실 어댑터만 비동기가 되는 비대칭).
 *
 * <h2>왜 RecordingAssemblyService(CAP-05)에도 필요한가</h2>
 * CAP-05 컨트롤러는 202 Accepted로 즉시 반환하는 계약이다. 하지만 그 서비스 메서드는 검증 후
 * Result를 동기로 반환해야 해서 메서드 전체를 @Async로 만들 수 없다 — 포트 호출 한 줄만 이
 * 협력자로 위임해 그 경계만 비동기로 만든다. self-invocation으로 @Async가 씹히는 문제를 피하려고
 * 별도 빈으로 뺀 SttBlockFormedWriter·CompletePartUploadWriter와 같은 이유의 분리다.
 *
 * MeetingCompletedAssemblyTrigger(MEET-08)는 이미 @Async 리스너 안에서 부르지만, 포트를 부르는
 * 경계 자체는 호출자가 누구든 똑같이 이 협력자를 거치게 해 어댑터를 항상 동기로 유지한다.
 */
@Component
class RecordingAssemblyDispatcher {

    private final RecordingAssemblyPort recordingAssemblyPort;

    RecordingAssemblyDispatcher(RecordingAssemblyPort recordingAssemblyPort) {
        this.recordingAssemblyPort = recordingAssemblyPort;
    }

    @Async("recordingAssemblyTaskExecutor")
    void dispatch(Long meetingId, int lastSegmentSeq, int lastSeq) {
        recordingAssemblyPort.startAssembly(meetingId, lastSegmentSeq, lastSeq);
    }
}
