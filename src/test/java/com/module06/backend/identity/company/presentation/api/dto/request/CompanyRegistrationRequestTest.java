package com.module06.backend.identity.company.presentation.api.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.module06.backend.identity.company.application.command.RegisterCompanyCommand;

@DisplayName("기업 등록 신청 본문 → 커맨드 변환")
class CompanyRegistrationRequestTest {

    @Test
    @DisplayName("주소를 그대로 커맨드에 실어 보낸다")
    void carriesAddress() {
        assertThat(request("서울시 강남구 테헤란로 123").toCommand())
                .extracting(RegisterCompanyCommand::address)
                .isEqualTo("서울시 강남구 테헤란로 123");
    }

    /*
     * 지도 SDK 가 뜨지 않는 화면은 입력칸을 비운 채 "" 를 보낸다. 그대로 저장하면
     * address != null 이 참이 되어 오시는 길 같은 화면이 빈 주소를 주소로 취급한다.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" → null")
    @ValueSource(strings = {"", " ", "\t", "   "})
    @DisplayName("빈 주소는 NULL 로 접는다 — 주소 없음의 표현은 하나여야 한다")
    void foldsBlankAddressToNull(String blank) {
        assertThat(request(blank).toCommand().address()).isNull();
    }

    @Test
    @DisplayName("주소를 아예 안 보내도 변환은 성립한다 — 선택 항목이다")
    void addressIsOptional() {
        assertThat(request(null).toCommand().address()).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백은 떼고 저장한다")
    void stripsSurroundingWhitespace() {
        assertThat(request("  서울시 강남구 테헤란로 123  ").toCommand().address())
                .isEqualTo("서울시 강남구 테헤란로 123");
    }

    /*
     * 규모·목적도 화면에서 고르지 않으면 "" 로 온다. 주소와 같은 이유로 NULL 로 접는다 —
     * 선택 입력값의 "값 없음"이 필드마다 다른 표현이면 읽는 쪽이 필드마다 다르게 분기해야 한다.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" → null")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("빈 임직원 규모·이용 목적도 NULL 로 접는다")
    void foldsBlankOptionalFieldsToNull(String blank) {
        RegisterCompanyCommand command = request(null, blank, blank).toCommand();

        assertThat(command.employeeScale()).isNull();
        assertThat(command.purpose()).isNull();
    }

    @Test
    @DisplayName("규모·목적도 앞뒤 공백을 떼고 저장한다")
    void stripsWhitespaceOnOptionalFields() {
        RegisterCompanyCommand command = request(null, "  6-20  ", "  회의록 자동화  ").toCommand();

        assertThat(command.employeeScale()).isEqualTo("6-20");
        assertThat(command.purpose()).isEqualTo("회의록 자동화");
    }

    private CompanyRegistrationRequest request(String address) {
        return request(address, "6-20", "회의록 자동화 도입 검토");
    }

    private CompanyRegistrationRequest request(String address, String employeeScale, String purpose) {
        return new CompanyRegistrationRequest("(주)테크스타트", "123-45-67890", "홍길동",
                "contact@company.com", "010-0000-0000", address,
                employeeScale, purpose, true, true, false);
    }
}
