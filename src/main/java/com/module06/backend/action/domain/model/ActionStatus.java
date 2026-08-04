package com.module06.backend.action.domain.model;

/* comment.
    액션 진행 상태. DB action.status ENUM과 1:1로 대응한다.
    '지연'은 마감일 기준 파생값이므로 여기에 넣지 않는다(스키마 "파생값은 컬럼 아님" 규칙).
    상태 전환에 서버 제약은 없다 — 단계 건너뛰기·되돌리기 모두 허용, UX 가드는 FE 담당.

    연결된 클래스
    - Action                        : 이 값을 상태 필드로 보유
    - BulkUpdateActionStatusRequest : 보드 저장 시 일괄 변경 요청(개인·팀 공용) (presentation)
*/
public enum ActionStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
