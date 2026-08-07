package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.dto.MemberDetail;

public interface GetMemberDetailUseCase {

    MemberDetail getDetail(Long companyId, Long memberId);
}
