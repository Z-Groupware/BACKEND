package com.module06.backend.meetingroom.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meetingroom.domain.model.ReservedSlot;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomSlotRepository;

/*
 * MeetingRoomSlotRepository 도메인 계약을 JPA로 구현하는 아웃바운드 어댑터다.
 *
 * 슬롯 행 조회와 회의 제목 조회를 각각 한 번씩만 실행하고 애플리케이션 계층에는 도메인 값 객체만 넘긴다.
 * 슬롯 테이블에는 회사 컬럼이 없으므로, 회의 조회 결과에 없는 슬롯은 요청 회사의 예약이 아니라고 보고 제외해
 * 다른 회사의 예약 정보가 현황판에 섞이지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingRoomSlotPersistenceAdapter implements MeetingRoomSlotRepository {

    /* 실제 meeting_room_slot 조회 쿼리를 실행하는 기술 저장소다. */
    private final SpringDataMeetingRoomSlotRepository springDataMeetingRoomSlotRepository;

    /* 슬롯을 점유한 회의의 제목과 회사 스코프를 확인하는 읽기 전용 기술 저장소다. */
    private final SpringDataMeetingReferenceRepository springDataMeetingReferenceRepository;

    /*
     * 회의실 목록과 기간 조건에 해당하는 예약 슬롯을 도메인 값 객체 목록으로 반환한다.
     *
     * @param companyId 인증된 요청자의 회사 식별자
     * @param meetingRoomIds 조회 대상 회의실 식별자 목록
     * @param fromInclusive 조회 시작 일시, 이 시각을 포함한다
     * @param toExclusive 조회 종료 일시, 이 시각을 포함하지 않는다
     * @return 회의 제목이 채워진 예약 슬롯 목록, 조회 결과가 없으면 빈 목록
     */
    @Override
    public List<ReservedSlot> findReservedSlots(
            Long companyId,
            List<Long> meetingRoomIds,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        /* 조회 대상 회의실이 없으면 IN 조건이 비어 무의미한 질의가 되므로 즉시 빈 목록을 반환한다. */
        if (meetingRoomIds == null || meetingRoomIds.isEmpty()) {
            return List.of();
        }

        /* 회의 테이블 범위 조회 없이 물질화된 슬롯 행만 읽는다. */
        List<MeetingRoomSlotJpaEntity> slots = springDataMeetingRoomSlotRepository
                .findAllByMeetingRoomIdInAndSlotStartGreaterThanEqualAndSlotStartLessThanOrderByMeetingRoomIdAscSlotStartAsc(
                        meetingRoomIds,
                        fromInclusive,
                        toExclusive
                );

        /* 예약이 하나도 없으면 회의 제목을 조회할 필요가 없다. */
        if (slots.isEmpty()) {
            return List.of();
        }

        /* 슬롯이 가리키는 회의의 제목을 회사 조건과 함께 한 번에 조회한다. */
        Map<Long, String> titlesByMeetingId = findMeetingTitles(companyId, slots);

        /* 요청 회사의 회의가 아닌 슬롯은 제외하고, 남은 슬롯에 제목을 채워 도메인 값 객체로 변환한다. */
        return slots.stream()
                .filter(slot -> titlesByMeetingId.containsKey(slot.getMeetingId()))
                .map(slot -> new ReservedSlot(
                        slot.getMeetingRoomId(),
                        slot.getSlotStart(),
                        slot.getMeetingId(),
                        titlesByMeetingId.get(slot.getMeetingId())
                ))
                .toList();
    }

    /*
     * 슬롯이 점유한 회의의 제목을 회사 스코프 안에서 조회한다.
     *
     * @param companyId 인증된 요청자의 회사 식별자
     * @param slots 조회된 슬롯 영속성 엔티티 목록
     * @return 회의 식별자를 키로 갖는 회의 제목 맵
     */
    private Map<Long, String> findMeetingTitles(Long companyId, List<MeetingRoomSlotJpaEntity> slots) {
        /* 하나의 회의가 여러 슬롯을 점유하므로 중복을 제거한 식별자 목록으로 조회한다. */
        List<Long> meetingIds = slots.stream()
                .map(MeetingRoomSlotJpaEntity::getMeetingId)
                .distinct()
                .toList();

        /* 회의 식별자는 기본 키라 결과가 중복되지 않으므로 그대로 맵으로 모은다. */
        return springDataMeetingReferenceRepository.findAllByIdInAndCompanyId(meetingIds, companyId).stream()
                .collect(Collectors.toMap(MeetingReferenceEntity::getId, MeetingReferenceEntity::getTitle));
    }
}
