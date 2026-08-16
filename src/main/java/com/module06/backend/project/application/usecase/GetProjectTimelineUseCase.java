package com.module06.backend.project.application.usecase;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    FR-PJ-05 — 프로젝트 타임라인 탭 조회 기능 계약. 전 구성원 공개다.
    한 행 = 팀 액션 카드다(부서 요약이 아니다). 한 팀이 같은 프로젝트에 팀 액션을
    여러 개 가질 수 있으므로 행마다 actionId가 실려야 하고, 클릭 시 팀 액션 상세로 내려간다.
    '지연' 배지는 마감일 기준 파생값이다 — 판정은 Action.isDelayed(진행중 && 마감일 < 오늘)를 그대로 쓴다.

    연결된 클래스
    - ProjectService : 구현체
    - ProjectTimelineItemResponse  : 출력 DTO (presentation)
    - ActionQueryPort              : 팀 액션 데이터의 실제 소유자(action 도메인)로부터 조회하는 계약
*/
public interface GetProjectTimelineUseCase {

    List<TimelineItem> getTimeline(Long companyId, Long projectId);

    record TimelineItem(
            Long actionId,
            String title,
            Long teamId,
            String teamName,
            ActionStatus status,
            LocalDate dueDate,
            boolean isDelayed
    ) {
    }
}
