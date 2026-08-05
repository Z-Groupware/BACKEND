package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.dto.MyProfile;

public interface GetMyProfileUseCase {

    MyProfile get(Long memberId);
}
