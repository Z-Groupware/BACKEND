package com.module06.backend.identity.auth.presentation.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.identity.auth.application.usecase.ChangeMyPasswordUseCase;
import com.module06.backend.identity.auth.application.usecase.LoginUseCase;
import com.module06.backend.identity.auth.application.usecase.LogoutUseCase;
import com.module06.backend.identity.auth.application.usecase.ReissueTokenUseCase;
import com.module06.backend.identity.auth.presentation.api.dto.request.ChangeMyPasswordRequest;
import com.module06.backend.identity.auth.presentation.api.dto.request.LoginRequest;
import com.module06.backend.identity.auth.presentation.api.dto.request.ReissueTokenRequest;
import com.module06.backend.identity.auth.presentation.api.dto.request.UpdateMyProfileRequest;
import com.module06.backend.identity.auth.presentation.api.dto.response.MyProfileResponse;
import com.module06.backend.identity.auth.presentation.api.dto.response.ReissuedTokenResponse;
import com.module06.backend.identity.auth.presentation.api.dto.response.TokenResponse;
import com.module06.backend.identity.member.application.usecase.GetMyProfileUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMyProfileUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "인증 · 기업 조회 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final ReissueTokenUseCase reissueTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ChangeMyPasswordUseCase changeMyPasswordUseCase;
    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;

    @Operation(summary = "로그인", description = "로그인 2단계. 기업 코드·이메일·비밀번호로 토큰을 발급합니다.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = TokenResponse.from(loginUseCase.login(request.toCommand()));
        return ApiResponse.success("로그인되었습니다", response);
    }

    /*
     * 공개를 명시한다(Gate 1 · AUTHZ_001). 이 엔드포인트는 인증을 요구할 수 없다 — 액세스 토큰이
     * 만료돼서 부르는 자리인데 유효한 액세스 토큰을 요구하면 재발급 자체가 불가능해진다.
     * 대신 갱신표의 서명·저장소 존재 여부·절대 수명이 인가를 대신한다(AuthService.reissue).
     *
     * 어노테이션을 생략해도 동작은 같지만, 그러면 "공개로 정했다"와 "붙이는 것을 잊었다"를
     * 리뷰에서 구분할 수 없다. 그래서 규칙이 생략을 잡고, 공개는 이렇게 적어 둔다
     * (PublicBillingConfigController 와 같은 방식).
     */
    @Operation(summary = "토큰 재발급",
            description = "갱신표로 새 액세스 토큰을 받습니다. 갱신표도 함께 교체됩니다(로테이션).")
    @PreAuthorize("permitAll()")
    @PostMapping("/refresh")
    public ApiResponse<ReissuedTokenResponse> refresh(@Valid @RequestBody ReissueTokenRequest request) {
        ReissuedTokenResponse response = ReissuedTokenResponse.from(
                reissueTokenUseCase.reissue(request.refreshToken()));
        return ApiResponse.success("토큰을 재발급했습니다", response);
    }

    /*
     * 대상을 바디로 받지 않고 토큰에서 꺼낸다. 바디로 받으면 남의 memberId 를 넣어
     * 다른 사람을 로그아웃시킬 수 있다.
     */
    @Operation(summary = "로그아웃",
            description = "갱신표를 폐기합니다. 이미 발급된 액세스 토큰은 남은 수명(30분)까지 유효합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal me) {
        logoutUseCase.logout(me.memberId());
        return ApiResponse.successWithoutData("로그아웃되었습니다");
    }

    @Operation(summary = "내 정보", description = "프론트 부트스트랩. 사이드바 메뉴 구성의 기준입니다.")
    @GetMapping("/me")
    public ApiResponse<MyProfileResponse> me(@AuthenticationPrincipal AuthPrincipal me) {
        MyProfileResponse response = MyProfileResponse.from(getMyProfileUseCase.get(me.memberId()));
        return ApiResponse.success("내 정보를 조회했습니다", response);
    }

    /*
     * 대상을 바디로 받지 않고 토큰에서 꺼낸다 — logout() 과 같은 이유로, 남의 프로필을 고치는
     * IDOR 를 막는다. 부서·직급·전화번호만 바뀐다 — 권한·이름·이메일은 이 경로로 못 바꾼다.
     */
    @Operation(summary = "마이페이지 프로필 수정",
            description = "부서·직급·전화번호만 셀프로 바꿉니다. 보낸 필드만 반영됩니다.")
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MyProfileResponse> updateMe(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody UpdateMyProfileRequest request) {
        MyProfileResponse response = MyProfileResponse.from(
                updateMyProfileUseCase.update(request.toCommand(me.memberId(), me.companyId())));
        return ApiResponse.success("프로필을 수정했습니다", response);
    }

    /*
     * 비밀번호를 바꾸는 유일한 경로다. 관리자 재발급도, 최초 로그인 강제 변경도 없다.
     *
     * 대상을 바디로 받지 않고 토큰에서 꺼낸다 — updateMe()·logout() 과 같은 이유로, 남의
     * 비밀번호를 바꾸는 IDOR 를 막는다.
     *
     * 성공하면 이 사람의 갱신표가 전부 사라진다. 프론트는 200 을 받으면 토큰을 버리고 로그인
     * 화면으로 보내야 한다 — 그대로 두면 죽은 토큰으로 401 만 반복해서 맞는다.
     */
    @Operation(summary = "마이페이지 비밀번호 변경",
            description = "현재 비밀번호로 본인 확인 후 교체합니다. 성공하면 모든 기기의 로그인이 해제됩니다.")
    @PatchMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changeMyPassword(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody ChangeMyPasswordRequest request) {
        changeMyPasswordUseCase.changePassword(request.toCommand(me.memberId(), me.companyId()));
        return ApiResponse.successWithoutData("비밀번호를 변경했습니다. 다시 로그인해 주세요.");
    }
}
