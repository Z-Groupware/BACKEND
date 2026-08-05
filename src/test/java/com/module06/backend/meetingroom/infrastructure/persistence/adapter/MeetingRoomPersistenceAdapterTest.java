package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingRoomJpaEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.repository.SpringDataMeetingRoomRepository;

/*
 * ROOM-01의 실제 JPA 조회 조건과 영속성 어댑터 변환을 검증하는 통합 테스트다.
 *
 * 테스트 DB에 여러 회사와 활성 상태가 섞인 회의실을 저장해
 * 회사 격리, 소프트 삭제 제외, 이름 정렬이 데이터베이스 쿼리에서 보장되는지 확인한다.
 */
@SpringBootTest
@Transactional
@DisplayName("ROOM-01 회의실 목록 영속성 어댑터")
class MeetingRoomPersistenceAdapterTest {

    /* 테스트 데이터를 저장하고 초기화할 Spring Data JPA 저장소다. */
    @Autowired
    private SpringDataMeetingRoomRepository springDataMeetingRoomRepository;

    /* application 계층이 실제로 사용하는 회의실 도메인 저장소 계약이다. */
    @Autowired
    private MeetingRoomRepository meetingRoomRepository;

    /* MEET-03이 회의실 표시 정보를 일괄 조회할 때 사용하는 D 내부 Port다. */
    @Autowired
    private MeetingRoomQueryPort meetingRoomQueryPort;

    /*
     * 각 테스트가 서로의 데이터에 영향을 주지 않도록 meeting_room 데이터를 초기화한다.
     */
    @BeforeEach
    void clearMeetingRooms() {
        /* 이전 테스트에서 저장한 회의실을 모두 삭제한다. */
        springDataMeetingRoomRepository.deleteAll();
    }

    /*
     * 요청 회사의 활성 회의실만 이름순으로 조회되는지 검증한다.
     */
    @Test
    @DisplayName("같은 회사의 활성 회의실만 이름순으로 조회한다")
    void findsOnlyActiveMeetingRoomsInRequestedCompany() {
        /* 같은 회사의 활성 회의실 두 개를 정렬되지 않은 순서로 저장한다. */
        springDataMeetingRoomRepository.save(meetingRoom(10L, "소회의실", null));
        springDataMeetingRoomRepository.save(meetingRoom(10L, "대회의실", null));

        /* 같은 회사지만 비활성화된 회의실을 저장한다. */
        springDataMeetingRoomRepository.save(
                meetingRoom(10L, "폐쇄 회의실", LocalDateTime.of(2026, 8, 4, 9, 0))
        );

        /* 다른 회사의 활성 회의실을 저장한다. */
        springDataMeetingRoomRepository.save(meetingRoom(20L, "외부 회의실", null));

        /* companyId가 10인 회사의 활성 회의실을 조회한다. */
        List<MeetingRoom> result = meetingRoomRepository.findAllActiveByCompanyId(10L);

        /* 다른 회사와 비활성 회의실이 제외되고 이름 오름차순으로 반환되는지 확인한다. */
        assertThat(result)
                .extracting(MeetingRoom::getName)
                .containsExactly("대회의실", "소회의실");
        assertThat(result)
                .extracting(MeetingRoom::getCompanyId)
                .containsOnly(10L);
        assertThat(result)
                .allMatch(MeetingRoom::isActive);
    }

    /*
     * 조건에 맞는 회의실이 없을 때 빈 목록을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("조회할 활성 회의실이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoActiveMeetingRoomMatches() {
        /* 요청 회사와 다른 회사의 회의실만 저장한다. */
        springDataMeetingRoomRepository.save(meetingRoom(20L, "외부 회의실", null));

        /* 회의실을 등록하지 않은 회사로 조회한다. */
        List<MeetingRoom> result = meetingRoomRepository.findAllActiveByCompanyId(10L);

