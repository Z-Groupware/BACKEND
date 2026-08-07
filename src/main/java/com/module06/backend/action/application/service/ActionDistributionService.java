package com.module06.backend.action.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.port.ActionDistributionPort;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.policy.ActionTypeShapePolicy;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MeetingReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    ActionDistributionPort 구현체 — AI(A, 이태연)가 회의 분석 직후 액션을 일괄 생성하는
    FR-AC-01의 정상 경로다. 사용자가 만드는 "+" 수동 추가(예외 경로)는 CreateActionUseCase
    쪽이고 여기가 아니다.

    트랜잭션은 REQUIRES_NEW 없이 기본 전파다 — 호출자(review)의 트랜잭션에 참여해서
    RVW-02/03이 롤백되면 여기서 만든 액션도 같이 사라져야 한다(결정로그 24번).

    이 서비스를 ActionService에 합치지 않은 이유: 08/04의 서비스 통합(13개 -> 3개)은
    REST 리소스 단위 통합이었고, 이건 REST가 아니라 BE 내부 도메인 간 인바운드 포트다.
    수명주기와 호출자가 완전히 다르므로 분리해 둔다.

    계약이 주지 않아 여기서 유도하는 값 3가지(결정로그 25번):
    1. teamId          — TEAM 액션의 대상 팀. 회의(meeting.team_id)에서 가져온다.
    2. parentActionId  — PERSONAL 액션의 상위 팀 액션. 회의(meeting.related_action_id, V3.1.1).
    3. dueDate         — 비어 오면 프로젝트 마감일로 채우고 dueDateDefaulted=true로 표시한다.

    ⚠️ 알려진 계약 한계: OWNER가 개설한 프로젝트 회의는 meeting.team_id가 NULL이라 그 회의에서
    TEAM 액션을 만들려 하면 대상 팀을 특정할 수 없다. 분배 계약에 teamId 필드가 없어 A가 팀을
    지정할 방법도 없다. 지금은 조용히 잘못된 팀에 꽂히는 대신 명시적으로 예외를 던진다 —
    이태연과 계약 보강(teamId 추가) 협의가 필요한 지점이다.

    연결된 클래스
    - ActionDistributionPort    : 구현하는 계약 (application.port)
    - ActionRepository          : 벌크 저장
    - ActionReferenceRepository : 회의·프로젝트 참조값 배치조회
    - ActionTypeShapePolicy     : TEAM/PERSONAL 필드 조합 검증 (domain.policy)
    - Action                    : 생성 대상 애그리거트
*/
@Service
@RequiredArgsConstructor
public class ActionDistributionService implements ActionDistributionPort {

    // 상태 없는 순수 규칙이라 빈으로 띄우지 않는다 — domain 계층에 스프링 애노테이션을 넣지 않기 위함(절대규칙 5항).
    private static final ActionTypeShapePolicy ACTION_TYPE_SHAPE_POLICY = new ActionTypeShapePolicy();

    private final ActionRepository actionRepository;
    private final ActionReferenceRepository actionReferenceRepository;

    @Override
    @Transactional
    public List<DistributedAction> distribute(DistributeActionsCommand command) {
        List<ActionDistributionItem> items = command.items();
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Map<Long, MeetingReference> meetingById = findMeetings(items);
        Map<Long, LocalDate> defaultDueDateByProjectId = findDefaultDueDates(items);

        List<Action> saved = actionRepository.saveAll(
                items.stream()
                        .map(item -> toAction(item, meetingById, defaultDueDateByProjectId))
                        .toList()
        );

        // 입력 순서 = 저장 결과 순서라는 ActionRepository.saveAll 계약에 기대어 인덱스로 짝짓는다.
        // 어긋나면 엉뚱한 actionId가 엉뚱한 원본과 매칭돼도 아무도 눈치채지 못하므로 여기서 막는다.
        if (saved.size() != items.size()) {
            throw new IllegalStateException(
                    "저장된 액션 수가 분배 입력 수와 다릅니다: 입력=" + items.size() + ", 저장=" + saved.size());
        }

        return IntStream.range(0, items.size())
                .mapToObj(i -> new DistributedAction(saved.get(i).getId(), items.get(i)))
                .toList();
    }

