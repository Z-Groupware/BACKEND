package com.module06.backend.action.application.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.usecase.GetTeamMemberStatusUseCase.TeamMemberItem;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.PositionReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ReferenceMemberStatus;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.SubTeamReference;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.TeamMemberReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.domain.repository.ActionRepository.AssigneeActionCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamMemberStatusServiceTest {

    private static final Long TEAM = 7L;

    @Mock
    private ActionReferenceRepository actionReferenceRepository;

    @Mock
    private ActionRepository actionRepository;

    private TeamMemberStatusService service() {
        return new TeamMemberStatusService(actionReferenceRepository, actionRepository);
    }

    @Test
    void getTeamMemberStatusJoinsRoleAndPositionLabelsAndActionCount() {
        TeamMemberStatusService service = service();
        TeamMemberReference member = new TeamMemberReference(10L, "이하윤", 20L, 30L, ReferenceMemberStatus.ACTIVE);
        when(actionReferenceRepository.findTeamMemberReferences(TEAM)).thenReturn(List.of(member));
        when(actionReferenceRepository.findSubTeamReferences(List.of(20L)))
                .thenReturn(List.of(new SubTeamReference(20L, "프론트엔드")));
        when(actionReferenceRepository.findPositionReferences(List.of(30L)))
                .thenReturn(List.of(new PositionReference(30L, "선임")));
        when(actionRepository.countActionsByAssigneeMemberIds(List.of(10L)))
                .thenReturn(List.of(new AssigneeActionCount(10L, 3L)));

        var result = service.getTeamMemberStatus(TEAM);

        assertThat(result.items()).hasSize(1);
        TeamMemberItem item = result.items().get(0);
        assertThat(item.memberId()).isEqualTo(10L);
        assertThat(item.name()).isEqualTo("이하윤");
        assertThat(item.roleName()).isEqualTo("프론트엔드");
        assertThat(item.positionName()).isEqualTo("선임");
        assertThat(item.status()).isEqualTo(ReferenceMemberStatus.ACTIVE);
        assertThat(item.actionCount()).isEqualTo(3L);
    }

    @Test
    void getTeamMemberStatusDefaultsActionCountToZeroWhenMemberHasNoActions() {
        TeamMemberStatusService service = service();
        TeamMemberReference member = new TeamMemberReference(11L, "박도현", null, null, ReferenceMemberStatus.VACATION);
        when(actionReferenceRepository.findTeamMemberReferences(TEAM)).thenReturn(List.of(member));
        when(actionReferenceRepository.findSubTeamReferences(List.of())).thenReturn(List.of());
        when(actionReferenceRepository.findPositionReferences(List.of())).thenReturn(List.of());
        when(actionRepository.countActionsByAssigneeMemberIds(List.of(11L))).thenReturn(List.of());

        var result = service.getTeamMemberStatus(TEAM);

        TeamMemberItem item = result.items().get(0);
        assertThat(item.roleName()).isNull();
        assertThat(item.positionName()).isNull();
        assertThat(item.status()).isEqualTo(ReferenceMemberStatus.VACATION);
        assertThat(item.actionCount()).isZero();
    }

    @Test
    void getTeamMemberStatusReturnsEmptyListWithoutQueryingReferencesWhenTeamHasNoMembers() {
        TeamMemberStatusService service = service();
        when(actionReferenceRepository.findTeamMemberReferences(TEAM)).thenReturn(List.of());

        assertThat(service.getTeamMemberStatus(TEAM).items()).isEmpty();
        verify(actionReferenceRepository, never()).findSubTeamReferences(anyList());
        verify(actionReferenceRepository, never()).findPositionReferences(anyList());
        verify(actionRepository, never()).countActionsByAssigneeMemberIds(anyList());
    }
}
