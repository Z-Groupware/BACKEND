package com.module06.backend.identity.member.application.usecase;

import com.module06.backend.identity.member.application.command.UpdateMyProfileCommand;
import com.module06.backend.identity.member.application.dto.MyProfile;

/** 마이페이지 "편집" — 본인 부서·직급·전화번호만 셀프로 바꾼다. */
public interface UpdateMyProfileUseCase {

    MyProfile update(UpdateMyProfileCommand command);
}
