package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meetingroom.domain.model.ReservedSlot;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomSlotRepository;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingRoomSlotJpaEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.repository.SpringDataMeetingRoomSlotRepository;

/*
 * ROOM-02의 실제 슬롯 조회 조건과 영속성 어댑터 변환을 검증하는 통합 테스트다.
 *
 * 여러 회의실·날짜·회사의 슬롯을 섞어 저장해
 * 회의실 필터, 하루 범위 경계, 회사 격리, 회의 제목 채움이 조회 단계에서 보장되는지 확인한다.
 */
@SpringBootTest
@Transactional
@DisplayName("ROOM-02 예약 슬롯 영속성 어댑터")
class MeetingRoomSlotPersistenceAdapterTest {

    /* 테스트 슬롯 데이터를 저장하고 초기화할 Spring Data JPA 저장소다. */
    @Autowired
    private SpringDataMeetingRoomSlotRepository springDataMeetingRoomSlotRepository;

    /* 슬롯이 가리킬 완전한 회의 데이터를 준비하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* application 계층이 실제로 사용하는 슬롯 도메인 저장소 계약이다. */
    @Autowired
    private MeetingRoomSlotRepository meetingRoomSlotRepository;

    /*
     * 각 테스트가 서로의 데이터에 영향을 주지 않도록 슬롯과 회의 데이터를 초기화한다.
     */
    @BeforeEach
    void clearSlots() {
        /* 슬롯이 회의를 참조하므로 슬롯을 먼저 삭제한다. */
        springDataMeetingRoomSlotRepository.deleteAll();
        springDataMeetingRepository.deleteAll();
    }

    /*
     * 요청 회의실과 조회 날짜에 해당하는 슬롯만 회의 제목과 함께 조회되는지 검증한다.
     */
    @Test
    @DisplayName("요청 회의실의 당일 슬롯만 회의 제목과 함께 조회한다")
    void findsSlotsOfRequestedRoomsAndDay() {
        /* 같은 회사의 회의 두 건을 저장한다. */
        MeetingJpaEntity firstMeeting = springDataMeetingRepository.save(meeting(10L, "온보딩 킥오프"));
        MeetingJpaEntity secondMeeting = springDataMeetingRepository.save(meeting(10L, "주간 회의"));

        /* 조회 대상 회의실의 당일 슬롯, 다음 날 슬롯, 조회 대상이 아닌 회의실의 슬롯을 저장한다. */
        springDataMeetingRoomSlotRepository.save(
                slot(2L, LocalDateTime.of(2026, 8, 4, 14, 0), firstMeeting.getId())
        );
        springDataMeetingRoomSlotRepository.save(
                slot(2L, LocalDateTime.of(2026, 8, 4, 14, 30), firstMeeting.getId())
        );
        springDataMeetingRoomSlotRepository.save(
                slot(2L, LocalDateTime.of(2026, 8, 5, 9, 0), secondMeeting.getId())
        );
        springDataMeetingRoomSlotRepository.save(
                slot(3L, LocalDateTime.of(2026, 8, 4, 9, 0), secondMeeting.getId())
        );

        /* 2번 회의실의 8월 4일 하루 범위를 조회한다. */
        List<ReservedSlot> result = meetingRoomSlotRepository.findReservedSlots(
                10L,
                List.of(2L),
                LocalDateTime.of(2026, 8, 4, 0, 0),
                LocalDateTime.of(2026, 8, 5, 0, 0)
        );

        /* 다른 회의실과 다음 날 슬롯이 제외되고 제목이 채워진 두 칸만 반환되는지 확인한다. */
        assertThat(result)
                .extracting(ReservedSlot::slotStart)
                .containsExactly(
                        LocalDateTime.of(2026, 8, 4, 14, 0),
                        LocalDateTime.of(2026, 8, 4, 14, 30)
                );
        assertThat(result)
                .extracting(ReservedSlot::meetingTitle)
                .containsOnly("온보딩 킥오프");
        assertThat(result)
                .extracting(ReservedSlot::meetingId)
                .containsOnly(firstMeeting.getId());
    }

