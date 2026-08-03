package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-05 — 프로젝트 타임라인 탭 조회 기능 계약. 전 구성원 공개다.
    한 행 = 팀 액션 카드다(부서 요약이 아니다). 한 팀이 같은 프로젝트에 팀 액션을
    여러 개 가질 수 있으므로 행마다 actionId가 실려야 하고, 클릭 시 팀 액션 상세로 내려간다.
    '지연' 배지는 마감일 기준 파생값이라 여기서 계산한다.

    연결된 클래스
    - GetProjectTimelineService    : 구현체
    - ProjectTimelineItemResponse  : 출력 DTO (presentation, 미생성)
    - action 도메인               : 팀 액션 데이터의 실제 소유자 (id 기준 조회)
*/
public interface GetProjectTimelineUseCase {
}
