package com.module06.backend.action.presentation.api.response;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.action.application.usecase.GetActionDetailUseCase.ActionDetail;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    개인 액션 상세 응답 DTO(FR-AC-02). 전 구성원 공개, 조회 전용(Figma 확인 결과 상태변경
    컨트롤 없음, 2026-08-07).
    담당자 이름·소속팀명·프로젝트 태그/이름·상위 팀 액션·출처 회의까지 조인해서 담고,
    체크리스트 목록도 인라인으로 싣는다.
    checklist는 FR-AC-05 미착수라 항상 빈 배열이다 — 필드는 응답 스펙 변경(breaking change)을
    피하려고 지금 확정해 넣는다(2026-08-06 결정, project_z_pending_debt_for_handoff 참고).

    연결된 클래스
    - ActionController      : 이 DTO를 내보내는 진입점
    - ActionService          : 이 DTO를 만드는 구현체
    - GetActionDetailUseCase.ActionDetail : 조인된 표시값을 담아오는 입력 (application.usecase)
    - ChecklistItemResponse  : 인라인으로 실리는 체크리스트 항목(FR-AC-05, 항상 빈 배열)
*/
public record ActionDetailResponse(
        Long id,
        String title,
        String description,
        ActionStatus status,
        LocalDate dueDate,
        boolean needsReview,
        String assigneeName,
        String projectTag,
        String projectName,
        String teamName,
        Long parentActionId,
        String parentActionTitle,
        Long sourceMeetingId,
        String sourceMeetingTitle,
        List<ChecklistItemResponse> checklist
) {

    public static ActionDetailResponse from(ActionDetail detail) {
        Action action = detail.action();

        return new ActionDetailResponse(
                action.getId(),
                action.getTitle(),
                action.getDescription(),
                action.getStatus(),
                action.getDueDate(),
                action.getReviewStatus() == ActionReviewStatus.PENDING,
                detail.assigneeName(),
                detail.projectTag(),
                detail.projectName(),
                detail.teamName(),
                action.getParentActionId(),
                detail.parentActionTitle(),
                action.getSourceMeetingId(),
                detail.sourceMeetingTitle(),
                List.of()
        );
    }
}
