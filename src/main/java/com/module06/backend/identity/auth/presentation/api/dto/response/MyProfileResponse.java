package com.module06.backend.identity.auth.presentation.api.dto.response;

import java.time.LocalDate;

import com.module06.backend.identity.member.application.dto.MyProfile;

/**
 * 프론트 부트스트랩 응답. 사이드바 메뉴와 프로필 배지가 전부 이 값으로 갈린다.
 *
 * <p>{@code roleLabel} 은 하위팀이 아니라 부서 안에서 부여하는 자유 라벨이다(명세 §0-3).
 * DB 에는 여전히 관계형 {@code sub_team} 테이블이 있지만(V1 스키마), 이 응답은 프론트가
 * 기대하는 평평한 문자열 계약을 따른다 — {@link MyProfile#roleLabel()} 참조.
 *
 * <p>{@code landingPath} 를 로그인 응답과 똑같이 담는다. 새로고침·재실행으로 로그인 때 받은
 * 값을 프론트가 잃어도, 부트스트랩 호출인 이 응답이 다시 채워준다 — 계산은 항상 서버가 한다
 * (역할별 고정값, {@link com.module06.backend.identity.member.domain.model.Role#landingPath()}).
 *
 * <p>{@code avatarColor} 를 주지 않는다 — 이름으로 색을 뽑는 것은 순수 계산이라 프론트 몫이다.
 * {@code mustChangePassword} 도 없다 — 비밀번호 변경 기능 자체가 없다.
 *
 * <p>{@code teamId}·{@code roleLabel}·{@code jobPositionId} 등은 null 로 나갈 수 있다.
 * 온보딩 전 오너가 그 경우다. {@code plan} 도 null 일 수 있다 — 결제가 필요한 상태를 뜻한다.
 */
public record MyProfileResponse(
        Long memberId,
        Long companyId,
        String companyName,
        String companyCode,
        String name,
        String email,
        String phone,
        Long teamId,
        String teamName,
        String roleLabel,
        Long jobPositionId,
        String positionName,
        String role,
        boolean isAdmin,
        String landingPath,
        boolean isOnboarded,
        String workStatus,
        LocalDate joinedOn,
        String plan
) {

    public static MyProfileResponse from(MyProfile profile) {
        return new MyProfileResponse(
                profile.memberId(), profile.companyId(), profile.companyName(), profile.companyCode(),
                profile.name(), profile.email(), profile.phone(),
                profile.teamId(), profile.teamName(), profile.roleLabel(),
                profile.jobPositionId(), profile.positionName(),
                profile.role().name(), profile.isAdmin(), profile.landingPath(),
                profile.isOnboarded(),
                profile.workStatus().name(), profile.joinedOn(),
                profile.plan() == null ? null : profile.plan().name());
    }
}
