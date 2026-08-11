package com.module06.backend.action.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.PositionReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.SubTeamReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamMemberReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.domain.repository.ActionRepository.AssigneeActionCount;

import lombok.RequiredArgsConstructor;

/* comment.
    팀 대시보드 "팀원 현황"(이슈 #352) 전용 구현체. TeamActionService에 얹지 않고 별도 클래스로
    둔다 — 다루는 리소스가 "팀 액션"이 아니라 "팀원"이라 08/04 협의(같은 리소스를 다루는 것끼리
    묶는다)의 적용 대상이 아니다.

    연결된 클래스
    - GetTeamMemberStatusUseCase : 구현하는 계약
    - ActionReferenceRepository  : 로스터·직급·역할 조회
    - ActionRepository           : 담당 액션 수 집계
*/
@Service
@RequiredArgsConstructor
public class TeamMemberStatusService implements GetTeamMemberStatusUseCase {

    private final ActionReferenceRepository actionReferenceRepository;
    private final ActionRepository actionRepository;

    @Override
    @Transactional(readOnly = true)
    public TeamMemberStatusList getTeamMemberStatus(Long teamId) {
        List<TeamMemberReference> members = actionReferenceRepository.findTeamMemberReferences(teamId);
        if (members.isEmpty()) {
            return new TeamMemberStatusList(List.of());
        }

        Map<Long, String> roleNameBySubTeamId = toDisplayMap(
                actionReferenceRepository.findSubTeamReferences(distinctNonNull(members, TeamMemberReference::subTeamId)),
                SubTeamReference::subTeamId, SubTeamReference::name);
        Map<Long, String> positionNameByPositionId = toDisplayMap(
                actionReferenceRepository.findPositionReferences(distinctNonNull(members, TeamMemberReference::positionId)),
                PositionReference::positionId, PositionReference::name);
        Map<Long, Long> actionCountByMemberId = actionRepository
                .countActionsByAssigneeMemberIds(members.stream().map(TeamMemberReference::memberId).toList())
                .stream()
                .collect(Collectors.toMap(AssigneeActionCount::assigneeMemberId, AssigneeActionCount::actionCount));

        List<TeamMemberItem> items = members.stream()
                .map(member -> new TeamMemberItem(
                        member.memberId(),
                        member.name(),
                        member.positionId() == null ? null : positionNameByPositionId.get(member.positionId()),
                        member.subTeamId() == null ? null : roleNameBySubTeamId.get(member.subTeamId()),
                        member.status(),
                        actionCountByMemberId.getOrDefault(member.memberId(), 0L)
                ))
                .toList();

        return new TeamMemberStatusList(items);
    }

    private static List<Long> distinctNonNull(List<TeamMemberReference> members, Function<TeamMemberReference, Long> extractor) {
        return members.stream().map(extractor).filter(Objects::nonNull).distinct().toList();
    }

    // TeamActionService.toDisplayMap과 동일한 방어적 구현(값 null·키 중복이 Collectors.toMap을 500으로 터뜨리는 것 방지).
    private static <T> Map<Long, String> toDisplayMap(List<T> references, Function<T, Long> idFn, Function<T, String> nameFn) {
        Map<Long, String> result = new HashMap<>();
        references.forEach(reference -> result.putIfAbsent(idFn.apply(reference), nameFn.apply(reference)));
        return result;
    }
}
