package com.module06.backend.project.domain.model;

/* comment.
    프로젝트 진행 상태. DB project.status ENUM과 1:1로 대응한다.
    '지연'은 마감일 기준 파생값이므로 여기에 넣지 않는다(스키마 "파생값은 컬럼 아님" 규칙).
    상태 전환에 서버 제약은 없다 — 단계 건너뛰기·되돌리기 모두 허용, UX 가드는 FE 담당.

    연결된 클래스
    - Project                          : 이 값을 상태 필드로 보유
    - BulkUpdateProjectStatusRequest   : 보드 저장 시 일괄 변경 요청 (presentation)
    - ProjectSummaryResponse           : 목록 응답의 상태 배지 (presentation)
*/
public enum ProjectStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
