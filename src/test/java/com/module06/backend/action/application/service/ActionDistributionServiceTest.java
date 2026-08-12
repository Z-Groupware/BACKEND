package com.module06.backend.action.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.port.ActionDistributionPort.ActionDistributionItem;
import com.module06.backend.action.application.port.ActionDistributionPort.DistributeActionsCommand;
import com.module06.backend.action.application.port.ActionDistributionPort.DistributedAction;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.model.AssigneeSource;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MeetingReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionDistributionServiceTest {

    private static final Long COMPANY = 1L;
    private static final Long PROJECT = 100L;
    private static final Long MEETING_WITH_TEAM = 500L;
    private static final Long TEAM = 10L;
    private static final Long PARENT_ACTION = 900L;
    private static final Long ASSIGNEE = 7L;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ActionReferenceRepository actionReferenceRepository;

    private ActionDistributionService service;

    @BeforeEach
    void setUp() {
        service = new ActionDistributionService(actionRepository, actionReferenceRepository);
        // saveAll은 입력 순서를 유지하며 id만 채번해 돌려준다 — 실제 어댑터 계약과 동일하게 시뮬레이션.
        lenient().when(actionRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Action> input = invocation.getArgument(0);
            return IntStream.range(0, input.size())
                    .mapToObj(i -> withId(input.get(i), 1000L + i))
                    .toList();
        });
        lenient().when(actionReferenceRepository.findMeetingReferences(anyList())).thenReturn(List.of());
        lenient().when(actionReferenceRepository.findProjectReferences(anyList())).thenReturn(List.of());
        // 기본은 "같은 회사 소속" — 회사 소속 검증 자체를 다루는 테스트에서만 false로 덮어쓴다.
        lenient().when(actionReferenceRepository.existsMemberInCompany(anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    void distributesMixedTeamAndPersonalActionsPreservingOrder() {
        when(actionReferenceRepository.findMeetingReferences(List.of(MEETING_WITH_TEAM)))
                .thenReturn(List.of(new MeetingReference(MEETING_WITH_TEAM, TEAM, PARENT_ACTION, null, null)));

        ActionDistributionItem teamItem = item("팀 액션", ActionType.TEAM, null, LocalDate.of(2026, 8, 20));
        ActionDistributionItem personalItem = item("개인 액션", ActionType.PERSONAL, ASSIGNEE, LocalDate.of(2026, 8, 21));

        List<DistributedAction> result = service.distribute(new DistributeActionsCommand(List.of(teamItem, personalItem)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).actionId()).isEqualTo(1000L);
        assertThat(result.get(0).source()).isEqualTo(teamItem);
        assertThat(result.get(1).actionId()).isEqualTo(1001L);
        assertThat(result.get(1).source()).isEqualTo(personalItem);
    }

    @Test
    void fillsMissingDueDateWithProjectDueDateAndMarksDefaulted() {
        LocalDate projectDueDate = LocalDate.of(2026, 8, 31);
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT)))
                .thenReturn(List.of(new ProjectReference(PROJECT, projectDueDate, null, null)));

        ActionDistributionItem itemWithoutDueDate = new ActionDistributionItem(
                "기한 없는 액션", "설명", ActionType.PERSONAL, ASSIGNEE,
                null, null, PROJECT, COMPANY,
                AssigneeSource.EXPLICIT_CALL, 1L, "{}", false
        );

        ArgumentCaptor<List<Action>> captor = ArgumentCaptor.forClass(List.class);
        service.distribute(new DistributeActionsCommand(List.of(itemWithoutDueDate)));
        org.mockito.Mockito.verify(actionRepository).saveAll(captor.capture());

        Action saved = captor.getValue().get(0);
        assertThat(saved.getDueDate()).isEqualTo(projectDueDate);
        assertThat(saved.isDueDateDefaulted()).isTrue();
    }

    @Test
    void createsWithPendingReviewStatusAndDerivesParentActionIdFromMeeting() {
        when(actionReferenceRepository.findMeetingReferences(List.of(MEETING_WITH_TEAM)))
                .thenReturn(List.of(new MeetingReference(MEETING_WITH_TEAM, TEAM, PARENT_ACTION, null, null)));

        ActionDistributionItem personalItem = item("개인 액션", ActionType.PERSONAL, ASSIGNEE, LocalDate.of(2026, 8, 21));

        ArgumentCaptor<List<Action>> captor = ArgumentCaptor.forClass(List.class);
        service.distribute(new DistributeActionsCommand(List.of(personalItem)));
        org.mockito.Mockito.verify(actionRepository).saveAll(captor.capture());

        Action saved = captor.getValue().get(0);
        assertThat(saved.getReviewStatus()).isEqualTo(ActionReviewStatus.PENDING);
        assertThat(saved.getParentActionId()).isEqualTo(PARENT_ACTION);
    }

    @Test
    void rejectsTeamActionFromOwnerHostedMeetingBecauseContractHasNoTeamId() {
        // OWNER가 개설한 회의는 team_id가 NULL이다(V1 주석) — 계약에도 teamId가 없어 특정 불가.
        when(actionReferenceRepository.findMeetingReferences(List.of(MEETING_WITH_TEAM)))
                .thenReturn(List.of(new MeetingReference(MEETING_WITH_TEAM, null, null, null, null)));

        ActionDistributionItem teamItemFromOwnerMeeting = item("팀 액션", ActionType.TEAM, null, LocalDate.of(2026, 8, 20));

        assertThatThrownBy(() -> service.distribute(new DistributeActionsCommand(List.of(teamItemFromOwnerMeeting))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("팀을 특정할 방법이 없습니다");
    }

    @Test
    void allowsPersonalActionWithoutAssignee() {
        // 2026-08-07 — 이태연(review) 요청 반영: AI가 참석자 명단 밖을 가리켰거나 이름을 못 찾은
        // 경우 담당자 없이 PENDING으로 저장하고, RVW-01 검토 화면에서 사람이 채운다.
        ActionDistributionItem personalItemWithoutAssignee =
                new ActionDistributionItem(
                        "담당자 없는 개인 액션", "설명", ActionType.PERSONAL, null,
                        LocalDate.of(2026, 8, 21), null, PROJECT, COMPANY,
                        null, null, null, false
                );

        List<DistributedAction> result =
                service.distribute(new DistributeActionsCommand(List.of(personalItemWithoutAssignee)));

        assertThat(result).hasSize(1);
    }

    @Test
    void rejectsAssigneeFromDifferentCompany() {
        // 2026-08-08 — 이태연 코드리뷰 지적(이슈 #228): 분배 경로는 담당자의 회사 소속을 검증하지 않았다.
        when(actionReferenceRepository.findMeetingReferences(List.of(MEETING_WITH_TEAM)))
                .thenReturn(List.of(new MeetingReference(MEETING_WITH_TEAM, TEAM, PARENT_ACTION, null, null)));
        when(actionReferenceRepository.existsMemberInCompany(ASSIGNEE, COMPANY)).thenReturn(false);

        ActionDistributionItem itemWithForeignAssignee = item("제목", ActionType.PERSONAL, ASSIGNEE, LocalDate.of(2026, 8, 21));

        assertThatThrownBy(() -> service.distribute(new DistributeActionsCommand(List.of(itemWithForeignAssignee))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ActionErrorCode.ACTION_ASSIGNEE_NOT_FOUND);
    }

    @Test
    void skipsCompanyCheckWhenAssigneeIsAbsent() {
        // 담당자 없는 분배는 checkDistribution이 이미 허용하는 케이스라 회사 소속 검증 자체를 안 탄다.
        ActionDistributionItem personalItemWithoutAssignee = new ActionDistributionItem(
                "담당자 없는 개인 액션", "설명", ActionType.PERSONAL, null,
                LocalDate.of(2026, 8, 21), null, PROJECT, COMPANY,
                null, null, null, false
        );

        service.distribute(new DistributeActionsCommand(List.of(personalItemWithoutAssignee)));

        // anyLong()은 null과 매칭되지 않아 existsMemberInCompany(null, companyId) 호출을 놓칠 수
        // 있다 — any()로 null까지 포함해 검증한다(코드래빗 지적, PR #229).
        verify(actionReferenceRepository, never()).existsMemberInCompany(any(), any());
    }

    @Test
    void rejectsUnknownSourceMeetingId() {
        Long unknownMeetingId = 999L;
        // findMeetingReferences가 빈 목록을 돌려주면(존재하지 않는 회의) meetingById에서 못 찾는다.
        ActionDistributionItem itemWithUnknownMeeting = new ActionDistributionItem(
                "제목", "설명", ActionType.PERSONAL, ASSIGNEE,
                LocalDate.of(2026, 8, 21), unknownMeetingId, PROJECT, COMPANY,
                null, null, null, false
        );

        assertThatThrownBy(() -> service.distribute(new DistributeActionsCommand(List.of(itemWithUnknownMeeting))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회의입니다");
    }

    @Test
    void rejectsUnknownProjectIdWhenDueDateNeedsDefaulting() {
        Long unknownProjectId = 777L;
        // findProjectReferences가 빈 목록을 돌려주면(존재하지 않는 프로젝트) 마감일 기본값을 못 채운다.
        ActionDistributionItem itemWithUnknownProject = new ActionDistributionItem(
                "제목", "설명", ActionType.PERSONAL, ASSIGNEE,
                null, null, unknownProjectId, COMPANY,
                null, null, null, false
        );

        assertThatThrownBy(() -> service.distribute(new DistributeActionsCommand(List.of(itemWithUnknownProject))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("마감일을 채울 프로젝트를 찾을 수 없습니다");
    }

    private ActionDistributionItem item(String title, ActionType actionType, Long assigneeMemberId, LocalDate dueDate) {
        return new ActionDistributionItem(
                title, "설명", actionType, assigneeMemberId,
                dueDate, MEETING_WITH_TEAM, PROJECT, COMPANY,
                AssigneeSource.EXPLICIT_CALL, 1L, "{}", false
        );
    }

    private Action withId(Action action, Long id) {
        return Action.reconstitute(
                id,
                action.getCompanyId(),
                action.getProjectId(),
                action.getParentActionId(),
                action.getSourceMeetingId(),
                action.getTeamId(),
                action.getAssigneeMemberId(),
                action.getActionType(),
                action.getTitle(),
                action.getDescription(),
                action.isDone(),
                action.getStartDate(),
                action.getPlannedStartDate(),
                action.getDueDate(),
                action.isDueDateDefaulted(),
                action.getReviewStatus(),
                action.getAssigneeSource(),
                action.getEvidenceTranscriptId(),
                action.getGateSignals(),
                action.isManual(),
                action.getConfirmedAt(),
                action.getCreatedAt(),
                action.getUpdatedAt()
        );
    }
}
