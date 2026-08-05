package com.module06.backend.meeting.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.MeetingLockRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingTopicJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingTopicRepository;

/*
 * RESULT-01과 E 인수인계 연동의 회의 읽기 계약을 JPA로 구현하는 어댑터다.
 *
 * 모든 회의 조회에 companyId 조건을 포함하고, 참석자는 파생 쿼리의 IN 조회로 일괄 로딩해
 * 타 회사 데이터 노출과 회의별 반복 조회를 방지한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingQueryPersistenceAdapter implements MeetingQueryRepository, MeetingLockRepository {

    /* E 배치 계약에서 한 번의 IN 조건에 허용하는 최대 회의 식별자 개수다. */
    private static final int MEETING_ID_BATCH_SIZE = 200;

    /* meeting 테이블에서 회사 범위 회의 행을 조회하는 기술 저장소다. */
    private final SpringDataMeetingRepository springDataMeetingRepository;

    /* meeting_attendee 테이블에서 단건·배치 참석자 행을 조회하는 기술 저장소다. */
    private final SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* meeting_topic 테이블에서 회의별 대주제와 소주제를 배치 조회하는 기술 저장소다. */
    private final SpringDataMeetingTopicRepository springDataMeetingTopicRepository;

    /* 회사와 식별자가 일치하는 회의 한 건과 전체 참석자 식별자를 조회한다. */
    @Override
    public Optional<MeetingSnapshot> findMeeting(Long companyId, Long meetingId) {
        /* 회의가 없거나 타 회사 소속이면 빈 결과를 그대로 반환한다. */
        return springDataMeetingRepository.findByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> toMeetingSnapshot(
                        meeting,
                        findAttendeeMemberIds(meeting.getId())
                ));
    }

    /* 대상 회의 행을 비관적으로 잠근 뒤 잠금 획득 시점의 참석자 명단을 조회한다. */
    @Override
    public Optional<MeetingSnapshot> findMeetingForUpdate(Long companyId, Long meetingId) {
        /* 같은 회의의 다른 명단 교체 트랜잭션이 끝날 때까지 기다린 뒤 최신 회의 행을 읽는다. */
        return springDataMeetingRepository.findLockedByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> toMeetingSnapshot(
                        meeting,
                        findAttendeeMemberIds(meeting.getId())
                ));
    }

    /* 프로젝트에 연결된 회사 범위 회의를 시간순으로 조회한다. */
    @Override
    public List<ProjectMeetingSnapshot> findProjectMeetingsOrdered(Long companyId, Long projectId) {
        /* 파생 쿼리가 startAt과 id의 안정적인 오름차순 정렬을 데이터베이스에서 적용한다. */
        return springDataMeetingRepository
                .findAllByCompanyIdAndProjectIdOrderByStartAtAscIdAsc(companyId, projectId)
                .stream()
                .map(this::toProjectMeetingSnapshot)
                .toList();
    }

    /* 인증 사용자가 참석자로 등록된 예정·진행 중 회의를 현재 이후 기준으로 제한 조회한다. */
    @Override
    public List<UpcomingMeetingSnapshot> findUpcomingMeetings(
            Long companyId,
            Long memberId,
            java.time.LocalDateTime now,
            int limit
    ) {
        /* 참석자 조인 테이블에서 먼저 사용자가 실제로 포함된 회의 식별자만 조회한다. */
        List<Long> attendeeMeetingIds = springDataMeetingAttendeeRepository
                .findAllByMemberIdOrderByMeetingIdAsc(memberId)
                .stream()
                .map(MeetingAttendeeJpaEntity::getMeetingId)
                .distinct()
                .toList();

        /* 참석자로 등록된 회의가 없으면 후속 meeting 조회 없이 빈 목록을 반환한다. */
        if (attendeeMeetingIds.isEmpty()) {
            return List.of();
        }

        /* 긴 IN 조건을 피하기 위해 참석 회의 식별자를 200개씩 나눠 활성 후보를 조회한다. */
        List<MeetingJpaEntity> candidates = new ArrayList<>();
        List<MeetingStatus> upcomingStatuses = List.of(
                MeetingStatus.SCHEDULED,
                MeetingStatus.IN_PROGRESS
        );
        for (int fromIndex = 0; fromIndex < attendeeMeetingIds.size(); fromIndex += MEETING_ID_BATCH_SIZE) {
            /* 현재 배치의 끝 위치가 전체 참석 회의 목록을 넘지 않도록 제한한다. */
            int toIndex = Math.min(fromIndex + MEETING_ID_BATCH_SIZE, attendeeMeetingIds.size());
            List<Long> batchMeetingIds = attendeeMeetingIds.subList(fromIndex, toIndex);

            /* 회사·상태·종료 시각 조건을 데이터베이스에 적용한 현재 배치 후보를 수집한다. */
            candidates.addAll(springDataMeetingRepository
                    .findAllByIdInAndCompanyIdAndStatusInAndEndAtGreaterThanEqualOrderByStartAtAscIdAsc(
                            batchMeetingIds,
                            companyId,
                            upcomingStatuses,
                            now
                    ));
        }

        /* 배치 경계를 넘어도 전체가 시작 시각과 식별자 순서를 유지하도록 정렬하고 limit을 적용한다. */
        List<MeetingJpaEntity> selectedMeetings = candidates.stream()
                .sorted(Comparator
                        .comparing(MeetingJpaEntity::getStartAt)
                        .thenComparing(MeetingJpaEntity::getId))
                .limit(limit)
                .toList();

        /* 필터 결과가 없으면 참석자 수 조회 없이 빈 목록을 반환한다. */
        if (selectedMeetings.isEmpty()) {
            return List.of();
        }

        /* 최대 20개 선택 회의의 참석자를 한 번에 조회해 회의별 인원수를 계산한다. */
        List<Long> selectedMeetingIds = selectedMeetings.stream()
                .map(MeetingJpaEntity::getId)
                .toList();
        Map<Long, Long> attendeeCounts = springDataMeetingAttendeeRepository
                .findAllByMeetingIdInOrderByMeetingIdAscMemberIdAsc(selectedMeetingIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MeetingAttendeeJpaEntity::getMeetingId,
                        Collectors.counting()
                ));

        /* 회의 메타와 배치 계산한 참석자 수를 MEET-03 읽기 모델로 변환한다. */
        return selectedMeetings.stream()
                .map(meeting -> toUpcomingMeetingSnapshot(
                        meeting,
                        attendeeCounts.getOrDefault(meeting.getId(), 0L).intValue()
                ))
                .toList();
    }

    /* 회사 범위에 속하는 여러 회의의 대주제와 소주제를 한 번에 조회한다. */
    @Override
    public List<MeetingTopicSnapshot> findMeetingTopics(Long companyId, List<Long> meetingIds) {
        /* 공통 배치 조회가 안건 엔티티를 회의별 표시 순서로 읽도록 함수를 전달한다. */
        return collectByScopedMeetingBatch(
                companyId,
                meetingIds,
                scopedMeetingIds -> springDataMeetingTopicRepository
                        .findAllByMeetingIdInOrderByMeetingIdAscSortOrderAscIdAsc(scopedMeetingIds)
                        .stream()
                        .map(this::toMeetingTopicSnapshot)
                        .toList()
        );
    }

    /* 회사 범위에 속하는 여러 회의의 참석자 식별자 쌍을 한 번에 조회한다. */
    @Override
    public List<MeetingAttendeeReference> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
        /* 공통 배치 조회가 참석자 엔티티를 회의와 구성원 식별자 순서로 읽도록 함수를 전달한다. */
        return collectByScopedMeetingBatch(
                companyId,
                meetingIds,
                scopedMeetingIds -> springDataMeetingAttendeeRepository
                        .findAllByMeetingIdInOrderByMeetingIdAscMemberIdAsc(scopedMeetingIds)
                        .stream()
                        .map(attendee -> new MeetingAttendeeReference(
                                attendee.getMeetingId(),
                                attendee.getMemberId()
                        ))
                        .toList()
        );
    }

    /* 회의 식별자를 회사 범위에서 검증하고 200개씩 나눠 읽기 모델을 수집한다. */
    private <T> List<T> collectByScopedMeetingBatch(
            Long companyId,
            List<Long> meetingIds,
            Function<List<Long>, List<T>> batchReader
    ) {
        /* 입력이 없으면 불필요한 IN 조회를 만들지 않고 즉시 빈 목록을 반환한다. */
        if (meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }

        /* null·음수 식별자를 제외하고 중복 제거와 정렬을 적용해 배치 간 결과 순서도 안정화한다. */
        List<Long> distinctMeetingIds = meetingIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(meetingId -> meetingId > 0L)
                .distinct()
                .sorted()
                .toList();

        /* 유효한 회의 식별자가 하나도 없으면 데이터베이스를 조회하지 않는다. */
        if (distinctMeetingIds.isEmpty()) {
            return List.of();
        }

        /* IN 조건 상한 200개를 지키기 위해 요청 식별자를 분할하고 결과를 합친다. */
        List<T> results = new ArrayList<>();
        for (int fromIndex = 0; fromIndex < distinctMeetingIds.size(); fromIndex += MEETING_ID_BATCH_SIZE) {
            /* 현재 배치의 끝 위치가 전체 목록을 넘지 않도록 제한한다. */
            int toIndex = Math.min(fromIndex + MEETING_ID_BATCH_SIZE, distinctMeetingIds.size());
            List<Long> batchMeetingIds = distinctMeetingIds.subList(fromIndex, toIndex);

            /* 배치 안에서 실제 요청 회사에 속한 회의 식별자만 조회 대상으로 선별한다. */
            Set<Long> scopedMeetingIds = springDataMeetingRepository
                    .findAllByIdInAndCompanyId(batchMeetingIds, companyId)
                    .stream()
                    .map(MeetingJpaEntity::getId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

            /* 해당 배치에 회사 범위 회의가 없으면 다음 배치로 넘어간다. */
            if (scopedMeetingIds.isEmpty()) {
                continue;
            }

            /* 호출자가 지정한 파생 쿼리 결과를 현재 배치의 전체 결과에 추가한다. */
            results.addAll(batchReader.apply(List.copyOf(scopedMeetingIds)));
        }

        /* 모든 배치의 결과를 외부에서 변경할 수 없는 목록으로 반환한다. */
        return List.copyOf(results);
    }

    /* 단일 회의에 저장된 참석자 식별자를 구성원 식별자 순서로 조회한다. */
    private List<Long> findAttendeeMemberIds(Long meetingId) {
        /* 저장소 엔티티를 애플리케이션에 노출하지 않고 식별자 목록으로 축소한다. */
        return springDataMeetingAttendeeRepository
                .findAllByMeetingIdOrderByMemberIdAsc(meetingId)
                .stream()
                .map(MeetingAttendeeJpaEntity::getMemberId)
                .toList();
    }

    /* 회의 엔티티와 참석자 식별자를 RESULT-01 및 E 단건 조회용 모델로 변환한다. */
    private MeetingSnapshot toMeetingSnapshot(MeetingJpaEntity meeting, List<Long> attendeeMemberIds) {
        /* 회의 테이블의 실제 필드와 별도 조회한 참석자 식별자를 손실 없이 전달한다. */
        return new MeetingSnapshot(
                meeting.getId(),
                meeting.getCompanyId(),
                meeting.getProjectId(),
                meeting.getHostMemberId(),
                meeting.getTitle(),
                meeting.getStatus(),
                meeting.getStartAt(),
                meeting.getEndAt(),
                meeting.getStartedAt(),
                meeting.getEndedAt(),
                attendeeMemberIds
        );
    }

    /* 회의 엔티티를 E 프로젝트 타임라인용 최소 모델로 변환한다. */
    private ProjectMeetingSnapshot toProjectMeetingSnapshot(MeetingJpaEntity meeting) {
        /* 타임라인에 필요한 식별자, 제목, 시각, 개설자, 상태만 반환한다. */
        return new ProjectMeetingSnapshot(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getStartAt(),
                meeting.getHostMemberId(),
                meeting.getStatus()
        );
    }

    /* 회의 엔티티와 집계된 참석자 수를 MEET-03 예정 회의 읽기 모델로 변환한다. */
    private UpcomingMeetingSnapshot toUpcomingMeetingSnapshot(MeetingJpaEntity meeting, int attendeeCount) {
        /* 카드 조립에 필요한 식별자, 시간, 상태, 참석자 수만 저장소 경계 밖으로 전달한다. */
        return new UpcomingMeetingSnapshot(
                meeting.getId(),
                meeting.getProjectId(),
                meeting.getMeetingRoomId(),
                meeting.getHostMemberId(),
                meeting.getTitle(),
                meeting.getStatus(),
                meeting.getStartAt(),
                meeting.getEndAt(),
                attendeeCount
        );
    }

    /* 회의 안건 엔티티를 E 배치 계약에 필요한 읽기 모델로 변환한다. */
    private MeetingTopicSnapshot toMeetingTopicSnapshot(MeetingTopicJpaEntity topic) {
        /* 회의 식별자, MAIN·SUB 유형, 내용, 표시 순서만 경계 밖으로 전달한다. */
        return new MeetingTopicSnapshot(
                topic.getMeetingId(),
                topic.getTopicType(),
                topic.getContent(),
                topic.getSortOrder()
        );
    }
}
