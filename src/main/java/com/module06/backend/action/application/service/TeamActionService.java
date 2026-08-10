package com.module06.backend.action.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.MemberReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/* comment.
    팀 액션 리소스(FR-AC-06)를 다루는 단일 구현체. 조회 전용이라 전부 읽기 트랜잭션이다.
    상태 변경(FR-AC-07)은 2026-08-07 폐기 확정 — 팀 액션은 보드에서 완전히 빠지고 하위
    개인 액션 완료 비율로 진행률만 읽기 전용 파생한다(화면 나오는 대로 반영). 그래서 이
    클래스엔 상태 변경 메서드가 없다 — UpdateTeamActionStatusUseCase·BulkUpdateTeamActionStatusUseCase·
    TeamActionLeaderOnlyPolicy는 죽은 스켈레톤이라 함께 삭제했다(FR-AC-04 때와 동일 판단).
    타임라인(FR-AC-08, ?tab=timeline)은 아직 착수 전이다 — 별도 세션.
    UseCase 인터페이스는 엔드포인트 1:1(포트 경계)로 유지하되, 구현체는 같은 리소스를
    다루는 것끼리 이 클래스 하나로 묶었다 — project/action 도메인 공통 원칙(08/04 협의).

    연결된 클래스
    - GetTeamActionsUseCase · GetTeamActionDetailUseCase : 구현하는 계약
    - ActionRepository           : 저장·조회
    - ActionReferenceRepository  : 프로젝트태그·팀명·첨부파일 조인
*/
@Service
@RequiredArgsConstructor
public class TeamActionService implements
        GetTeamActionsUseCase,
        GetTeamActionDetailUseCase,
        GetTeamActionTimelineUseCase {

    private final ActionRepository actionRepository;
    private final ActionReferenceRepository actionReferenceRepository;

    // FR-AC-06 목록 — teamId는 JWT에서만 나오므로 다른 팀 조회가 애초에 불가능하다.
    // 2026-08-10 페이지네이션 도입(이홍근 요청).
    @Override
    @Transactional(readOnly = true)
    public TeamActionListResult getTeamActions(Long teamId, ActionStatus status, String sort, String order, int page, int size) {
        long totalElements = actionRepository.countByTeamId(teamId, status);
        List<Action> actions = actionRepository.findAllByTeamId(teamId, status, sort, order, page, size);
        if (actions.isEmpty()) {
            return new TeamActionListResult(List.of(), totalElements);
        }

        Map<Long, String> projectTagById = toDisplayMap(
                actionReferenceRepository.findProjectReferences(distinct(actions, Action::getProjectId)),
                ProjectReference::projectId, ProjectReference::tag);
        String teamName = actionReferenceRepository.findTeamReferences(List.of(teamId)).stream()
                .findFirst().map(TeamReference::name).orElse(null);

        List<TeamActionListItem> items = actions.stream()
                .map(action -> new TeamActionListItem(action, projectTagById.get(action.getProjectId()), teamName))
                .toList();

        return new TeamActionListResult(items, totalElements);
    }

    // FR-AC-06 상세 — 전 구성원 공개, 회사 스코프와 TEAM 종류만 다시 확인한다(IDOR 방지).
    @Override
    @Transactional(readOnly = true)
    public TeamActionDetail getTeamActionDetail(Long companyId, Long teamActionId) {
        Action action = requireOwnCompanyTeamAction(companyId, teamActionId);

        String projectTag = actionReferenceRepository.findProjectReferences(List.of(action.getProjectId())).stream()
                .findFirst().map(ProjectReference::tag).orElse(null);
        String teamName = actionReferenceRepository.findTeamReferences(List.of(action.getTeamId())).stream()
                .findFirst().map(TeamReference::name).orElse(null);
        List<ActionReferenceRepository.AttachmentReference> attachments =
                actionReferenceRepository.findProjectAttachments(action.getProjectId());

        return new TeamActionDetail(action, projectTag, teamName, attachments);
    }

    // FR-AC-08 타임라인(?tab=timeline) — 상세와 같은 IDOR 방지 확인 후 하위 개인 액션을 담당자명과 함께 내려준다.
    // projectTag·teamName은 담지 않는다 — 이미 팀 액션 상세 화면 안(같은 프로젝트·같은 팀)이라 중복이다.
    @Override
    @Transactional(readOnly = true)
    public List<TimelineItem> getTeamActionTimeline(Long companyId, Long teamActionId) {
        requireOwnCompanyTeamAction(companyId, teamActionId);

        // requireOwnCompanyTeamAction 통과 = teamActionId의 companyId가 이 companyId와 같다는 뜻이라
        // 반환값에서 다시 꺼낼 필요 없이 파라미터 그대로 조회 조건에 쓴다.
        List<Action> children = actionRepository.findAllByParentActionId(companyId, teamActionId);
        if (children.isEmpty()) {
            return List.of();
        }

        Map<Long, String> assigneeNameById = toDisplayMap(
                actionReferenceRepository.findMemberReferences(distinctNonNull(children, Action::getAssigneeMemberId)),
                MemberReference::memberId, MemberReference::name);

        return children.stream()
                .map(action -> new TimelineItem(
                        action, action.getAssigneeMemberId() == null ? null : assigneeNameById.get(action.getAssigneeMemberId())))
                .toList();
    }

    // 상세·타임라인 공용 — 다른 회사 팀 액션 id나 PERSONAL 액션 id를 넣으면 존재하지 않는 것과 같은 404로 덮는다(#100과 동일 판단).
    private Action requireOwnCompanyTeamAction(Long companyId, Long teamActionId) {
        Action action = actionRepository.findById(teamActionId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.ACTION_NOT_FOUND));

        if (!companyId.equals(action.getCompanyId()) || action.getActionType() != ActionType.TEAM) {
            throw new BusinessException(ActionErrorCode.ACTION_NOT_FOUND);
        }

        return action;
    }

    private static List<Long> distinct(List<Action> actions, Function<Action, Long> extractor) {
        return actions.stream().map(extractor).distinct().toList();
    }

    private static List<Long> distinctNonNull(List<Action> actions, Function<Action, Long> extractor) {
        return actions.stream().map(extractor).filter(Objects::nonNull).distinct().toList();
    }

    // Collectors.toMap은 값이 null이거나 키가 중복되면 예외를 던져 500으로 샌다 —
    // 지금은 두 경우 다 안 생기지만(project.tag NOT NULL, findAllById는 PK라 중복 없음),
    // 재사용 시 조용한 함정이 되는 걸 막기 위해 방어적으로 구현한다(2026-08-07, CodeRabbit 지적).
    private static <T> Map<Long, String> toDisplayMap(List<T> references, Function<T, Long> idFn, Function<T, String> nameFn) {
        Map<Long, String> result = new HashMap<>();
        references.forEach(reference -> result.putIfAbsent(idFn.apply(reference), nameFn.apply(reference)));
        return result;
    }
}
