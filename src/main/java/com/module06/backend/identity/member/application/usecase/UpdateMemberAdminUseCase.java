package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.command.UpdateMemberAdminCommand;
import com.module06.backend.identity.member.application.dto.MemberDetail;

public interface UpdateMemberAdminUseCase {

    MemberDetail update(UpdateMemberAdminCommand command);
}
