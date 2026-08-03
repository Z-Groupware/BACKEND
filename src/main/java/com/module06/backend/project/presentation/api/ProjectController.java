package com.module06.backend.project.presentation.api;

/* comment.
    FR-PJ-01,02,03,05,06 — 프로젝트 본체 API 진입점. base path = /api/projects.
    담당 엔드포인트
    - POST   /api/projects                        생성 (OWNER)
    - GET    /api/projects                        목록 조회 (전 구성원)
    - GET    /api/projects/{projectId}            상세(기획 탭) 조회 (전 구성원)
    - GET    /api/projects/{projectId}/timeline    타임라인 탭 조회 (전 구성원)
    - PATCH  /api/projects/{projectId}            수정 (OWNER)
    - PATCH  /api/projects/status/bulk            보드 상태 일괄 변경 (OWNER)
    응답은 ApiResponse, 예외는 BusinessException으로만 낸다 — 개별 try-catch 금지(0절 4항).

    연결된 클래스
    - CreateProjectUseCase · GetProjectListUseCase · GetProjectDetailUseCase ·
      GetProjectTimelineUseCase · UpdateProjectUseCase · BulkUpdateProjectStatusUseCase : 호출 대상
    - CreateProjectRequest · UpdateProjectRequest · BulkUpdateProjectStatusRequest      : 입력 DTO
    - ProjectSummaryResponse · ProjectDetailResponse · ProjectTimelineItemResponse      : 출력 DTO
    - ApiResponse                                                                       : 성공 응답 래퍼
*/
public class ProjectController {
}
