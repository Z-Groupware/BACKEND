package com.module06.backend.meetingroom.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meetingroom.domain.model.MeetingRoom;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomCommandRepository;
import com.module06.backend.meetingroom.domain.repository.MeetingRoomRepository;
import com.module06.backend.meetingroom.infrastructure.persistence.entity.MeetingRoomJpaEntity;
import com.module06.backend.meetingroom.infrastructure.persistence.repository.SpringDataMeetingRoomRepository;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;

/*
 * MeetingRoomRepository 도메인 계약을 JPA로 구현하는 아웃바운드 어댑터다.
 *
 * Spring Data 저장소 호출과 영속성 엔티티를 순수 도메인 모델로 변환하는 책임을 가진다.
 * application 계층은 이 구현체를 직접 참조하지 않고 MeetingRoomRepository에만 의존한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingRoomPersistenceAdapter implements MeetingRoomRepository, MeetingRoomCommandRepository {

    /* 활성 회의실 이름의 동시 저장을 최종 차단하는 데이터베이스 제약 이름이다. */
    private static final String ACTIVE_NAME_UNIQUE_CONSTRAINT = "UK_MEETING_ROOM_ACTIVE_NAME";

    /* 실제 meeting_room 조회 쿼리를 실행하는 기술 저장소다. */
    private final SpringDataMeetingRoomRepository springDataMeetingRoomRepository;

    /* 회사 안에서 동일한 이름의 활성 회의실이 존재하는지 데이터베이스에서 확인한다. */
    @Override
    public boolean existsActiveByCompanyIdAndName(Long companyId, String name) {
        /* 회사·이름·활성 조건을 모두 파생 쿼리에 포함해 다른 회사와 비활성 행을 제외한다. */
        return springDataMeetingRoomRepository.existsByCompanyIdAndNameAndDeletedAtIsNull(companyId, name);
    }

    /* 회사의 활성 회의실을 수정·예약 트랜잭션 동안 쓰기 잠금으로 조회한다. */
    @Override
    public Optional<MeetingRoom> findActiveByIdForUpdate(Long companyId, Long meetingRoomId) {
        /* 회사·활성 조건을 쿼리에 포함하고 잠긴 영속성 결과를 도메인 객체로 변환한다. */
        return springDataMeetingRoomRepository
                .findForUpdateByIdAndCompanyIdAndDeletedAtIsNull(meetingRoomId, companyId)
                .map(this::toDomain);
    }

    /* 현재 수정 대상 이외에 동일한 활성 이름이 존재하는지 확인한다. */
    @Override
    public boolean existsActiveByCompanyIdAndNameExcludingId(
            Long companyId,
            String name,
            Long excludedMeetingRoomId
    ) {
        /* 회사·활성·이름 조건에 ID 제외 조건까지 포함한 파생 쿼리를 실행한다. */
        return springDataMeetingRoomRepository
                .existsByCompanyIdAndNameAndDeletedAtIsNullAndIdNot(companyId, name, excludedMeetingRoomId);
    }

    /* 신규 회의실을 저장하고 데이터베이스 생성 식별자가 반영된 도메인 객체를 반환한다. */
    @Override
    public MeetingRoom save(MeetingRoom meetingRoom) {
        /* 검증된 도메인을 영속성 엔티티로 변환하고 IDENTITY 식별자를 즉시 확보한다. */
        MeetingRoomJpaEntity saved;
        try {
            /* flush까지 실행해 유일성 제약 위반을 트랜잭션 종료 전 이 경계에서 확인한다. */
            saved = springDataMeetingRoomRepository.saveAndFlush(MeetingRoomJpaEntity.from(meetingRoom));
        } catch (DataIntegrityViolationException exception) {
            /* 활성 이름 유일성 위반만 공개 계약인 MR-002로 변환하고 다른 무결성 오류는 숨기지 않는다. */
            if (containsConstraintName(exception, ACTIVE_NAME_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NAME_DUPLICATE, exception);
            }
            throw exception;
        }

        /* 저장된 전체 컬럼을 프레임워크 의존성이 없는 회의실 도메인 객체로 복원한다. */
        return toDomain(saved);
    }

    /* 예외 원인 체인에 특정 데이터베이스 제약 이름이 포함돼 있는지 확인한다. */
    private boolean containsConstraintName(Throwable exception, String constraintName) {
        /* JDBC 드라이버와 Hibernate가 예외를 여러 단계로 감싸므로 최하위 원인까지 순회한다. */
        Throwable current = exception;
        while (current != null) {
            /* MySQL·H2가 제약 이름을 메시지에 포함하는 경우 대소문자 차이 없이 판별한다. */
            String message = current.getMessage();
            if (message != null && message.toUpperCase(java.util.Locale.ROOT)
                    .contains(constraintName.toUpperCase(java.util.Locale.ROOT))) {
                return true;
            }
            current = current.getCause();
        }

        /* 전체 원인 체인에 대상 제약이 없으면 다른 데이터 무결성 오류로 유지한다. */
        return false;
    }

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

    /* 예정 회의가 참조하는 회의실을 활성 여부와 무관하게 회사 범위에서 일괄 조회한다. */
    @Override
    public List<MeetingRoom> findAllByIds(Long companyId, List<Long> meetingRoomIds) {
        /* 중복 식별자를 제거해 파생 IN 쿼리의 크기를 줄이고 식별자 순서를 안정화한다. */
        List<Long> distinctIds = meetingRoomIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(meetingRoomId -> meetingRoomId > 0L)
                .distinct()
                .sorted()
                .toList();

        /* 유효한 식별자가 없으면 데이터베이스를 호출하지 않고 빈 목록을 반환한다. */
        if (distinctIds.isEmpty()) {
            return List.of();
        }

        /* 비활성화된 회의실도 과거·예정 회의 표시값으로 사용할 수 있도록 삭제 조건 없이 조회한다. */
        return springDataMeetingRoomRepository
                .findAllByCompanyIdAndIdInOrderByIdAsc(companyId, distinctIds)
                .stream()
                .map(this::toDomain)
                .toList();
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
                entity.getAvailableFrom(),
                entity.getAvailableTo(),
                entity.getDeletedAt()
        );
    }
}
