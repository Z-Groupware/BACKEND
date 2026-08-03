package com.module06.backend.project.application.policy;

/* comment.
    FR-PJ-01,03 — 프로젝트 생성·수정은 OWNER만 가능하다는 권한 규칙.
    조회는 전 구성원 공개이므로 이 정책을 타지 않는다(기획 description 포함).
    권한 판단은 인증 주체(JWT claim)를 봐야 하므로 domain이 아닌 application에 둔다.
    Controller의 @PreAuthorize가 1차 차단, 이 정책이 2차 방어(5.2절 방어벽 구분).

    연결된 클래스
    - CreateProjectService : 생성 시 호출
    - UpdateProjectService : 수정 시 호출
    - ProjectErrorCode     : 위반 시 던질 에러 코드 (미생성)
*/
public class ProjectOwnerOnlyPolicy {
}
