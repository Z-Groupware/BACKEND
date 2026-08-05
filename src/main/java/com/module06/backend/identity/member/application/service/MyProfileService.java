package com.module06.backend.identity.member.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.port.out.MyProfileQueryPort;
import com.module06.backend.identity.member.application.usecase.GetMyProfileUseCase;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyProfileService implements GetMyProfileUseCase {

    private final MyProfileQueryPort myProfileQueryPort;

    @Override
    @Transactional(readOnly = true)
    public MyProfile get(Long memberId) {
        return myProfileQueryPort.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
    }
}
