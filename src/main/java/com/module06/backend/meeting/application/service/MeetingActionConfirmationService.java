package com.module06.backend.meeting.application.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.application.port.in.MeetingActionConfirmationPort;
import com.module06.backend.meeting.domain.repository.MeetingActionConfirmationRepository;

/* RVW-05의 확정 시각을 회의 목록 노출 기준으로 저장하는 D 도메인 서비스다. */
@Service
@RequiredArgsConstructor
public class MeetingActionConfirmationService implements MeetingActionConfirmationPort {

    /* 최초 확정 시각만 기록하는 D 도메인 저장소다. */
    private final MeetingActionConfirmationRepository meetingActionConfirmationRepository;

    /* 액션 분배와 같은 트랜잭션에 참여해 확정 상태가 서로 어긋나지 않도록 저장한다. */
    @Override
    @Transactional
    public void confirmActions(Long companyId, Long meetingId, LocalDateTime confirmedAt) {
        /* RVW-05가 검증한 회사·회의와 한 번 읽은 확정 시각을 그대로 저장소에 전달한다. */
        meetingActionConfirmationRepository.confirmActionsIfAbsent(companyId, meetingId, confirmedAt);
    }
}
