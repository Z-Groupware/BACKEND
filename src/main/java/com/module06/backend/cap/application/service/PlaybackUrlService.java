package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.usecase.GetPlaybackUrlUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 재생용 presigned URL 발급(CAP-14): 열람 권한(참석자) 확인 → 녹음본 조회 → presigned GET 발급.
// 읽기 전용 조회. 프로젝트 멤버까지 확대 열람은 CAP 도메인 공통 access-guard 후속 이슈에서 다룬다(지금은 참석자).
@Service
@Transactional(readOnly = true)
public class PlaybackUrlService implements GetPlaybackUrlUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final RecordingRepository recordingRepository;
    private final CapObjectStoragePort capObjectStoragePort;

    public PlaybackUrlService(MeetingReferenceRepository meetingReferenceRepository,
                              RecordingRepository recordingRepository,
                              CapObjectStoragePort capObjectStoragePort) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.recordingRepository = recordingRepository;
        this.capObjectStoragePort = capObjectStoragePort;
    }

    @Override
    public Result getPlaybackUrl(Long meetingId, Requester requester) {
        // 열람 권한(403): 참석자거나, 같은 회사의 owner/admin(감독 열람)만. 아니면 거부.
        if (!canView(meetingId, requester)) {
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

    // 참석자면 무조건 허용(참석자는 회의=회사 소속 보장). 참석자가 아니면 "같은 회사의 owner/admin"만
    // 감독 열람 허용 — 타 회사 owner/admin이 남의 회의 녹음을 여는 cross-tenant를 발급 시점 회사 스코프로 차단한다.
    private boolean canView(Long meetingId, Requester requester) {
        if (meetingReferenceRepository.isAttendee(meetingId, requester.memberId())) {
            return true;
        }
        if (!requester.isOwnerOrAdmin()) {
            return false;
        }
        return meetingReferenceRepository.findCompanyId(meetingId)
                .map(companyId -> companyId.equals(requester.companyId()))
                .orElse(false);
    }
}

