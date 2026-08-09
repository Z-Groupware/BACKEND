package com.module06.backend.identity.member.application.port.out;

/** 마이페이지 셀프 프로필 수정 쓰기 창구. */
public interface MyProfileCommandPort {

    /**
     * null 인 인자는 값을 바꾸지 않는다 — 부분 수정이다. {@code teamId}·{@code positionId} 가 자기
     * 회사 소속인지 확인하는 것은 호출자(서비스) 책임이다 — 여기는 순수 쓰기만 한다.
     */
    void updateProfile(Long memberId, Long teamId, Long positionId, String phone);
}
