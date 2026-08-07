package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.usecase.GetCaptionsUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 자막 전체 조회(CAP-12): 회의 존재(404) → 열람 권한(참석자 또는 같은 회사 owner/admin, 403) → 시간순 전체 백필.
// 읽기 전용. 정본 아님(캡션 그대로) — SSE(CAP-13)는 구독 시점 이후만 push하므로, 그 이전 분은 이 API로 백필한다.
// 프로젝트 멤버까지 확대 열람은 재생 URL(CAP-14)과 함께 CAP 도메인 공통 access-guard 후속 이슈에서 다룬다.
@Service
@Transactional(readOnly = true)
public class GetCaptionsService implements GetCaptionsUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CaptionChunkRepository captionChunkRepository;

    public GetCaptionsService(MeetingReferenceRepository meetingReferenceRepository,
                              CaptionChunkRepository captionChunkRepository) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.captionChunkRepository = captionChunkRepository;
    }

    @Override
    public Result getCaptions(Long meetingId, Requester requester) {
        // 회의가 없으면 404 — 존재 여부를 회사 밖으로 노출하지 않도록 열람 권한 판정 전에 먼저 본다.
        if (!meetingReferenceRepository.existsById(meetingId)) {
            throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        }
        // 열람 권한(403): 참석자거나, 같은 회사의 owner/admin(감독 열람)만. 아니면 거부.
        if (!canView(meetingId, requester)) {
            throw new BusinessException(CapErrorCode.CAP_NOT_ATTENDEE);
        }

        List<CaptionItem> captions = captionChunkRepository.findByMeetingId(meetingId).stream()
                .map(this::toItem)
                .toList();
        return new Result(captions);
    }

    private CaptionItem toItem(CaptionChunk chunk) {
        return new CaptionItem(chunk.getSeq(), chunk.getMemberId(), chunk.getStartOffsetMs(), chunk.getEndOffsetMs(),
                chunk.getText(), chunk.getRms());
    }

    // 재생 URL(PlaybackUrlService.canView)과 동일한 판정 기준 — 참석자면 무조건 허용(참석자는 회의=회사
    // 소속 보장), 아니면 같은 회사의 owner/admin만 감독 열람 허용(타 회사 cross-tenant는 회사 스코프로 차단).
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
