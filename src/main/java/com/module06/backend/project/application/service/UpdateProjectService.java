package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.UpdateProjectUseCase;

/* comment.
    FR-PJ-03 프로젝트 수정 구현체. 쓰기 트랜잭션 경계를 가진다.
    흐름: OWNER 권한 검사 → 태그 불변 규칙 적용(요청의 태그 무시) → 필드 반영 → 저장.

    연결된 클래스
    - UpdateProjectUseCase      : 구현하는 계약
    - UpdateProjectCommand      : 입력
    - ProjectOwnerOnlyPolicy    : 권한 검사
    - ProjectTagImmutablePolicy : 태그 불변 검사
    - ProjectRepository          : 조회·저장
*/
public class UpdateProjectService implements UpdateProjectUseCase {
}
