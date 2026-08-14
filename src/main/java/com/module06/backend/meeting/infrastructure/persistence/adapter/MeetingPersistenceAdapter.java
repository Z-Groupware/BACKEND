package com.module06.backend.meeting.infrastructure.persistence.adapter;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.repository.MeetingCancellationRepository;
import com.module06.backend.meeting.domain.repository.MeetingCompletionRepository;
import com.module06.backend.meeting.domain.repository.MeetingRepository;
import com.module06.backend.meeting.domain.repository.MeetingUpdateRepository;
import com.module06.backend.meeting.exception.MeetingErrorCode;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingReservationSlotId;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingReservationSlotJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingReservationSlotRepository;

/*
 * 회의 애그리거트 저장 계약을 JPA로 구현하는 아웃바운드 어댑터다.
 *
 * 회의, 슬롯, 참석자 저장을 같은 트랜잭션에서 순서대로 실행하고 슬롯 PK 충돌을 MT-002로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingPersistenceAdapter implements
        MeetingRepository,
        MeetingCompletionRepository,
        MeetingUpdateRepository,
        MeetingCancellationRepository {

    /* meeting 기본 행을 저장하고 데이터베이스 생성 식별자를 받는 기술 저장소다. */
    private final SpringDataMeetingRepository springDataMeetingRepository;

    /* 기존 참석자 행을 조회해 목표 명단과의 추가·삭제 차이를 계산하는 기술 저장소다. */
    private final SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* MEET-05가 기존 예약 슬롯과 최종 슬롯의 차이를 조회하는 기술 저장소다. */
    private final SpringDataMeetingReservationSlotRepository springDataMeetingReservationSlotRepository;

    /* 복합 PK 엔티티를 merge가 아닌 INSERT로 강제하기 위한 JPA 영속성 컨텍스트다. */
    private final EntityManager entityManager;

    /*
     * 회의 기본 행을 만든 뒤 슬롯과 참석자를 강제 INSERT하고 즉시 flush한다.
     *
     * @param meeting 저장할 신규 회의
     * @return 데이터베이스 생성 값이 반영된 회의
     */
    @Override
    public Meeting saveReservation(Meeting meeting) {
        /* IDENTITY 식별자를 확보해 자식 슬롯과 참석자 행에 사용할 수 있게 한다. */
        MeetingJpaEntity savedMeeting = springDataMeetingRepository.saveAndFlush(MeetingJpaEntity.from(meeting));

        /* 기존 슬롯을 UPDATE하지 않고 INSERT해 복합 PK를 동시성 관문으로 사용한다. */
        persistReservationSlots(meeting, savedMeeting.getId());

        /* 개설자를 포함해 검증된 참석자 전체를 입장 허용 명단으로 저장한다. */
        persistAttendees(meeting, savedMeeting.getId());

        /* 저장된 회의 값과 원래의 참석자 순서를 합쳐 도메인 애그리거트로 복원한다. */
        return savedMeeting.toDomain(meeting.getAttendeeMemberIds());
    }

    /*
     * 회의 기본 행과 참석자만 저장한다(MEET-18 비대면 회의).
     *
     * 회의실이 없어 예약 슬롯을 점유하지 않으므로 물리 회의와 달리 슬롯 저장을 생략한다.
     *
     * @param meeting 저장할 신규 온라인 회의
     * @return 데이터베이스 생성 값이 반영된 회의
     */
    @Override
    public Meeting saveOnlineReservation(Meeting meeting) {
        /* IDENTITY 식별자를 확보해 참석자 행에 사용할 수 있게 한다. */
        MeetingJpaEntity savedMeeting = springDataMeetingRepository.saveAndFlush(MeetingJpaEntity.from(meeting));

        /* 개설자를 포함해 검증된 참석자 전체를 입장 허용 명단으로 저장한다. */
        persistAttendees(meeting, savedMeeting.getId());

        /* 저장된 회의 값과 원래의 참석자 순서를 합쳐 도메인 애그리거트로 복원한다. */
        return savedMeeting.toDomain(meeting.getAttendeeMemberIds());
    }

    /* 회사 범위의 회의를 잠그고 종료 응답과 A 스냅숏에 필요한 최신 참석자까지 복원한다. */
    @Override
    public Optional<Meeting> findForCompletion(Long companyId, Long meetingId) {
        /* 회의 행 잠금을 먼저 획득해 CAP 시작과 중복 종료 요청을 하나씩 처리한다. */
        return springDataMeetingRepository.findLockedByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> meeting.toDomain(
                        springDataMeetingAttendeeRepository
                                .findAllByMeetingIdOrderByMemberIdAsc(meeting.getId())
                                .stream()
                                .map(MeetingAttendeeJpaEntity::getMemberId)
                                .toList()
                ));
    }

    /* 완료된 회의의 DONE 상태와 endedAt을 저장하고 최신 영속성 값을 복원한다. */
    @Override
    public Meeting saveCompleted(Meeting meeting) {
        /* 식별자가 있는 meeting 행을 갱신하고 종료 상태를 트랜잭션 안에서 즉시 반영한다. */
        MeetingJpaEntity savedMeeting = springDataMeetingRepository.saveAndFlush(MeetingJpaEntity.from(meeting));

        /* 종료 시점에 확정된 참석자 명단은 변경하지 않고 도메인 결과에 그대로 유지한다. */
        return savedMeeting.toDomain(meeting.getAttendeeMemberIds());
    }

    /* 회사 범위의 수정 대상 회의를 잠그고 최신 참석자 명단과 함께 복원한다. */
    @Override
    public Optional<Meeting> findForUpdate(Long companyId, Long meetingId) {
        /* 회의 행 잠금으로 같은 회의의 수정·입장·종료·참석자 교체 요청을 직렬화한다. */
        return springDataMeetingRepository.findLockedByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> meeting.toDomain(
                        springDataMeetingAttendeeRepository
                                .findAllByMeetingIdOrderByMemberIdAsc(meeting.getId())
                                .stream()
                                .map(MeetingAttendeeJpaEntity::getMemberId)
                                .toList()
                ));
    }

    /* 수정된 meeting 행과 변경된 예약 슬롯을 같은 트랜잭션에서 저장한다. */
    @Override
    public Meeting saveUpdate(Meeting meeting, boolean reservationChanged) {
        /* 관리 엔티티 갱신을 즉시 반영해 필수 컬럼 오류가 이 저장 경계 밖으로 늦게 새지 않게 한다. */
        MeetingJpaEntity savedMeeting = springDataMeetingRepository.saveAndFlush(MeetingJpaEntity.from(meeting));

        /* 회의실 또는 시간의 실제 값이 달라진 경우에만 예약 슬롯 차이를 반영한다. */
        if (reservationChanged) {
            synchronizeReservationSlots(meeting);
        }

        /* 수정 API는 참석자 명단을 바꾸지 않으므로 잠금 조회에서 복원한 목록을 그대로 유지한다. */
        return savedMeeting.toDomain(meeting.getAttendeeMemberIds());
    }

    /* 회사 범위의 취소 대상 회의를 잠그고 최신 참석자 명단과 함께 복원한다. */
    @Override
    public Optional<Meeting> findForCancellation(Long companyId, Long meetingId) {
        /* 회의 행 잠금으로 같은 회의의 취소·수정·입장·종료 요청을 하나씩 처리한다. */
        return springDataMeetingRepository.findLockedByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> meeting.toDomain(
                        springDataMeetingAttendeeRepository
                                .findAllByMeetingIdOrderByMemberIdAsc(meeting.getId())
                                .stream()
                                .map(MeetingAttendeeJpaEntity::getMemberId)
                                .toList()
                ));
    }

    /* 취소 상태를 저장하고 현재 회의가 점유한 모든 예약 슬롯을 같은 트랜잭션에서 해제한다. */
    @Override
    public Meeting saveCancellationAndReleaseSlots(Meeting meeting) {
        /* CANCELED와 canceledAt을 먼저 영속성 컨텍스트에 반영해 상태 이력을 보존한다. */
        MeetingJpaEntity savedMeeting = springDataMeetingRepository.saveAndFlush(MeetingJpaEntity.from(meeting));

        /* 슬롯은 예약 충돌을 막는 현재 점유 정보이므로 취소 시 모두 삭제해 재예약 가능하게 한다. */
        springDataMeetingReservationSlotRepository
                .findAllByMeetingIdOrderBySlotStartAsc(meeting.getId())
                .forEach(entityManager::remove);

        /* 상태 변경과 슬롯 해제의 무결성 오류가 트랜잭션 밖으로 늦게 새지 않도록 즉시 반영한다. */
        entityManager.flush();

        /* 참석자 이력은 삭제하지 않고 잠금 조회에서 복원한 명단과 저장 결과를 합친다. */
        return savedMeeting.toDomain(meeting.getAttendeeMemberIds());
    }

    /* 기존 참석자 중 빠진 행을 삭제하고 새로 추가된 행을 저장해 명단을 전체 교체한다. */
    @Override
    public void replaceAttendees(Long meetingId, List<Long> attendeeMemberIds) {
        try {
            /* 현재 명단을 영속성 컨텍스트의 관리 엔티티로 조회한다. */
            List<MeetingAttendeeJpaEntity> existingAttendees = springDataMeetingAttendeeRepository
                    .findAllByMeetingIdOrderByMemberIdAsc(meetingId);

            /* 목표 명단과 기존 명단을 집합으로 만들어 추가·삭제 대상을 빠르게 판정한다. */
            Set<Long> targetMemberIds = Set.copyOf(attendeeMemberIds);
            Set<Long> existingMemberIds = existingAttendees.stream()
                    .map(MeetingAttendeeJpaEntity::getMemberId)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));

            /* 목표 명단에서 빠진 기존 참석자 행만 삭제한다. */
            existingAttendees.stream()
                    .filter(attendee -> !targetMemberIds.contains(attendee.getMemberId()))
                    .forEach(entityManager::remove);

            /* 기존 명단에 없던 목표 참석자만 신규 복합 PK 행으로 저장한다. */
            attendeeMemberIds.stream()
                    .filter(memberId -> !existingMemberIds.contains(memberId))
                    .map(memberId -> new MeetingAttendeeJpaEntity(meetingId, memberId))
                    .forEach(entityManager::persist);

            /* 교체 결과를 트랜잭션 종료 전에 반영해 FK·PK 오류를 이 경계에서 변환한다. */
            entityManager.flush();
        } catch (PersistenceException exception) {
            /* 검증 이후 구성원 삭제 등 경합으로 발생한 무결성 오류를 MT-010으로 통일한다. */
            throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
        }
    }

    /* 회의가 점유할 모든 슬롯을 persist하고 PK 충돌을 도메인 오류로 변환한다. */
    private void persistReservationSlots(Meeting meeting, Long meetingId) {
        try {
            /* 한 슬롯마다 신규 엔티티 상태로 persist해 기존 예약을 덮어쓰지 않는다. */
            for (var slotStart : meeting.reservationSlotStarts()) {
                entityManager.persist(new MeetingReservationSlotJpaEntity(
                        meeting.getMeetingRoomId(),
                        slotStart,
                        meetingId
                ));
            }

            /* 트랜잭션 종료까지 미루지 않고 여기서 PK 충돌을 발생시켜 MT-002로 변환한다. */
            entityManager.flush();
        } catch (PersistenceException exception) {
            /* 중복 슬롯 예약은 재시도하지 않고 사용자에게 다른 시간 선택을 요청한다. */
            throw new BusinessException(MeetingErrorCode.MEETING_ROOM_TIME_CONFLICT);
        }
    }

    /* 개설자를 포함한 참석자 명단을 persist하고 참조 무결성 오류를 MT-010으로 변환한다. */
    private void persistAttendees(Meeting meeting, Long meetingId) {
        try {
            /* 도메인에서 중복 제거된 순서대로 참석자 행을 신규 INSERT한다. */
            for (Long memberId : meeting.getAttendeeMemberIds()) {
                entityManager.persist(new MeetingAttendeeJpaEntity(meetingId, memberId));
            }

            /* FK나 복합 PK 오류가 서비스 밖으로 늦게 새지 않도록 즉시 반영한다. */
            entityManager.flush();
        } catch (PersistenceException exception) {
            /* 검증 이후 구성원이 삭제되는 경합도 참석자 명단 오류로 일관되게 응답한다. */
            throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
        }
    }

    /* 기존 슬롯과 최종 슬롯의 삭제·유지·추가 차이를 반영하고 충돌을 MT-002로 변환한다. */
    private void synchronizeReservationSlots(Meeting meeting) {
        try {
            /* 현재 회의가 점유한 슬롯을 관리 엔티티로 조회해 같은 복합 키는 그대로 유지한다. */
            List<MeetingReservationSlotJpaEntity> existingSlots = springDataMeetingReservationSlotRepository
                    .findAllByMeetingIdOrderBySlotStartAsc(meeting.getId());

            /* 최종 회의실과 시간 범위가 요구하는 복합 키 집합을 만든다. */
            Set<MeetingReservationSlotId> targetSlotIds = meeting.reservationSlotStarts().stream()
                    .map(slotStart -> new MeetingReservationSlotId(meeting.getMeetingRoomId(), slotStart))
                    .collect(java.util.stream.Collectors.toSet());

            /* 최종 예약에 포함되지 않는 기존 슬롯만 제거해 겹치는 슬롯의 불필요한 재삽입을 막는다. */
            existingSlots.stream()
                    .filter(slot -> !targetSlotIds.contains(new MeetingReservationSlotId(
                            slot.getMeetingRoomId(),
                            slot.getSlotStart()
                    )))
                    .forEach(entityManager::remove);

            /* 기존 복합 키를 색인해 최종 예약 중 새로 필요한 슬롯만 강제 INSERT한다. */
            Set<MeetingReservationSlotId> existingSlotIds = existingSlots.stream()
                    .map(slot -> new MeetingReservationSlotId(slot.getMeetingRoomId(), slot.getSlotStart()))
                    .collect(java.util.stream.Collectors.toSet());
            targetSlotIds.stream()
                    .filter(slotId -> !existingSlotIds.contains(slotId))
                    .map(slotId -> new MeetingReservationSlotJpaEntity(
                            slotId.getMeetingRoomId(),
                            slotId.getSlotStart(),
                            meeting.getId()
                    ))
                    .forEach(entityManager::persist);

            /* 삭제와 INSERT를 즉시 실행해 다른 회의 슬롯과의 복합 PK 충돌을 이 경계에서 확인한다. */
            entityManager.flush();
        } catch (PersistenceException exception) {
            /* 신규 슬롯 중 하나라도 이미 점유됐다면 meeting 변경까지 전체 롤백하도록 공개 오류로 변환한다. */
            throw new BusinessException(MeetingErrorCode.MEETING_ROOM_TIME_CONFLICT);
        }
    }
}