        /* Repository 계약에 따라 null이 아닌 빈 목록이 반환되는지 확인한다. */
        assertThat(result).isEmpty();
    }

    /*
     * 식별자와 회사가 모두 일치하는 활성 회의실만 단건 조회되는지 검증한다(ROOM-02 회의실 필터).
     */
    @Test
    @DisplayName("식별자와 회사가 일치하는 활성 회의실을 단건 조회한다")
    void findsActiveMeetingRoomByIdAndCompanyId() {
        /* 회사 10의 활성 회의실을 저장하고 생성된 식별자를 확보한다. */
        MeetingRoomJpaEntity saved = springDataMeetingRoomRepository.save(meetingRoom(10L, "대회의실", null));

        /* 같은 회사로 조회하면 회의실이 반환돼야 한다. */
        Optional<MeetingRoom> result = meetingRoomRepository.findActiveById(10L, saved.getId());

        /* 조회된 회의실의 정보가 저장한 값과 일치하는지 확인한다. */
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("대회의실");
        assertThat(result.get().isActive()).isTrue();

        /* 다른 회사로 같은 식별자를 조회하면 존재 여부가 드러나지 않도록 결과가 비어야 한다. */
        assertThat(meetingRoomRepository.findActiveById(20L, saved.getId())).isEmpty();
    }

    /*
     * 비활성화된 회의실이 단건 조회에서 제외되는지 검증한다.
     */
    @Test
    @DisplayName("비활성화된 회의실은 단건 조회에서 제외한다")
    void excludesDeletedMeetingRoomFromSingleLookup() {
        /* 비활성화 시각이 기록된 회의실을 저장한다. */
        MeetingRoomJpaEntity saved = springDataMeetingRoomRepository.save(
                meetingRoom(10L, "폐쇄 회의실", LocalDateTime.of(2026, 8, 4, 9, 0))
        );

        /* 활성 회의실만 조회 대상이므로 결과가 비어야 한다. */
        assertThat(meetingRoomRepository.findActiveById(10L, saved.getId())).isEmpty();
    }

    /* 예정 회의가 참조하는 비활성 회의실도 회사 범위 안에서 표시할 수 있는지 검증한다. */
    @Test
    @DisplayName("예정 회의용 배치 조회는 비활성 회의실을 포함하고 다른 회사는 제외한다")
    void findsMeetingRoomsForUpcomingMeetingsIncludingInactiveRoom() {
        /* 같은 회사의 활성·비활성 회의실과 다른 회사 회의실을 각각 저장한다. */
        MeetingRoomJpaEntity active = springDataMeetingRoomRepository.save(
                meetingRoom(10L, "활성 회의실", null)
        );
        MeetingRoomJpaEntity inactive = springDataMeetingRoomRepository.save(
                meetingRoom(10L, "비활성 회의실", LocalDateTime.of(2026, 8, 4, 9, 0))
        );
        MeetingRoomJpaEntity otherCompany = springDataMeetingRoomRepository.save(
                meetingRoom(20L, "다른 회사 회의실", null)
        );

        /* 세 식별자를 모두 전달하되 회사 10의 회의실 표시 정보를 조회한다. */
        List<MeetingRoomQueryPort.MeetingRoomSnapshot> result = meetingRoomQueryPort.findMeetingRooms(
                10L,
                List.of(active.getId(), inactive.getId(), otherCompany.getId())
        );

        /* 활성 여부와 관계없이 같은 회사 두 회의실만 식별자 순서로 반환돼야 한다. */
        assertThat(result)
                .extracting(MeetingRoomQueryPort.MeetingRoomSnapshot::meetingRoomId)
                .containsExactly(active.getId(), inactive.getId());
        assertThat(result)
                .extracting(MeetingRoomQueryPort.MeetingRoomSnapshot::name)
                .containsExactly("활성 회의실", "비활성 회의실");
    }

    /*
     * 테스트 조건에 맞는 회의실 영속성 엔티티를 생성한다.
     *
     * @param companyId 소속 회사 식별자
     * @param name 회의실 이름
     * @param deletedAt 비활성화 시각
     * @return 테스트 DB에 저장할 회의실 엔티티
     */
    private MeetingRoomJpaEntity meetingRoom(Long companyId, String name, LocalDateTime deletedAt) {
        /* ROOM-01에 필요한 나머지 필드는 동일한 정상값으로 채운다. */
        return new MeetingRoomJpaEntity(
                null,
                companyId,
                name,
                "박애관 421호",
                12,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                deletedAt
        );
    }
}
