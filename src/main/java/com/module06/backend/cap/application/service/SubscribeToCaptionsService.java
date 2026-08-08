package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.port.out.CaptionStreamPort;
import com.module06.backend.cap.application.usecase.SubscribeToCaptionsUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 자막 실시간 구독(CAP-13): 회의 존재(404) → 참석자(403, 전원 가능) → SSE 연결 등록.
// 자막 저장(CAP-11)이 들어오면 이 연결로 실시간 push된다. 트랜잭션은 인가 확인 두 쿼리 구간에만 걸린다 —
// emitter를 반환하는 즉시 메서드가 끝나므로 스트리밍 자체가 트랜잭션에 물려 있지 않다(다른 읽기 전용
// cap 서비스와 동일하게 @Transactional(readOnly = true)를 붙인다).
@Service
@Transactional(readOnly = true)
public class SubscribeToCaptionsService implements SubscribeToCaptionsUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CaptionStreamPort captionStreamPort;

    public SubscribeToCaptionsService(MeetingReferenceRepository meetingReferenceRepository,
                                      CaptionStreamPort captionStreamPort) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.captionStreamPort = captionStreamPort;
    }

    @Override
    public SseEmitter subscribeToCaptions(Long meetingId, Long memberId) {
        // 인가: 회의 존재(404) → 참석자(403). 참석자 전원 가능 — 열람권한 확대(owner/admin 등)는 없음(스펙).
        if (!meetingReferenceRepository.existsById(meetingId)) {
            throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        }
        if (!meetingReferenceRepository.isAttendee(meetingId, memberId)) {
            throw new BusinessException(CapErrorCode.CAP_NOT_ATTENDEE);
        }
        return captionStreamPort.subscribe(meetingId, memberId);
    }
}
