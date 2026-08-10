package com.module06.backend.identity.company.presentation.api.dto.request;

import com.module06.backend.identity.company.application.command.RegisterCompanyCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기업 등록 신청 본문. 비로그인 화면에서 대표가 직접 넣는다.
 *
 * <p>동의는 {@code boolean} 으로 받고 시각은 서버가 찍는다. 클라이언트가 보낸 시각을 저장하면
 * "동의한 적 없다"는 분쟁에서 증거가 되지 못한다.
 *
 * <p>필수 동의 2종을 {@code @AssertTrue} 로 막지 않는다 — 그러면 400 이 필드 검증 메시지로 나가
 * "동의해 주세요"라는 안내를 프론트가 다시 만들어야 한다. 서비스에서
 * {@code TERMS_NOT_AGREED} 로 던져 에러 코드 카탈로그를 통해 나가게 한다.
 *
 * <p>{@code address} 는 선택이다. 화면은 지도로 고르게 하지만 지도 SDK 가 뜨지 않는 환경이 있어
 * 필수로 걸면 그 환경의 사용자가 회사를 못 만든다. 대신 좌표는 받지 않는다 — 저장할 컬럼이 없고,
 * 쓰는 화면이 생길 때 컬럼과 함께 들이는 편이 값만 버려지는 것보다 낫다.
 */
@Schema(description = "기업 등록 신청")
public record CompanyRegistrationRequest(

        @Schema(description = "기업명", example = "(주)테크스타트")
        @NotBlank(message = "기업명을 입력해 주세요.")
        @Size(max = 100)
        String companyName,

        @Schema(description = "사업자등록번호", example = "123-45-67890")
        @NotBlank(message = "사업자등록번호를 입력해 주세요.")
        String registrationNo,

        @Schema(description = "대표자명 — 오너 계정의 이름이 된다", example = "홍길동")
        @NotBlank(message = "대표자명을 입력해 주세요.")
        @Size(max = 50)
        String representativeName,

        @Schema(description = "담당자 이메일 — 오너 계정의 로그인 아이디가 된다",
                example = "contact@company.com")
        @NotBlank(message = "담당자 이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255)
        String managerEmail,

        @Schema(description = "담당자 연락처", example = "010-0000-0000")
        @NotBlank(message = "담당자 연락처를 입력해 주세요.")
        @Size(max = 30)
        String managerPhone,

        @Schema(description = "회사 주소 (선택)", example = "서울시 강남구 테헤란로 123")
        @Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.")
        String address,

        @Schema(description = "임직원 규모 구간 (선택)", example = "6-20")
        @Size(max = 20)
        String employeeScale,

        @Schema(description = "이용 목적 (선택)", example = "회의록 자동화 도입 검토")
        @Size(max = 500)
        String purpose,

        @Schema(description = "이용약관 동의 (필수)", example = "true")
        boolean agreedTerms,

        @Schema(description = "개인정보 처리방침 동의 (필수)", example = "true")
        boolean agreedPrivacy,

        @Schema(description = "마케팅 수신 동의 (선택)", example = "false")
        boolean agreedMarketing
) {

    public RegisterCompanyCommand toCommand() {
        return new RegisterCompanyCommand(
                companyName, registrationNo, representativeName, managerEmail, managerPhone,
                blankToNull(address), employeeScale, purpose,
                agreedTerms, agreedPrivacy, agreedMarketing);
    }

    /**
     * 주소만 빈 문자열을 NULL 로 접는다. 지도를 못 쓰는 화면은 입력칸을 비운 채 {@code ""} 를 보내는데,
     * 그대로 저장하면 "주소가 있다"({@code address != null})가 참이 되어 오시는 길 같은 화면이 빈
     * 주소를 주소로 취급한다. 주소 없음은 NULL 로 통일한다.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
