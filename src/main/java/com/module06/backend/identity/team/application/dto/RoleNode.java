package com.module06.backend.identity.team.application.dto;

/**
 * 부서 안의 "역할"(프론트엔드·백엔드 등, 구 sub_team). 계층이 없어 자식을 갖지 않는다.
 *
 * <p>{@code memberCount} 는 이 역할을 달고 있는 <b>재직자</b> 수다. 역할 삭제(§6-12)를 막는
 * 조건과 같은 축으로 세므로, 이 값이 0 이 아니면 삭제가 409 로 막힌다 — 화면이 "N명이 이 역할을
 * 쓰고 있습니다"를 추가 호출 없이 보여줄 수 있게 하려는 것이다(2026-08-14 프론트엔드 요청).
 *
 * <p>시스템 역할 "없음"(id 2)은 부서에 매이지 않아 회사 전체의 미배정 인원 수가 되고, 그래서
 * 모든 부서에 같은 숫자가 실린다. 그 행은 지울 수 없으므로 삭제 판단에 쓰이지 않는다.
 */
public record RoleNode(Long roleId, String name, long memberCount) {
}
