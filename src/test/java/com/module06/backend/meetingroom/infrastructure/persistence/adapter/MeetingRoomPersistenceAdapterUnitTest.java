package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingRoomJpaEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.repository.SpringDataMeetingRoomRepository;

/*
 * ROOM-03 저장 시 데이터베이스 무결성 오류를 외부 비즈니스 계약으로 변환하는지 검증한다.
 */
@DisplayName("ROOM-03 회의실 저장 예외 변환")
class MeetingRoomPersistenceAdapterUnitTest {

    /* 활성 이름 유일성 제약 위반이 MR-002로 반환되는지 검증한다. */
    @Test
    @DisplayName("활성 회의실 이름 제약 위반을 MR-002로 변환한다")
    void translatesActiveNameConstraintViolation() {
        /* 활성 이름 유일성 제약 위반을 발생시키는 Spring Data 저장소 대역을 준비한다. */
        SpringDataMeetingRoomRepository repository = mock(SpringDataMeetingRoomRepository.class);
        when(repository.saveAndFlush(any(MeetingRoomJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'UK_MEETING_ROOM_ACTIVE_NAME'"
                ));
        MeetingRoomPersistenceAdapter adapter = new MeetingRoomPersistenceAdapter(repository);

        /* 동시 중복 저장을 시도하면 데이터베이스 예외 대신 공개 오류 코드가 반환돼야 한다. */
        assertThatThrownBy(() -> adapter.save(newMeetingRoom()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("MR-002");
    }

    /* 이름 중복과 무관한 무결성 오류를 MR-002로 오인하지 않는지 검증한다. */
    @Test
    @DisplayName("다른 데이터 무결성 오류는 원래 예외로 유지한다")
    void preservesUnrelatedIntegrityViolation() {
        /* 외래 키 위반을 흉내 내는 원본 예외를 준비한다. */
        DataIntegrityViolationException original = new DataIntegrityViolationException(
                "Foreign key constraint violation"
        );
        SpringDataMeetingRoomRepository repository = mock(SpringDataMeetingRoomRepository.class);
        when(repository.saveAndFlush(any(MeetingRoomJpaEntity.class))).thenThrow(original);
        MeetingRoomPersistenceAdapter adapter = new MeetingRoomPersistenceAdapter(repository);

        /* 대상 제약 이름이 없는 오류는 원인을 숨기지 않고 동일한 예외로 다시 던져야 한다. */
        assertThatThrownBy(() -> adapter.save(newMeetingRoom())).isSameAs(original);
    }

    /* 저장 예외 테스트에서 사용하는 정상 신규 회의실 도메인을 만든다. */
    private MeetingRoom newMeetingRoom() {
        /* 이름 중복 이외의 속성은 모두 정상값으로 채운다. */
        return MeetingRoom.create(
                10L,
                "대회의실",
                "박애관 421호"
        );
    }
}
