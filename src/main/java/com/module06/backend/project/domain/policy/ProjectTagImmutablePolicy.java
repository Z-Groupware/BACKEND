package com.module06.backend.project.domain.policy;

/* comment.
    FR-PJ-04 — 프로젝트 태그 불변 규칙. 태그는 URL 식별자로 쓰이므로 생성 후 변경하면
    링크·북마크가 깨진다. 따라서 수정 요청에 태그가 섞여 와도 반영하지 않는다.
    권한과 무관한 순수 비즈니스 규칙이라 domain.policy에 둔다(권한 판단은 application.policy).

    연결된 클래스
    - Project              : 검사 대상 태그의 소유 모델
    - UpdateProjectService : 이 규칙을 호출하는 수정 유스케이스 (application.service)
*/
public class ProjectTagImmutablePolicy {
}
