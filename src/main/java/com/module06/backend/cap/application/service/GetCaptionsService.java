package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.usecase.GetCaptionsUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 자막 전체 조회(CAP-12): 회의 존재(404) → 열람 권한(CapMeetingAccessGuard) → 시간순 전체 백필.
// 읽기 전용. 정본 아님(캡션 그대로) — SSE(CAP-13)는 구독 시점 이후만 push하므로, 그 이전 분은 이 API로 백필한다.
@Service
@Transactional(readOnly = true)
public class GetCaptionsService implements GetCaptionsUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapMeetingAccessGuard accessGuard;
    private final CaptionChunkRepository captionChunkRepository;

    public GetCaptionsService(MeetingReferenceRepository meetingReferenceRepository,
                              CapMeetingAccessGuard accessGuard,
                              CaptionChunkRepository captionChunkRepository) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.captionChunkRepository = captionChunkRepository;
    }

    @Override
    public Result getCaptions(Long meetingId, Requester requester) {
        // 회의가 없으면 404 — 존재 여부를 회사 밖으로 노출하지 않도록 열람 권한 판정 전에 먼저 본다.
        if (!meetingReferenceRepository.existsById(meetingId)) {
            throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        }
        // 열람 권한(403): 참석자 / 같은 회사 owner·admin / 프로젝트 멤버. 아니면 거부.
        if (!accessGuard.canView(meetingId, toViewerContext(requester))) {
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

    private CapMeetingAccessGuard.ViewerContext toViewerContext(Requester requester) {
        return new CapMeetingAccessGuard.ViewerContext(
                requester.memberId(), requester.companyId(), requester.teamId(), requester.role(), requester.isAdmin());
    }
}
