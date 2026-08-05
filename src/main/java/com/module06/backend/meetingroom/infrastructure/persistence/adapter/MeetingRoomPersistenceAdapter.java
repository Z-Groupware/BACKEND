package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingRoomJpaEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.repository.SpringDataMeetingRoomRepository;

/*
 * MeetingRoomRepository 도메인 계약을 JPA로 구현하는 아웃바운드 어댑터다.
 *
 * Spring Data 저장소 호출과 영속성 엔티티를 순수 도메인 모델로 변환하는 책임을 가진다.
 * application 계층은 이 구현체를 직접 참조하지 않고 MeetingRoomRepository에만 의존한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingRoomPersistenceAdapter implements MeetingRoomRepository {

    /* 실제 meeting_room 조회 쿼리를 실행하는 기술 저장소다. */
    private final SpringDataMeetingRoomRepository springDataMeetingRoomRepository;

    /*
     * 특정 회사에 속한 활성 회의실을 정렬된 도메인 목록으로 반환한다.
     *
     * @param companyId 조회할 회사 식별자
     * @return 이름과 식별자로 정렬된 활성 회의실 도메인 목록
     */
    @Override
    public List<MeetingRoom> findAllActiveByCompanyId(Long companyId) {
        /* JPA 조회 결과가 상위 계층으로 노출되지 않도록 각 엔티티를 도메인 모델로 변환한다. */
        return springDataMeetingRoomRepository
                .findAllByCompanyIdAndDeletedAtIsNullOrderByNameAscIdAsc(companyId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    /*
     * 특정 회사에 속한 활성 회의실 하나를 도메인 모델로 반환한다.
     *
     * @param companyId 조회할 회사 식별자
     * @param meetingRoomId 조회할 회의실 식별자
     * @return 조건을 만족하는 활성 회의실 도메인 모델, 없으면 빈 Optional
     */
    @Override
    public Optional<MeetingRoom> findActiveById(Long companyId, Long meetingRoomId) {
        /* 회사와 활성 조건을 만족하는 회의실만 도메인 모델로 변환해 상위 계층에 전달한다. */
        return springDataMeetingRoomRepository
                .findByIdAndCompanyIdAndDeletedAtIsNull(meetingRoomId, companyId)
                .map(this::toDomain);
    }

    /*
     * 영속성 엔티티를 프레임워크 의존성이 없는 회의실 도메인 모델로 변환한다.
     *
     * @param entity 변환할 JPA 영속성 엔티티
     * @return 변환된 회의실 도메인 모델
     */
    private MeetingRoom toDomain(MeetingRoomJpaEntity entity) {
        /* ROOM-01과 회의실 활성 상태 판단에 필요한 모든 필드를 도메인 모델로 전달한다. */
        return new MeetingRoom(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getLocation(),
                entity.getCapacity(),
                entity.getAvailableFrom(),
                entity.getAvailableTo(),
                entity.getDeletedAt()
        );
    }
}
