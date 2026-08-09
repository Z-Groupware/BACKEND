package com.module06.backend.identity.member.application.command;

/** 관리 권한(겸직) 부여·회수(§7-7). 호출자가 OWNER 인지는 컨트롤러의 hasRole('OWNER')가 막는다. */
public record UpdateMemberAdminCommand(
        Long companyId,
        Long targetMemberId,
        boolean isAdmin
) {
}
