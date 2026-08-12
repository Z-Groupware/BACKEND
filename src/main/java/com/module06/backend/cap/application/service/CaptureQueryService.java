package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.port.out.CaptureHeartbeatPort;
import com.module06.backend.cap.application.usecase.GetActiveCaptureUseCase;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.ActiveCaptureQueryRepository;
import com.module06.backend.cap.domain.repository.CapCaptureSessionReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

// 진행 중 캡처 조회(CAP-09)의 실제 로직. 저장소가 준 캡처 상태에 하트비트(canTakeover)와 경과 시간(elapsedMs)을
// 덧붙여 응답 결과로 만든다. 읽기 전용 트랜잭션.
@Service
@Transactional(readOnly = true)
public class CaptureQueryService implements GetActiveCaptureUseCase {

    private final ActiveCaptureQueryRepository activeCaptureQueryRepository;
    private final CaptureHeartbeatPort captureHeartbeatPort;
    private final CapCaptureSessionReferenceRepository captureSessionReferenceRepository;

    public CaptureQueryService(ActiveCaptureQueryRepository activeCaptureQueryRepository,
                               CaptureHeartbeatPort captureHeartbeatPort,
                               CapCaptureSessionReferenceRepository captureSessionReferenceRepository) {
        this.activeCaptureQueryRepository = activeCaptureQueryRepository;
        this.captureHeartbeatPort = captureHeartbeatPort;
        this.captureSessionReferenceRepository = captureSessionReferenceRepository;
    }

    @Override
    public Optional<Result> getActiveCapture(Long memberId) {
        return activeCaptureQueryRepository.findActiveCaptureForMember(memberId)
                .map(this::toResult);
    }

    // 캡처 상태 → 응답 결과. canTakeover는 하트비트가 끊겼는지(!isAlive), elapsedMs는 캡처 시작(createdAt) 이후 경과.
    private Result toResult(CaptureUploadState state) {
        boolean canTakeover = !captureHeartbeatPort.isAlive(state.getMeetingId());
        return new Result(
                state.getMeetingId(),
                captureSessionReferenceRepository.findSessionId(state.getMeetingId()).orElse(null),
                state.getSegmentSeq(),
                state.getLastSeq(),
                state.getRecorderPersonId(),
                canTakeover,
                elapsedMsSince(state.getCreatedAt()));
    }

    // createdAt(첫 presign 시각)부터 지금까지 경과 ms. createdAt이 없거나(이론상) 음수가 나오면 0으로 바닥 처리.
    private long elapsedMsSince(LocalDateTime createdAt) {
        if (createdAt == null) {
            return 0L;
        }
        long elapsed = Duration.between(createdAt, LocalDateTime.now()).toMillis();
        return Math.max(elapsed, 0L);
    }
}
