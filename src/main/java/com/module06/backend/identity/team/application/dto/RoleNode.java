package com.module06.backend.identity.team.application.dto;

/** 부서 안의 "역할"(프론트엔드·백엔드 등, 구 sub_team). 계층이 없어 자식을 갖지 않는다. */
public record RoleNode(Long roleId, String name) {
}
