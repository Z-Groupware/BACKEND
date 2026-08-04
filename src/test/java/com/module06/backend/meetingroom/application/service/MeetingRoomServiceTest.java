package com.module06.backend.meetingroom.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.meetingroom.application.result.MeetingRoomSummary;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;

/*
 * MeetingRoomService의 ROOM-01 애플리케이션 로직을 검증하는 단위 테스트다.
 *
 * Spring과 JPA를 실행하지 않고 도메인 저장소 대역을 사용해
 * 회사 식별자 전달, 결과 변환, 빈 목록 반환 계약만 빠르게 검증한다.
 */
@DisplayName("ROOM-01 회의실 목록 조회 서비스")
class MeetingRoomServiceTest {

    /*
     * 저장소에서 조회한 회의실 도메인 객체가 API 전달용 Summary로 변환되는지 검증한다.
     */
    @Test
    @DisplayName("활성 회의실을 MeetingRoomSummary 목록으로 변환한다")
    void convertsMeetingRoomsToSummaries() {
        /* 테스트에서 조회할 회사 식별자와 회의실 도메인 객체를 준비한다. */
        Long companyId = 10L;
        MeetingRoom meetingRoom = new MeetingRoom(
                1L,
                companyId,
                "대회의실",
                "박애관 421호",
                12,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null
        );

        /* 준비한 회의실을 반환하는 저장소 대역으로 조회 서비스를 생성한다. */
        RecordingMeetingRoomRepository repository = new RecordingMeetingRoomRepository(List.of(meetingRoom));
        MeetingRoomService service = new MeetingRoomService(repository);

        /* ROOM-01 유스케이스를 실행한다. */
        List<MeetingRoomSummary> result = service.getMeetingRooms(companyId);

        /* 회사 식별자가 저장소에 그대로 전달되고 모든 응답 필드가 올바르게 변환됐는지 확인한다. */
        assertThat(repository.requestedCompanyId()).isEqualTo(companyId);
        assertThat(result).containsExactly(
                new MeetingRoomSummary(
                        1L,
                        "대회의실",
                        "박애관 421호",
                        12,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )
        );
    }

    /*
     * 회사에 활성 회의실이 없을 때 예외나 null 대신 빈 목록을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("활성 회의실이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoMeetingRoomExists() {
        /* 빈 조회 결과를 반환하는 저장소 대역으로 서비스를 생성한다. */
        RecordingMeetingRoomRepository repository = new RecordingMeetingRoomRepository(List.of());
        MeetingRoomService service = new MeetingRoomService(repository);

        /* 회의실이 없는 회사의 ROOM-01 유스케이스를 실행한다. */
        List<MeetingRoomSummary> result = service.getMeetingRooms(10L);

        /* API가 200 OK와 빈 배열을 만들 수 있도록 빈 목록이 반환되는지 확인한다. */
        assertThat(result).isEmpty();
    }

    /*
     * 단위 테스트에서 조회 결과와 전달된 회사 식별자를 기록하는 저장소 대역이다.
     */
    private static final class RecordingMeetingRoomRepository implements MeetingRoomRepository {

        /* 서비스에 반환할 미리 준비된 회의실 목록이다. */
        private final List<MeetingRoom> meetingRooms;

        /* 서비스가 저장소에 전달한 회사 식별자를 기록한다. */
        private Long requestedCompanyId;

        /*
         * 저장소 대역이 반환할 회의실 목록을 설정한다.
         *
         * @param meetingRooms 서비스에 반환할 회의실 목록
         */
        private RecordingMeetingRoomRepository(List<MeetingRoom> meetingRooms) {
            /* 테스트 중 목록이 바뀌지 않도록 불변 복사본을 저장한다. */
            this.meetingRooms = List.copyOf(meetingRooms);
        }

        /*
         * 전달된 회사 식별자를 기록하고 준비된 회의실 목록을 반환한다.
         *
         * @param companyId 서비스가 전달한 회사 식별자
         * @return 테스트에서 준비한 회의실 목록
         */
        @Override
        public List<MeetingRoom> findAllActiveByCompanyId(Long companyId) {
            /* 회사 범위 전달 여부를 검증할 수 있도록 요청값을 저장한다. */
            this.requestedCompanyId = companyId;

            /* 실제 DB 조회 대신 테스트에서 준비한 목록을 반환한다. */
            return meetingRooms;
        }

        /*
         * 저장소에 마지막으로 전달된 회사 식별자를 반환한다.
         *
         * @return 기록된 회사 식별자
         */
        private Long requestedCompanyId() {
            /* 테스트 검증을 위해 기록된 요청값을 노출한다. */
            return requestedCompanyId;
        }
    }
}
