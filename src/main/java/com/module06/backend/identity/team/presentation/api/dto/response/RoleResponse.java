package com.module06.backend.identity.team.presentation.api.dto.response;

import com.module06.backend.identity.team.application.dto.RoleNode;

public record RoleResponse(Long roleId, String name) {

    public static RoleResponse from(RoleNode node) {
        return new RoleResponse(node.roleId(), node.name());
    }
}
