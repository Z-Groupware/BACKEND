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

    private CompanyRegistrationRequest request(String address) {
        return new CompanyRegistrationRequest("(주)테크스타트", "123-45-67890", "홍길동",
                "contact@company.com", "010-0000-0000", address,
                "6-20", "회의록 자동화 도입 검토", true, true, false);
    }
}
