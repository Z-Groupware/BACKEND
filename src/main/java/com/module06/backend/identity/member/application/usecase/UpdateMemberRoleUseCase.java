package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.command.UpdateMemberRoleCommand;
import com.module06.backend.identity.member.application.dto.MemberDetail;

public interface UpdateMemberRoleUseCase {

    MemberDetail update(UpdateMemberRoleCommand command);
}