    /*
     * 다른 회사의 회의가 점유한 슬롯이 조회 결과에서 제외되는지 검증한다.
     */
    @Test
    @DisplayName("다른 회사 회의의 슬롯은 조회 결과에서 제외한다")
    void excludesSlotsOfOtherCompanyMeetings() {
        /* 요청 회사와 다른 회사의 회의를 각각 저장한다. */
        MeetingJpaEntity ownCompanyMeeting = springDataMeetingRepository.save(meeting(10L, "우리 회사 회의"));
        MeetingJpaEntity otherCompanyMeeting = springDataMeetingRepository.save(meeting(20L, "다른 회사 회의"));

        /* 같은 회의실 번호에 두 회사의 회의 슬롯이 각각 존재하는 상황을 만든다. */
        springDataMeetingRoomSlotRepository.save(
                slot(2L, LocalDateTime.of(2026, 8, 4, 9, 0), ownCompanyMeeting.getId())
        );
        springDataMeetingRoomSlotRepository.save(
                slot(2L, LocalDateTime.of(2026, 8, 4, 9, 30), otherCompanyMeeting.getId())
        );

        /* 회사 식별자 10으로 조회한다. */
        List<ReservedSlot> result = meetingRoomSlotRepository.findReservedSlots(
                10L,
                List.of(2L),
                LocalDateTime.of(2026, 8, 4, 0, 0),
                LocalDateTime.of(2026, 8, 5, 0, 0)
        );

        /* 다른 회사 회의의 예약 정보가 현황판에 섞이지 않아야 한다. */
        assertThat(result)
                .extracting(ReservedSlot::meetingId)
                .containsExactly(ownCompanyMeeting.getId());
    }

    /*
     * 조회 대상 회의실이 없을 때 빈 목록을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("조회 대상 회의실이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoMeetingRoomRequested() {
        /* 회의실 식별자 목록이 비어 있으면 조회 없이 빈 목록이 반환돼야 한다. */
        List<ReservedSlot> result = meetingRoomSlotRepository.findReservedSlots(
                10L,
                List.of(),
                LocalDateTime.of(2026, 8, 4, 0, 0),
                LocalDateTime.of(2026, 8, 5, 0, 0)
        );

        /* Repository 계약에 따라 null이 아닌 빈 목록이 반환되는지 확인한다. */
        assertThat(result).isEmpty();
    }

    /*
     * 테스트용 슬롯 영속성 엔티티를 생성한다.
     *
     * @param meetingRoomId 슬롯을 점유한 회의실 식별자
     * @param slotStart 슬롯 시작 시각
     * @param meetingId 슬롯을 점유한 회의 식별자
     * @return 테스트 DB에 저장할 슬롯 엔티티
     */
    private MeetingRoomSlotJpaEntity slot(Long meetingRoomId, LocalDateTime slotStart, Long meetingId) {
        /* 복합 PK 구성 값과 점유 회의만 지정하고 생성 시각은 데이터베이스에 맡긴다. */
        return new MeetingRoomSlotJpaEntity(meetingRoomId, slotStart, meetingId);
    }

    /* ROOM-02 조회 테스트가 참조할 수 있도록 필수 컬럼을 모두 가진 회의 엔티티를 만든다. */
    private MeetingJpaEntity meeting(Long companyId, String title) {
        /* 운영 코드와 같은 회의 애그리거트를 거쳐 읽기 전용 참조가 조회할 실제 행을 생성한다. */
        Meeting meeting = Meeting.create(
                companyId,
                12L,
                100L,
                2L,
                3L,
                title,
                LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 10, 0),
                false,
                null,
                List.of(3L)
        );

        /* meeting 테이블의 NOT NULL 계약을 만족하는 완전한 영속성 엔티티로 변환한다. */
        return MeetingJpaEntity.from(meeting);
    }
}
