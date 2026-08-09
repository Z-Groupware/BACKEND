package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.usecase.GetPlaybackUrlUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 재생용 presigned URL 발급(CAP-14): 열람 권한(CapMeetingAccessGuard) 확인 → 녹음본 조회 → presigned GET 발급.
// 읽기 전용 조회.
@Service
@Transactional(readOnly = true)
public class PlaybackUrlService implements GetPlaybackUrlUseCase {

    private final CapMeetingAccessGuard accessGuard;
    private final RecordingRepository recordingRepository;
    private final CapObjectStoragePort capObjectStoragePort;

    public PlaybackUrlService(CapMeetingAccessGuard accessGuard,
                              RecordingRepository recordingRepository,
                              CapObjectStoragePort capObjectStoragePort) {
        this.accessGuard = accessGuard;
        this.recordingRepository = recordingRepository;
        this.capObjectStoragePort = capObjectStoragePort;
    }

    @Override
    public Result getPlaybackUrl(Long meetingId, CapMeetingAccessGuard.ViewerContext requester) {
        // 열람 권한(403): 참석자 / 같은 회사 owner·admin / 프로젝트 멤버. 아니면 거부.
        if (!accessGuard.canView(meetingId, requester)) {
            throw new BusinessException(CapErrorCode.CAP_NOT_ATTENDEE);
        }

        // 녹음본이 없으면 404(아직 조립/업로드 전이거나 삭제됨).
        Recording recording = recordingRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_RECORDING_NOT_FOUND));

        // 발급 시점 권한을 통과했으므로 presigned GET URL 발급.
        CapObjectStoragePort.IssuedPlaybackUrl issued = capObjectStoragePort.issuePlaybackUrl(recording.getFileUrl());

        // durationSec(초, nullable) → durationMs. 파이프라인이 아직 안 채웠으면 0.
        long durationMs = recording.getDurationSec() != null ? recording.getDurationSec() * 1000L : 0L;

        return new Result(issued.url(), issued.expiresInSeconds(), durationMs);
    }
}
