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
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
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

    /* ROOM-03에서 활성 이름 중복 확인과 신규 저장에 사용하는 명령 저장소 계약이다. */
    @Autowired
    private MeetingRoomCommandRepository meetingRoomCommandRepository;

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

    /* ROOM-03 신규 도메인이 저장되고 생성 식별자가 반환되는지 검증한다. */
    @Test
    @DisplayName("신규 회의실을 저장하고 생성 식별자를 반환한다")
    void savesNewMeetingRoomWithGeneratedId() {
        /* 식별자가 없는 활성 회의실 도메인을 생성한다. */
        MeetingRoom meetingRoom = MeetingRoom.create(
                10L,
                "대회의실",
                "박애관 421호",
                12,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );

        /* 명령 저장소를 통해 실제 테스트 DB에 신규 회의실을 저장한다. */
        MeetingRoom saved = meetingRoomCommandRepository.save(meetingRoom);

        /* IDENTITY 식별자와 전체 등록 속성이 저장 결과에 반영돼야 한다. */
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCompanyId()).isEqualTo(10L);
        assertThat(saved.getName()).isEqualTo("대회의실");
        assertThat(saved.getAvailableFrom()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.isActive()).isTrue();
    }

    /* 회사·이름·활성 조건을 모두 적용해 중복을 판단하는지 검증한다. */
    @Test
    @DisplayName("같은 회사의 활성 회의실 이름만 중복으로 판단한다")
    void checksDuplicateNameOnlyAmongActiveRoomsInSameCompany() {
        /* 기준이 될 같은 회사의 활성 회의실을 저장한다. */
        springDataMeetingRoomRepository.save(meetingRoom(10L, "대회의실", null));

        /* 같은 회사의 같은 활성 이름은 중복이어야 한다. */
        assertThat(meetingRoomCommandRepository.existsActiveByCompanyIdAndName(10L, "대회의실"))
                .isTrue();

        /* 다른 회사이거나 존재하지 않는 이름은 중복으로 노출되면 안 된다. */
        assertThat(meetingRoomCommandRepository.existsActiveByCompanyIdAndName(20L, "대회의실"))
                .isFalse();
        assertThat(meetingRoomCommandRepository.existsActiveByCompanyIdAndName(10L, "소회의실"))
                .isFalse();

        /* 활성 행을 지우고 같은 이름의 비활성 행만 남기면 이름을 재사용할 수 있어야 한다. */
        springDataMeetingRoomRepository.deleteAll();
        springDataMeetingRoomRepository.save(
                meetingRoom(10L, "대회의실", LocalDateTime.of(2026, 8, 5, 9, 0))
        );
        assertThat(meetingRoomCommandRepository.existsActiveByCompanyIdAndName(10L, "대회의실"))
                .isFalse();
    }

    /* ROOM-04 이름 중복 조회가 현재 수정 대상 ID를 제외하는지 검증한다. */
    @Test
    @DisplayName("현재 회의실을 제외한 다른 활성 이름만 중복으로 판단한다")
    void checksDuplicateNameExcludingCurrentMeetingRoom() {
        /* 같은 회사에 이름이 다른 활성 회의실 두 개를 저장한다. */
        MeetingRoomJpaEntity current = springDataMeetingRoomRepository.save(meetingRoom(10L, "회의실 B", null));
        MeetingRoomJpaEntity other = springDataMeetingRoomRepository.save(meetingRoom(10L, "대회의실", null));

        /* 자기 이름은 자기 ID를 제외하면 중복이 아니어야 한다. */
        assertThat(meetingRoomCommandRepository.existsActiveByCompanyIdAndNameExcludingId(
                10L,
                "회의실 B",
                current.getId()
        )).isFalse();

        /* 다른 활성 회의실 이름은 현재 ID를 제외해도 중복이어야 한다. */
        assertThat(meetingRoomCommandRepository.existsActiveByCompanyIdAndNameExcludingId(
                10L,
                "대회의실",
                current.getId()
        )).isTrue();

        /* 이름을 가진 회의실 본인을 제외하면 다시 중복이 아니어야 한다. */
        assertThat(meetingRoomCommandRepository.existsActiveByCompanyIdAndNameExcludingId(
                10L,
                "대회의실",
                other.getId()
        )).isFalse();
    }

    /* ROOM-04 수정용 잠금 조회가 회사와 활성 조건을 적용하는지 검증한다. */
    @Test
    @DisplayName("수정용 잠금 조회는 같은 회사의 활성 회의실만 반환한다")
    void findsActiveMeetingRoomForUpdateWithinCompany() {
        /* 활성 회의실과 비활성 회의실을 각각 저장한다. */
        MeetingRoomJpaEntity active = springDataMeetingRoomRepository.save(meetingRoom(10L, "회의실 B", null));
        MeetingRoomJpaEntity inactive = springDataMeetingRoomRepository.save(
                meetingRoom(10L, "폐쇄 회의실", LocalDateTime.of(2026, 8, 6, 9, 0))
        );

        /* 같은 회사의 활성 회의실만 잠금 조회 결과로 반환돼야 한다. */
        assertThat(meetingRoomCommandRepository.findActiveByIdForUpdate(10L, active.getId()))
                .isPresent();
        assertThat(meetingRoomCommandRepository.findActiveByIdForUpdate(20L, active.getId()))
                .isEmpty();
        assertThat(meetingRoomCommandRepository.findActiveByIdForUpdate(10L, inactive.getId()))
                .isEmpty();
    }

    /* ROOM-05 소프트 삭제 상태가 기존 행에 반영되고 활성 조회에서 제외되는지 검증한다. */
    @Test
    @DisplayName("회의실 비활성화 시 deletedAt을 저장하고 활성 조회에서 제외한다")
    void savesDeactivatedMeetingRoomAsSoftDeleted() {
        /* 활성 회의실을 저장하고 운영 저장 경로에서 사용하는 도메인 객체로 잠금 조회한다. */
        MeetingRoomJpaEntity entity = springDataMeetingRoomRepository.save(meetingRoom(10L, "회의실 B", null));
        MeetingRoom activeRoom = meetingRoomCommandRepository
                .findActiveByIdForUpdate(10L, entity.getId())
                .orElseThrow();

        /* 고정된 시각으로 소프트 삭제한 도메인 상태를 명령 저장소에 저장한다. */
        LocalDateTime deactivatedAt = LocalDateTime.of(2026, 8, 6, 9, 0);
        MeetingRoom saved = meetingRoomCommandRepository.save(activeRoom.deactivate(deactivatedAt));

        /* 저장 결과와 실제 DB 행에 deletedAt이 기록되고 활성 조회에서는 제외돼야 한다. */
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getDeletedAt()).isEqualTo(deactivatedAt);
        MeetingRoomJpaEntity persisted = springDataMeetingRoomRepository.findById(entity.getId()).orElseThrow();
        assertThat(persisted.getDeletedAt()).isEqualTo(deactivatedAt);
        assertThat(meetingRoomRepository.findActiveById(10L, entity.getId())).isEmpty();
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
