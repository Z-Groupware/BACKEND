package com.module06.backend.action.infrastructure.adapter;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.capture.application.port.out.ReviewActionCreatePort.ManualAction;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 2026-08-13 · RVW-03 teamId 지원(이홍근 요청) — createManual이 처음으로 TEAM 분기를
 * 타게 됐는데 이 어댑터에 테스트가 없던 상태였다(ActionReviewApplyAdapterTest의
 * teamId 케이스와 같은 이유로 추가).
 */
@ExtendWith(MockitoExtension.class)
class ReviewActionCreateAdapterTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long PROJECT = 31L;
    private static final Long TEAM_ID = 3L;
    private static final Long ATTENDEE = 42L;
    private static final LocalDate DUE = LocalDate.of(2026, 8, 20);

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ActionReferenceRepository actionReferenceRepository;

    private ReviewActionCreateAdapter adapter() {
        return new ReviewActionCreateAdapter(actionRepository, actionReferenceRepository);
    }

    @Test
    void createsPersonalActionWhenTeamIdIsNull() {
        when(actionRepository.save(any())).thenReturn(savedActionWithId(9_001L));

        adapter().createManual(new ManualAction(
                COMPANY, MEETING, PROJECT, ATTENDEE, null, "제목", "내용", DUE, null));

        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(actionRepository).save(captor.capture());
        Action saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(ActionType.PERSONAL);
        assertThat(saved.getAssigneeMemberId()).isEqualTo(ATTENDEE);
        assertThat(saved.getTeamId()).isNull();
        verify(actionReferenceRepository, never()).existsTeamInCompany(any(), any());
    }

    @Test
    void createsTeamActionWhenTeamIdBelongsToCompany() {
        when(actionReferenceRepository.existsTeamInCompany(TEAM_ID, COMPANY)).thenReturn(true);
        when(actionRepository.save(any())).thenReturn(savedActionWithId(9_002L));

        adapter().createManual(new ManualAction(
                COMPANY, MEETING, PROJECT, null, TEAM_ID, "제목", "내용", DUE, null));

        ArgumentCaptor<Action> captor = ArgumentCaptor.forClass(Action.class);
        verify(actionRepository).save(captor.capture());
        Action saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(ActionType.TEAM);
        assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(saved.getAssigneeMemberId()).isNull();
    }

    @Test
    void rejectsTeamIdFromAnotherCompany() {
        when(actionReferenceRepository.existsTeamInCompany(TEAM_ID, COMPANY)).thenReturn(false);

        // A(capture)가 넘긴 teamId라도 다른 회사 팀이면 거절한다 — ActionReviewApplyAdapter와
        // 같은 판단(#100, 클래스 주석).
        assertThatThrownBy(() -> adapter().createManual(new ManualAction(
                COMPANY, MEETING, PROJECT, null, TEAM_ID, "제목", "내용", DUE, null))
        ).isInstanceOf(BusinessException.class);

        verify(actionRepository, never()).save(any());
    }

    // save()가 DB 채번 후 돌려주는 모양을 흉내낸다 — createManual이 반환값의 id를 그대로
    // 쓰므로(null이면 언박싱에서 NPE), 어떤 값으로 저장됐는지는 captor로 따로 검증한다.
    private Action savedActionWithId(long id) {
        return Action.reconstitute(
                id, COMPANY, PROJECT, null, MEETING, null, ATTENDEE,
                ActionType.PERSONAL, "제목", "내용", false, null, null, DUE, false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, true,
                null, null, null
        );
    }
}
