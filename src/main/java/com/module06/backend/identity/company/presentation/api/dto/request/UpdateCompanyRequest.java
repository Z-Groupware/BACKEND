package com.module06.backend.identity.company.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import com.module06.backend.identity.company.application.command.UpdateCompanyCommand;

/** §4-3. 전부 선택 값이다 — 보낸 필드만 바뀐다. {@code code}는 대상이 아니다(로그인 키). */
@Schema(description = "기업 기본 정보 부분 수정 — null 필드는 값을 바꾸지 않는다")
public record UpdateCompanyRequest(

        @Schema(description = "기업명", example = "(주)테크스타트")
        @Size(max = 100, message = "기업명은 100자 이하로 입력해 주세요.")
        String name,

        @Schema(description = "사업자등록번호 (000-00-00000)", example = "123-45-67890")
        String businessNumber,

        @Schema(description = "대표자명", example = "김서준")
        @Size(max = 50, message = "대표자명은 50자 이하로 입력해 주세요.")
        String representativeName,

        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        @Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.")
        String address,

        @Schema(description = "대표번호", example = "02-1234-5678")
        @Size(max = 30, message = "대표번호는 30자 이하로 입력해 주세요.")
        String phone
) {
    public UpdateCompanyCommand toCommand(Long companyId) {
        return new UpdateCompanyCommand(companyId, name, businessNumber, representativeName, address, phone);
    }
}
