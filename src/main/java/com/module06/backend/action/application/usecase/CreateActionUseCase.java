package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-01 — 액션 수동 추가 기능 계약(예외 경로). AI가 놓친 액션을 사람이 "+"로 넣을 때 쓰인다.
    정상 생성 경로는 ActionDistributionPort — 이 유스케이스는 그 경로를 대체하지 않는다.

    연결된 클래스
    - CreateActionCommand : 입력
    - ActionService : 구현체
    - ActionController    : 호출자 (presentation)
*/
public interface CreateActionUseCase {
}
