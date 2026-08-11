package com.module06.backend.identity.company.presentation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.module06.backend.identity.company.application.command.UpdateCompanyCommand;

/**
 * §4-3. 전부 선택 값이다 — 보낸 필드만 바뀐다. {@code code}는 대상이 아니다(로그인 키).
 *
 * <p>빈 값 차단을 {@code @NotBlank} 로 하지 않는다 — 그건 {@code null} 도 함께 튕겨서, 주소만 고치려고
 * 나머지를 안 실은 요청이 400 이 된다. 부분 수정 계약({@code null} = 미변경)이 깨진다. 대신
 * {@code NOT_BLANK_IF_PRESENT} 로 "안 보내는 건 되지만, 보냈으면 빈 값은 안 된다"만 막는다 —
 * {@code @Pattern} 은 명세상 {@code null} 을 통과시키므로 두 규칙이 한 애노테이션에 들어간다.
 *
 * <p>이 차단이 서버에 있어야 하는 이유는 화면이 이미 막고 있어도 그것이 서버의 보장은 아니기 때문이다.
 * {@code ""} 가 들어오면 {@link com.module06.backend.identity.company.infrastructure.persistence.CompanyJpaEntity}
 * 의 병합이 {@code null} 검사만 하므로 빈 문자열이 그대로 덮어써진다 — 기업명이 사라질 수 있다.
 */
@Schema(description = "기업 기본 정보 부분 수정 — null 필드는 값을 바꾸지 않는다")
public record UpdateCompanyRequest(

        @Schema(description = "기업명", example = "(주)테크스타트")
        @Size(max = 100, message = "기업명은 100자 이하로 입력해 주세요.")
        @Pattern(regexp = NOT_BLANK_IF_PRESENT, message = "기업명은 빈 값으로 보낼 수 없습니다.")
        String name,

        @Schema(description = "사업자등록번호 (000-00-00000)", example = "123-45-67890")
        @Pattern(regexp = NOT_BLANK_IF_PRESENT, message = "사업자등록번호는 빈 값으로 보낼 수 없습니다.")
        String businessNumber,

        @Schema(description = "대표자명", example = "김서준")
        @Size(max = 50, message = "대표자명은 50자 이하로 입력해 주세요.")
        @Pattern(regexp = NOT_BLANK_IF_PRESENT, message = "대표자명은 빈 값으로 보낼 수 없습니다.")
        String representativeName,

        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        @Size(max = 255, message = "주소는 255자 이하로 입력해 주세요.")
        @Pattern(regexp = NOT_BLANK_IF_PRESENT, message = "주소는 빈 값으로 보낼 수 없습니다.")
        String address,

        @Schema(description = "대표번호", example = "02-1234-5678")
        @Size(max = 30, message = "대표번호는 30자 이하로 입력해 주세요.")
        @Pattern(regexp = NOT_BLANK_IF_PRESENT, message = "대표번호는 빈 값으로 보낼 수 없습니다.")
        String phone
) {

    /**
     * 공백 아닌 문자가 최소 하나. {@code @Pattern} 이 {@code null} 은 통과시키므로 "보냈으면"만 걸린다.
     *
     * <p>{@code (?s)} 가 필요하다. {@code @Pattern} 은 문자열 전체를 검사하는데 Java 의 {@code .} 는
     * 기본적으로 줄 종결 문자를 매칭하지 않아서, 이게 없으면 {@code "서울시\n강남구"} 처럼 줄바꿈이 든
     * 값이 "빈 값"으로 튕긴다 — 비어 있지도 않은데 "빈 값으로 보낼 수 없습니다"가 나간다.
     * 등록 신청 쪽은 {@code String.isBlank()} 로 접어서 줄바꿈이 든 주소를 그대로 받으므로, 여기서
     * 막으면 이 PR 이 없애려던 "같은 값인데 경로마다 결과가 다르다"가 다시 생긴다(코드래빗 지적).
     */
    private static final String NOT_BLANK_IF_PRESENT = "(?s).*\\S.*";

    public UpdateCompanyCommand toCommand(Long companyId) {
        return new UpdateCompanyCommand(companyId, name, businessNumber, representativeName, address, phone);
    }
}
