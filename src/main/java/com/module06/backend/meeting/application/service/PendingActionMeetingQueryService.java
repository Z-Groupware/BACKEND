package com.module06.backend.meeting.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.ActionQueryPort.UndispatchedActionMeeting;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;
import com.module06.backend.meeting.application.query.GetPendingActionMeetingsQuery;
import com.module06.backend.meeting.application.result.PendingActionMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetPendingActionMeetingsUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.repository.PendingActionMeetingRepository;
import com.module06.backend.meeting.domain.repository.PendingActionMeetingRepository.PendingActionMeetingCandidate;

/*
 * MEET-10 확정 대기 회의 목록 조회를 조율하는 애플리케이션 서비스다.
 *
 * host인 종료 회의 후보는 D 저장소에서 읽고, 분배 대기 판정은 C 액션 도메인에 한 번의
 * 배치 호출로 위임한 뒤 두 결과의 교집합만 남긴다. 회의별로 액션 Port를 반복 호출하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PendingActionMeetingQueryService implements GetPendingActionMeetingsUseCase {

    /* 액션 도메인 배치 계약이 한 번에 허용하는 최대 회의 식별자 개수다. */
    private static final int MEETING_ID_BATCH_SIZE = 200;

    /* 회사·host·종료 상태 조건으로 후보 회의를 조회하는 저장소다. */
    private final PendingActionMeetingRepository pendingActionMeetingRepository;

    /* 분배 대기 액션이 남은 회의와 그 건수를 판정하는 C 연동 Port다. */
    private final ActionQueryPort actionQueryPort;

    /* 회의 카드에 표시할 프로젝트 태그·이름을 일괄 조회하는 C 연동 Port다. */
    private final ProjectQueryPort projectQueryPort;

    /* 로그인 사용자가 host인 종료 회의 중 아직 분배하지 않은 액션이 남은 회의를 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public PendingActionMeetingListResult getPendingActionMeetings(GetPendingActionMeetingsQuery query) {
        /* 인증 주체를 식별할 수 없으면 저장소와 외부 Port를 호출하기 전에 거절한다. */
        validateRequiredValues(query);

        /* 회사·host·DONE 조건을 저장소에서 적용해 startAt 내림차순 후보를 확보한다. */
        List<PendingActionMeetingCandidate> candidates = pendingActionMeetingRepository
                .findHostedDoneMeetings(query.companyId(), query.requesterMemberId());

        /* 후보가 없으면 액션·프로젝트 Port를 호출하지 않고 빈 목록을 정상 반환한다. */
        if (candidates.isEmpty()) {
            return new PendingActionMeetingListResult(List.of());
        }

        /* 후보 회의 식별자를 배치로 넘겨 분배 대기 회의와 건수를 한 번에 판정받는다. */
        Map<Long, Long> undispatchedCounts = findUndispatchedCounts(
                query.companyId(),
                candidates.stream().map(PendingActionMeetingCandidate::meetingId).toList()
        );

        /* 액션 도메인이 하나도 반환하지 않으면 프로젝트 조회 없이 빈 목록으로 끝낸다. */
        if (undispatchedCounts.isEmpty()) {
            return new PendingActionMeetingListResult(List.of());
        }

        /* 후보 순서를 유지한 채 액션 도메인이 인정한 회의만 남긴다. */
        List<PendingActionMeetingCandidate> pendingMeetings = candidates.stream()
                .filter(candidate -> undispatchedCounts.containsKey(candidate.meetingId()))
                .toList();

        /* 교집합이 비면 요청하지 않은 회의만 돌아온 경우이므로 빈 목록으로 응답한다. */
        if (pendingMeetings.isEmpty()) {
            return new PendingActionMeetingListResult(List.of());
        }

        /* 중복을 제거한 프로젝트 식별자를 C 연동 Port에 한 번에 전달한다. */
        List<Long> projectIds = pendingMeetings.stream()
                .map(PendingActionMeetingCandidate::projectId)
                .distinct()
                .toList();
        Map<Long, ProjectSnapshot> projects = indexProjects(
                projectIds,
                projectQueryPort.findProjects(query.companyId(), projectIds)
        );

        /* 저장소가 보장한 startAt·meetingId 내림차순을 유지하면서 카드 결과로 변환한다. */
        List<PendingActionMeetingListResult.MeetingItem> items = pendingMeetings.stream()
                .map(meeting -> toResultItem(
                        meeting,
                        undispatchedCounts.get(meeting.meetingId()),
                        projects.get(meeting.projectId())
                ))
                .toList();

        /* 조회 결과가 없을 수도 있는 정상 목록 계약으로 전체 카드를 반환한다. */
        return new PendingActionMeetingListResult(items);
    }

    /* 인증 식별자가 조회에 사용할 수 있는 값인지 확인한다. */
    private void validateRequiredValues(GetPendingActionMeetingsQuery query) {
        /* 이 목록은 host 본인만 볼 수 있으므로 회사와 구성원 식별자가 모두 있어야 한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.requesterMemberId() == null
                || query.requesterMemberId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 후보 회의 식별자를 배치로 나눠 액션 도메인의 분배 대기 판정을 모은다. */
    private Map<Long, Long> findUndispatchedCounts(Long companyId, List<Long> meetingIds) {
        /* 긴 IN 조건을 피하기 위해 액션 도메인 계약과 동일한 크기로 나눠 호출한다. */
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (int fromIndex = 0; fromIndex < meetingIds.size(); fromIndex += MEETING_ID_BATCH_SIZE) {
            /* 현재 배치의 끝 위치가 전체 후보 목록을 넘지 않도록 제한한다. */
            int toIndex = Math.min(fromIndex + MEETING_ID_BATCH_SIZE, meetingIds.size());

            /* 회의별 반복 호출 대신 배치 단위로 액션 도메인에 한 번씩만 묻는다. */
            List<UndispatchedActionMeeting> batch = actionQueryPort.findMeetingsWithUndispatchedActions(
                    companyId,
                    meetingIds.subList(fromIndex, toIndex)
            );

            /* 분배 대기 건수가 0 이하인 회의는 목록에 남길 이유가 없으므로 제외한다. */
            for (UndispatchedActionMeeting meeting : batch) {
                if (meeting.meetingId() != null && meeting.undispatchedCount() > 0L) {
                    counts.put(meeting.meetingId(), meeting.undispatchedCount());
                }
            }
        }

        /* 이후 교집합 판정에서 값이 바뀌지 않도록 불변 맵으로 반환한다. */
        return Map.copyOf(counts);
    }

    /* 요청한 프로젝트 전체가 회사 범위 조회 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, ProjectSnapshot> indexProjects(
            List<Long> requestedIds,
            List<ProjectSnapshot> snapshots
    ) {
        /* 조회 순서와 무관하게 회의 카드가 식별자로 표시값을 찾을 수 있도록 맵을 만든다. */
        Map<Long, ProjectSnapshot> indexed = new LinkedHashMap<>();
        for (ProjectSnapshot snapshot : snapshots) {
            indexed.put(snapshot.projectId(), snapshot);
        }

        /* 프로젝트 표시 정보가 누락되면 임의 태그를 만들지 않고 데이터 계약 위반으로 실패한다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("확정 대기 회의가 참조하는 프로젝트 표시 정보를 조회할 수 없습니다.");
        }

        /* 응답 조립 중 외부에서 값을 바꾸지 못하도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* 후보 회의와 외부 판정값을 MEET-10 카드 한 건으로 변환한다. */
    private PendingActionMeetingListResult.MeetingItem toResultItem(
            PendingActionMeetingCandidate meeting,
            long pendingActionCount,
            ProjectSnapshot project
    ) {
        /* 후보 조회가 DONE으로 좁혀 놓았으므로 카드 상태는 종료로 고정한다. */
        return new PendingActionMeetingListResult.MeetingItem(
                meeting.meetingId(),
                meeting.title(),
                MeetingStatus.DONE,
                meeting.startAt(),
                pendingActionCount,
                new PendingActionMeetingListResult.Project(
                        project.projectId(),
                        project.tag(),
                        project.name()
                )
        );
    }
}