    private Map<Long, MeetingReference> findMeetings(List<ActionDistributionItem> items) {
        List<Long> meetingIds = items.stream()
                .map(ActionDistributionItem::sourceMeetingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return actionReferenceRepository.findMeetingReferences(meetingIds).stream()
                .collect(Collectors.toMap(MeetingReference::meetingId, Function.identity()));
    }

    // 마감일이 비어 온 액션이 하나도 없으면 프로젝트를 조회하지 않는다.
    private Map<Long, LocalDate> findDefaultDueDates(List<ActionDistributionItem> items) {
        List<Long> projectIds = items.stream()
                .filter(item -> item.dueDate() == null)
                .map(ActionDistributionItem::projectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return actionReferenceRepository.findProjectReferences(projectIds).stream()
                .filter(project -> project.dueDate() != null)
                .collect(Collectors.toMap(ProjectReference::projectId, ProjectReference::dueDate));
    }

    private Action toAction(
            ActionDistributionItem item,
            Map<Long, MeetingReference> meetingById,
            Map<Long, LocalDate> defaultDueDateByProjectId
    ) {
        MeetingReference meeting = findMeeting(item, meetingById);

        Long teamId = item.actionType() == ActionType.TEAM ? resolveTeamId(item, meeting) : null;
        Long parentActionId = item.actionType() == ActionType.PERSONAL && meeting != null
                ? meeting.relatedActionId()
                : null;

        boolean dueDateDefaulted = item.dueDate() == null;
        LocalDate dueDate = dueDateDefaulted
                ? resolveDefaultDueDate(item, defaultDueDateByProjectId)
                : item.dueDate();

        // 분배 경로 — PERSONAL이어도 담당자가 없을 수 있다(2026-08-07, 이태연 요청).
        ACTION_TYPE_SHAPE_POLICY.checkDistribution(item.actionType(), teamId, item.assigneeMemberId());

        return Action.create(
                item.companyId(),
                item.projectId(),
                parentActionId,
                item.sourceMeetingId(),
                teamId,
                item.assigneeMemberId(),
                item.actionType(),
                item.title(),
                item.description(),
                dueDate,
                dueDateDefaulted,
                item.assigneeSource(),
                item.evidenceTranscriptId(),
                item.gateSignals(),
                item.isManual()
        );
    }

    private MeetingReference findMeeting(ActionDistributionItem item, Map<Long, MeetingReference> meetingById) {
        if (item.sourceMeetingId() == null) {
            return null;
        }

        MeetingReference meeting = meetingById.get(item.sourceMeetingId());
        if (meeting == null) {
            throw new IllegalArgumentException("존재하지 않는 회의입니다: sourceMeetingId=" + item.sourceMeetingId());
        }
        return meeting;
    }

    private Long resolveTeamId(ActionDistributionItem item, MeetingReference meeting) {
        if (meeting == null) {
            throw new IllegalArgumentException(
                    "TEAM 액션의 대상 팀은 회의에서 유도하므로 sourceMeetingId가 필요합니다: title=" + item.title());
        }
        if (meeting.teamId() == null) {
            throw new IllegalStateException(
                    "대상 팀이 없는 회의(OWNER 개설)에서는 TEAM 액션을 만들 수 없습니다 — "
                            + "분배 계약에 teamId가 없어 팀을 특정할 방법이 없습니다: sourceMeetingId=" + meeting.meetingId());
        }
        return meeting.teamId();
    }

    private LocalDate resolveDefaultDueDate(ActionDistributionItem item, Map<Long, LocalDate> defaultDueDateByProjectId) {
        LocalDate dueDate = defaultDueDateByProjectId.get(item.projectId());
        if (dueDate == null) {
            throw new IllegalArgumentException(
                    "마감일을 채울 프로젝트를 찾을 수 없습니다: projectId=" + item.projectId());
        }
        return dueDate;
    }
}
