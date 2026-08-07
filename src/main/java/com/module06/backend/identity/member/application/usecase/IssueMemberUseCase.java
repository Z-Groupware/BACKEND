package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.application.dto.IssuedMember;

public interface IssueMemberUseCase {

    IssuedMember issue(IssueMemberCommand command);
}
