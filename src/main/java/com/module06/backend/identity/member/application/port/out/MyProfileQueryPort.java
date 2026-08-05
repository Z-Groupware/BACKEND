package com.module06.backend.identity.member.application.port.out;

import java.util.Optional;

import com.module06.backend.identity.member.application.dto.MyProfile;

public interface MyProfileQueryPort {

    /** 퇴사한 회원은 없는 것으로 본다 — deleted_at 필터는 구현 쪽 책임이다. 본인 탈퇴는 없다. */
    Optional<MyProfile> findByMemberId(Long memberId);
}
