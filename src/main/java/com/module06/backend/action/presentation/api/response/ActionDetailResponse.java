package com.module06.backend.action.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.action.application.usecase.GetActionDetailUseCase.ActionDetail;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    개인 액션 상세 응답 DTO(FR-AC-02). 전 구성원 공개, 조회 전용(Figma 확인 결과 상태변경
    컨트롤 없음, 2026-08-07).
    담당자 이름·소속팀명·프로젝트 태그/이름·상위 팀 액션·출처 회의까지 조인해서 담는다.

    checklist 필드는 없다 — FR-AC-05가 2026-08-08 PO 정식 확인으로 스코프 아웃 확정됐고
    (캘린더·개인 Todo가 그 역할을 대체), FE도 체크리스트 UI를 만든 적이 없어 필드를 아예 뺐다.
    2026-08-06 결정 당시엔 "미착수라 나중에 채울 수도 있으니 필드는 남긴다"였지만, 이제 영구
    스코프 아웃이라 그 전제가 사라졌다.

    startDate는 이미 있던 컬럼(V2.6.7)을 노출만 추가한 것(2026-08-10, 이홍근 요청) — 프로젝트
    쪽 startDate(PR #292)와 같은 성격의 표시용 값이다. TODO 상태면 null이 정상이다.

    연결된 클래스
    - ActionController      : 이 DTO를 내보내는 진입점
    - ActionService          : 이 DTO를 만드는 구현체
    - GetActionDetailUseCase.ActionDetail : 조인된 표시값을 담아오는 입력 (application.usecase)
*/
public record ActionDetailResponse(
        Long id,
        String title,
        String description,
        ActionStatus status,
        LocalDate startDate,
        LocalDate dueDate,
        boolean needsReview,
        String assigneeName,
        String projectTag,
        String projectName,
        String teamName,
        Long parentActionId,
        String parentActionTitle,
        Long sourceMeetingId,
        String sourceMeetingTitle
) {

    public static ActionDetailResponse from(ActionDetail detail) {
        Action action = detail.action();

        return new ActionDetailResponse(
                action.getId(),
                action.getTitle(),
                action.getDescription(),
                action.getStatus(),
                action.getStartDate(),
                action.getDueDate(),
                action.getReviewStatus() == ActionReviewStatus.PENDING,
                detail.assigneeName(),
                detail.projectTag(),
                detail.projectName(),
                detail.teamName(),
                action.getParentActionId(),
                detail.parentActionTitle(),
                action.getSourceMeetingId(),
                detail.sourceMeetingTitle()
        );
    }
}
