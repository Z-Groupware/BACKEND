package com.module06.backend.meeting.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.repository.CaptureSessionRepository;
import com.module06.backend.meeting.exception.CaptureSessionErrorCode;
import com.module06.backend.meeting.infrastructure.persistence.entity.CaptureSessionJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataCaptureSessionRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;

/*
 * CAP-01의 회의 잠금 조회와 capture_session 저장 계약을 JPA로 구현하는 어댑터다.
 */
@Component
@RequiredArgsConstructor
public class CaptureSessionPersistenceAdapter implements CaptureSessionRepository {

    /* 회사 범위 회의 행을 잠금 조회하는 기존 회의 기술 저장소다. */
    private final SpringDataMeetingRepository springDataMeetingRepository;

    /* 잠근 회의의 최신 예약 참석자 명단을 조회하는 기술 저장소다. */
    private final SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* 캡처 세션의 존재 확인과 저장을 수행하는 기술 저장소다. */
    private final SpringDataCaptureSessionRepository springDataCaptureSessionRepository;

    /* 회사 범위 회의와 참석자 명단을 잠금 없이 읽어 B 도메인 조회용 사전 스냅샷을 만든다. */
    @Override
    public Optional<Meeting> findMeeting(Long companyId, Long meetingId) {
        /* 원격 또는 타 도메인 호출 전에 필요한 ID만 읽으며 meeting 행의 쓰기 잠금은 획득하지 않는다. */
        return springDataMeetingRepository.findByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> meeting.toDomain(
                        springDataMeetingAttendeeRepository
                                .findAllByMeetingIdOrderByMemberIdAsc(meeting.getId())
                                .stream()
                                .map(MeetingAttendeeJpaEntity::getMemberId)
                                .toList()
                ));
    }

    /* 회사 범위 회의를 잠그고 host·상태·최신 참석자 명단을 가진 도메인으로 복원한다. */
    @Override
    public Optional<Meeting> findMeetingForStart(Long companyId, Long meetingId) {
        /* 같은 회의의 시작 요청을 직렬화하기 위해 meeting 행의 쓰기 잠금을 먼저 획득한다. */
        return springDataMeetingRepository.findLockedByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> meeting.toDomain(
                        springDataMeetingAttendeeRepository
                                .findAllByMeetingIdOrderByMemberIdAsc(meeting.getId())
                                .stream()
                                .map(MeetingAttendeeJpaEntity::getMemberId)
                                .toList()
                ));
    }

    /* 해당 회의의 캡처 세션 존재 여부를 파생 쿼리로 조회한다. */
    @Override
    public boolean existsByMeetingId(Long meetingId) {
        /* 빠른 사용자 오류 반환을 위한 사전 검사이며 최종 보장은 UNIQUE 제약이 담당한다. */
        return springDataCaptureSessionRepository.existsByMeetingId(meetingId);
    }

    /* 신규 캡처 세션을 저장하고 회의당 하나인 UNIQUE 충돌을 CS-002로 변환한다. */
    @Override
    public CaptureSession save(CaptureSession captureSession) {
        try {
            /* IDENTITY 식별자와 제약 위반을 트랜잭션 종료 전 확보하도록 즉시 flush한다. */
            CaptureSessionJpaEntity savedEntity = springDataCaptureSessionRepository.saveAndFlush(
                    CaptureSessionJpaEntity.from(captureSession)
            );

            /* 저장 기술을 숨기고 데이터베이스 식별자가 반영된 도메인 애그리거트를 반환한다. */
            return savedEntity.toDomain();
        } catch (DataIntegrityViolationException exception) {
            /* 동시 요청의 meeting_id UNIQUE 충돌을 공개 캡처 세션 오류로 통일한다. */
            throw new BusinessException(CaptureSessionErrorCode.CAPTURE_SESSION_ALREADY_EXISTS);
        }
    }
}
