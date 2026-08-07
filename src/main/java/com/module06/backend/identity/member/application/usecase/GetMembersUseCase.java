package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.application.dto.MemberPage;

public interface GetMembersUseCase {

    MemberPage getMembers(Long companyId, MemberListFilter filter, String q, int page, int size);
}
