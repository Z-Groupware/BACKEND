package com.module06.backend.identity.company.presentation.api.dto.response;

import com.module06.backend.identity.company.application.dto.CompanyRegistrationResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 등록 완료 응답. <b>비밀번호를 담지 않는다.</b>
 *
 * <p>응답에 실으면 브라우저 개발자도구와 중간 프록시 로그에 평문이 그대로 남는다. 비밀번호가
 * 사용자에게 가는 경로는 메일 하나뿐이다.
 */
@Schema(description = "기업 등록 결과")
public record CompanyRegistrationResponse(

        @Schema(description = "생성된 기업 id", example = "7")
        Long companyId,

        @Schema(description = "발급된 기업 코드. 로그인 1단계에 입력한다", example = "NOVA-7K3D")
        String companyCode,

        @Schema(description = "오너 계정의 로그인 아이디", example = "contact@company.com")
        String ownerEmail
) {

    public static CompanyRegistrationResponse from(CompanyRegistrationResult result) {
        return new CompanyRegistrationResponse(
                result.companyId(), result.companyCode(), result.ownerEmail());
    }
}
