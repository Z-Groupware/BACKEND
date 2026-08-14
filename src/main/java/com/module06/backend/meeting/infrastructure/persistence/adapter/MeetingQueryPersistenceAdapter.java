package com.module06.backend.meeting.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository;
import com.module06.backend.meeting.domain.repository.MeetingDetailRepository;
import com.module06.backend.meeting.domain.repository.MeetingListRepository;
import com.module06.backend.meeting.domain.repository.MeetingLockRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.PendingActionMeetingRepository;
import com.module06.backend.meeting.domain.repository.StalledSummaryMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingTopicJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingTopicRepository;

/*
 * E 인수인계·C 프로젝트 연동의 회의 읽기 계약을 JPA로 구현하는 어댑터다.
 *
 * 모든 회의 조회에 companyId 조건을 포함하고, 참석자는 파생 쿼리의 IN 조회로 일괄 로딩해
 * 타 회사 데이터 노출과 회의별 반복 조회를 방지한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingQueryPersistenceAdapter
        implements MeetingQueryRepository, MeetingDetailRepository, MeetingLockRepository,
        MeetingListRepository, PendingActionMeetingRepository, StalledSummaryMeetingRepository,
        DashboardMeetingRepository {

    /* E 배치 계약에서 한 번의 IN 조건에 허용하는 최대 회의 식별자 개수다. */
    private static final int MEETING_ID_BATCH_SIZE = 200;

    /* 프로젝트 목록 집계에서 한 번의 IN 조건에 허용하는 최대 프로젝트 식별자 개수다. */
    private static final int PROJECT_ID_BATCH_SIZE = 200;

    /* meeting 테이블에서 회사 범위 회의 행을 조회하는 기술 저장소다. */
    private final SpringDataMeetingRepository springDataMeetingRepository;

    /* meeting_attendee 테이블에서 단건·배치 참석자 행을 조회하는 기술 저장소다. */
    private final SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* meeting_topic 테이블에서 회의별 대주제와 소주제를 배치 조회하는 기술 저장소다. */
    private final SpringDataMeetingTopicRepository springDataMeetingTopicRepository;

    /* 회사·권한·필터를 한 번의 페이징 쿼리에 적용하고 참석자 식별자를 페이지 단위로 일괄 조회한다. */
    @Override
    public MeetingPage findMeetings(MeetingListCriteria criteria) {
        /* 동적 조건을 JPA Specification으로 구성해 필터 조합마다 별도 @Query를 만들지 않는다. */
        Specification<MeetingJpaEntity> specification = buildMeetingListSpecification(criteria);

        /* 같은 시작 시각에도 결과가 흔들리지 않도록 회의 식별자를 두 번째 내림차순 키로 사용한다. */
        PageRequest pageRequest = PageRequest.of(
                criteria.page(),
                criteria.size(),
                Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"))
        );

        /* 데이터베이스에서 조건·정렬·페이지를 적용해 현재 페이지의 회의 엔티티만 조회한다. */
        Page<MeetingJpaEntity> meetingPage = springDataMeetingRepository.findAll(specification, pageRequest);

        /* 빈 페이지는 참석자 테이블을 추가 조회하지 않고 전체 건수 메타만 반환한다. */
        if (meetingPage.isEmpty()) {
            return new MeetingPage(List.of(), meetingPage.getTotalElements(), meetingPage.getTotalPages());
        }

        /* 현재 페이지 회의 식별자의 참석자를 한 번에 읽어 회의별 식별자 목록으로 묶는다. */
        List<Long> meetingIds = meetingPage.getContent().stream()
                .map(MeetingJpaEntity::getId)
                .toList();
        Map<Long, List<Long>> attendeeMemberIdsByMeeting = springDataMeetingAttendeeRepository
                .findAllByMeetingIdInOrderByMeetingIdAscMemberIdAsc(meetingIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MeetingAttendeeJpaEntity::getMeetingId,
                        LinkedHashMap::new,
                        Collectors.mapping(MeetingAttendeeJpaEntity::getMemberId, Collectors.toList())
                ));

        /* 엔티티 페이지 순서를 유지하며 애플리케이션에 필요한 최소 읽기 모델로 변환한다. */
        List<MeetingListSnapshot> meetings = meetingPage.getContent().stream()
                .map(meeting -> toMeetingListSnapshot(
                        meeting,
                        attendeeMemberIdsByMeeting.getOrDefault(meeting.getId(), List.of())
                ))
                .toList();

        /* 현재 페이지 회의와 Spring Page가 계산한 전체 결과 규모를 도메인 결과로 반환한다. */
        return new MeetingPage(meetings, meetingPage.getTotalElements(), meetingPage.getTotalPages());
    }

    /* MEET-02의 필수 회사 조건과 선택 필터 및 사용자 열람 범위를 Specification으로 만든다. */
    private Specification<MeetingJpaEntity> buildMeetingListSpecification(MeetingListCriteria criteria) {
        /* count 쿼리와 content 쿼리에 동일한 조건이 적용되도록 순수 조건 생성 람다를 반환한다. */
        return (meeting, criteriaQuery, criteriaBuilder) -> {
            /* 모든 목록 조회에 회사와 기간 조건을 필수로 적용해 테넌트와 기본 범위를 고정한다. */
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(meeting.get("companyId"), criteria.companyId()));
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    meeting.get("startAt"),
                    criteria.fromInclusive()
            ));
            predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    meeting.get("startAt"),
                    criteria.toInclusive()
            ));

            /* 프로젝트가 지정된 경우 해당 식별자와 일치하는 회의만 포함한다. */
            if (criteria.projectId() != null) {
                predicates.add(criteriaBuilder.equal(meeting.get("projectId"), criteria.projectId()));
            }

            /* 회의실이 지정된 경우 해당 식별자와 일치하는 회의만 포함한다. */
            if (criteria.meetingRoomId() != null) {
                predicates.add(criteriaBuilder.equal(meeting.get("meetingRoomId"), criteria.meetingRoomId()));
            }

            /* 상태가 지정된 경우 예약·진행·종료 중 요청 상태와 일치하는 회의만 포함한다. */
            if (criteria.status() != null) {
                predicates.add(criteriaBuilder.equal(meeting.get("status"), criteria.status()));
            }

            /* scope는 회의 화면의 개인 탭이라 역할 기반 열람 범위보다 우선한다 — OWNER·ADMIN도 예외 없다. */
            if (criteria.scope() != null) {
                switch (criteria.scope()) {
                    case HOSTED -> predicates.add(
                            criteriaBuilder.equal(meeting.get("hostMemberId"), criteria.requesterMemberId())
                    );
                    case ATTENDING -> {
                        /* host 본인은 뺀다 — 안 빼면 개설자 화면에 두 탭이 같은 회의를 중복 표시한다. */
                        predicates.add(criteriaBuilder.notEqual(
                                meeting.get("hostMemberId"),
                                criteria.requesterMemberId()
                        ));
                        predicates.add(criteriaBuilder.exists(
                                attendeeExistsSubquery(meeting, criteriaQuery, criteriaBuilder, criteria.requesterMemberId())
                        ));
                    }
                }
            } else if (!criteria.companyWideRead()) {
                /* 회사 전체 열람자가 아니면 자신이 개설했거나 참석자로 등록된 회의로 제한한다. */
                Subquery<Long> attendeeSubquery =
                        attendeeExistsSubquery(meeting, criteriaQuery, criteriaBuilder, criteria.requesterMemberId());

                /* 개설자 조건과 참석자 EXISTS 조건 중 하나라도 만족하면 목록에 포함한다. */
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(meeting.get("hostMemberId"), criteria.requesterMemberId()),
                        criteriaBuilder.exists(attendeeSubquery)
                ));
            }

            /* 누적된 모든 조건을 AND로 결합해 content와 count 조회에 동일하게 적용한다. */
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /* 지정한 구성원이 해당 회의의 참석자로 등록돼 있는지 확인하는 EXISTS 서브쿼리를 만든다. */
    private Subquery<Long> attendeeExistsSubquery(
            Root<MeetingJpaEntity> meeting,
            AbstractQuery<?> criteriaQuery,
            CriteriaBuilder criteriaBuilder,
            Long memberId
    ) {
        Subquery<Long> attendeeSubquery = criteriaQuery.subquery(Long.class);
        Root<MeetingAttendeeJpaEntity> attendee = attendeeSubquery.from(MeetingAttendeeJpaEntity.class);
        attendeeSubquery.select(attendee.get("meetingId"));
        attendeeSubquery.where(
                criteriaBuilder.equal(attendee.get("meetingId"), meeting.get("id")),
                criteriaBuilder.equal(attendee.get("memberId"), memberId)
        );
        return attendeeSubquery;
    }

    /* 회의 엔티티와 배치 조회한 참석자 식별자를 MEET-02 목록 읽기 모델로 변환한다. */
    private MeetingListSnapshot toMeetingListSnapshot(MeetingJpaEntity meeting, List<Long> attendeeMemberIds) {
        /* 외부 표시 정보 조합에 필요한 식별자와 회의 메타만 저장소 경계 밖으로 전달한다. */
        return new MeetingListSnapshot(
                meeting.getId(),
                meeting.getProjectId(),
                meeting.getTeamId(),
                meeting.getMeetingRoomId(),
                meeting.getTitle(),
                meeting.getStatus(),
                meeting.getStartAt(),
                meeting.getEndAt(),
                meeting.getHostMemberId(),
                attendeeMemberIds
        );
    }

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

    /* 회사와 식별자가 일치하는 MEET-04 상세 회의와 참석자 식별자를 조회한다. */
    @Override
    public Optional<MeetingDetailSnapshot> findMeetingDetail(Long companyId, Long meetingId) {
        /* companyId가 다른 회의는 빈 결과로 숨기고 같은 회사 회의만 상세 모델로 변환한다. */
        return springDataMeetingRepository.findByIdAndCompanyId(meetingId, companyId)
                .map(meeting -> toMeetingDetailSnapshot(
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

    /* 회사 범위에서 여러 프로젝트의 취소되지 않은 회의 수를 배치 단위로 집계한다. */
    @Override
    public Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds) {
        /* 요청 프로젝트가 없으면 파생 쿼리의 빈 IN 조건을 만들지 않고 빈 집계를 반환한다. */
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        /* 신규 @Query 없이 회사·프로젝트·상태 파생 쿼리 결과를 프로젝트 식별자로 집계한다. */
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (int fromIndex = 0; fromIndex < projectIds.size(); fromIndex += PROJECT_ID_BATCH_SIZE) {
            /* 현재 배치의 끝 위치가 전체 프로젝트 목록을 넘지 않도록 제한한다. */
            int toIndex = Math.min(fromIndex + PROJECT_ID_BATCH_SIZE, projectIds.size());
            List<Long> batchProjectIds = projectIds.subList(fromIndex, toIndex);

            /* CANCELED만 제외해 SCHEDULED·IN_PROGRESS·DONE 회의를 프로젝트별로 누적한다. */
            springDataMeetingRepository
                    .findAllByCompanyIdAndProjectIdInAndStatusNot(
                            companyId,
                            batchProjectIds,
                            MeetingStatus.CANCELED
                    )
                    .forEach(meeting -> counts.merge(meeting.getProjectId(), 1L, Long::sum));
        }

        /* 서비스가 요청 프로젝트의 0건 항목을 완성할 수 있도록 실제 존재하는 집계만 반환한다. */
        return Map.copyOf(counts);
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

    /* 회의 엔티티와 참석자 식별자를 E 단건 조회용 모델로 변환한다. */
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

    /* 회의 엔티티와 참석자 식별자를 MEET-04 상세 조회 모델로 변환한다. */
    private MeetingDetailSnapshot toMeetingDetailSnapshot(
            MeetingJpaEntity meeting,
            List<Long> attendeeMemberIds
    ) {
        /* 상세 화면과 권한 판정에 필요한 D 소유 필드를 손실 없이 저장소 경계 밖으로 전달한다. */
        return new MeetingDetailSnapshot(
                meeting.getId(),
                meeting.getCompanyId(),
                meeting.getProjectId(),
                meeting.getTeamId(),
                meeting.getMeetingRoomId(),
                meeting.getHostMemberId(),
                meeting.getTitle(),
                meeting.getStatus(),
                meeting.getStartAt(),
                meeting.getEndAt(),
                meeting.getStartedAt(),
                meeting.getEndedAt(),
                meeting.isRecordingConsent(),
                meeting.getCreatedAt(),
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
        /* 회의 식별자, 안건·부모 안건 식별자, MAIN·SUB 유형, 내용, 표시 순서를 경계 밖으로 전달한다. */
        return new MeetingTopicSnapshot(
                topic.getMeetingId(),
                topic.getId(),
                topic.getParentTopicId(),
                topic.getTopicType(),
                topic.getContent(),
                topic.getSortOrder()
        );
    }

    /* MEET-10 후보로 요청 회사에서 사용자가 host인 종료 회의를 최근 시작 순으로 조회한다. */
    @Override
    public List<PendingActionMeetingCandidate> findHostedDoneMeetings(Long companyId, Long hostMemberId) {
        /* 분배 대기 판정은 액션 도메인 몫이므로 여기서는 회사·host·종료 상태까지만 좁힌다. */
        return springDataMeetingRepository
                .findAllByCompanyIdAndHostMemberIdAndStatusOrderByStartAtDescIdDesc(
                        companyId,
                        hostMemberId,
                        MeetingStatus.DONE
                )
                .stream()
                .map(meeting -> new PendingActionMeetingCandidate(
                        meeting.getId(),
                        meeting.getProjectId(),
                        meeting.getTitle(),
                        meeting.getStartAt()
                ))
                .toList();
    }

    /* MEET-15 후보로 요청 회사에서 사용자가 host인 종료 회의를 최근 시작 순으로 조회한다. */
    @Override
    public List<StalledSummaryMeetingCandidate> findHostedDoneSummaryCandidates(
            Long companyId,
            Long hostMemberId
    ) {
        /*
         * 요약 상태 판정은 A 도메인 몫이므로 여기서는 회사·host·종료 상태까지만 좁힌다.
         * MEET-18 온라인 회의(startAt 없음)는 이 화면의 시작일 필터·정렬과 맞지 않아 제외한다 —
         * isOnline=false로 걸러 startAt null 비교 자체가 발생하지 않게 한다.
         */
        return springDataMeetingRepository
                .findAllByCompanyIdAndHostMemberIdAndStatusAndIsOnlineFalseOrderByStartAtDescIdDesc(
                        companyId,
                        hostMemberId,
                        MeetingStatus.DONE
                )
                .stream()
                .map(meeting -> new StalledSummaryMeetingCandidate(
                        meeting.getId(),
                        meeting.getProjectId(),
                        meeting.getTitle(),
                        meeting.getStartAt()
                ))
                .toList();
    }

    /* MEET-17 대시보드 카드 후보로 회사·스코프 조건을 만족하는 최근 회의를 상한 개수만큼 조회한다. */
    @Override
    public List<DashboardMeetingCandidate> findDashboardMeetings(DashboardMeetingCriteria criteria) {
        /* 스코프별 조건을 Specification으로 구성해 조합마다 별도 @Query를 만들지 않는다. */
        Specification<MeetingJpaEntity> specification = buildDashboardMeetingSpecification(criteria);

        /* 카드는 총 건수가 필요 없으므로 페이지 메타 없이 상한 개수만큼만 데이터베이스에서 자른다. */
        PageRequest pageRequest = PageRequest.of(
                0,
                criteria.limit(),
                Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"))
        );

        return springDataMeetingRepository.findAll(specification, pageRequest)
                .getContent()
                .stream()
                .map(this::toDashboardMeetingCandidate)
                .toList();
    }

    /* MEET-17의 회사·취소 제외·스코프 조건을 Specification으로 만든다. */
    private Specification<MeetingJpaEntity> buildDashboardMeetingSpecification(DashboardMeetingCriteria criteria) {
        return (meeting, criteriaQuery, criteriaBuilder) -> {
            /* 모든 스코프가 회사 범위와 취소 회의 제외를 공통으로 적용한다. */
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(meeting.get("companyId"), criteria.companyId()));
            predicates.add(criteriaBuilder.notEqual(meeting.get("status"), MeetingStatus.CANCELED));

            /* MEET-18 온라인 회의는 startAt이 없어 이 카드의 예약 시간 정렬·표시와 맞지 않아 제외한다. */
            predicates.add(criteriaBuilder.isFalse(meeting.get("isOnline")));

            /* 스코프별로 정확히 하나의 소유·소속 조건만 추가한다. */
            switch (criteria.scope()) {
                case OWNER -> predicates.add(
                        criteriaBuilder.equal(meeting.get("hostMemberId"), criteria.requesterMemberId())
                );
                case TEAM -> {
                    /* 팀장이 직접 개설한 회의만 보이지 않도록 관련 액션이 있는 회의로 한정한다. */
                    predicates.add(criteriaBuilder.equal(meeting.get("teamId"), criteria.requesterTeamId()));
                    predicates.add(criteriaBuilder.isNotNull(meeting.get("relatedActionId")));
                }
                case ME -> predicates.add(criteriaBuilder.exists(
                        attendeeExistsSubquery(meeting, criteriaQuery, criteriaBuilder, criteria.requesterMemberId())
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /* 회의 엔티티를 MEET-17 대시보드 카드 조립에 필요한 최소 읽기 모델로 변환한다. */
    private DashboardMeetingCandidate toDashboardMeetingCandidate(MeetingJpaEntity meeting) {
        return new DashboardMeetingCandidate(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getProjectId(),
                meeting.getStatus(),
                meeting.getMeetingRoomId(),
                meeting.getStartAt()
        );
    }

}
