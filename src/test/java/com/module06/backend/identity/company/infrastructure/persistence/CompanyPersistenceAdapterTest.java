package com.module06.backend.identity.company.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyProfileRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRegistrationRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

/**
 * 부분 수정 병합은 실제로는 엔티티가 한다. 서비스 테스트의 가짜 저장소가 그 규칙을 흉내 내고
 * 있어서, 둘이 어긋나면 아무도 눈치채지 못한 채 초록불이 유지된다 — 그 어긋남을 여기서 잡는다.
 */
@SpringBootTest
@Transactional
@DisplayName("Company 영속성 어댑터 — 프로필 부분 수정")
class CompanyPersistenceAdapterTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyRegistrationRepository registrationRepository;

    @Autowired
    private CompanyProfileRepository profileRepository;

    @Test
    @DisplayName("null 인 필드는 손대지 않는다 — 좌표만 보내면 주소·기업명이 그대로다")
    void nullFieldsAreLeftUntouched() {
        Long companyId = register();
        profileRepository.updateProfile(companyId, null, null, null, "서울시 강남구 테헤란로 123", null, null, null);

        profileRepository.updateProfile(companyId, null, null, null, null, 37.5006, 127.0366, null);

        Company company = find(companyId);
        assertThat(company.address()).isEqualTo("서울시 강남구 테헤란로 123");
        assertThat(company.name()).isEqualTo("(주)테크스타트");
        assertThat(company.latitude()).isEqualTo(37.5006);
        assertThat(company.longitude()).isEqualTo(127.0366);
    }

    @Test
    @DisplayName("빈 주소는 주소와 좌표를 함께 NULL 로 지운다 — 빈 문자열이 그대로 저장되면 안 된다")
    void blankAddressClearsAddressAndCoordinates() {
        Long companyId = register();
        profileRepository.updateProfile(companyId, null, null, null,
                "서울시 강남구 테헤란로 123", 37.5006, 127.0366, null);

        profileRepository.updateProfile(companyId, null, null, null, "   ", null, null, null);

        Company company = find(companyId);
        assertThat(company.address()).isNull();
        assertThat(company.latitude()).isNull();
        assertThat(company.longitude()).isNull();
    }

    @Test
    @DisplayName("탭·줄바꿈만 있는 주소도 지우기다 — 눈에 안 보이는 문자가 주소로 남으면 안 된다")
    void whitespaceOnlyAddressIsAlsoClear() {
        Long companyId = register();
        profileRepository.updateProfile(companyId, null, null, null, "서울시 강남구 테헤란로 123", null, null, null);

        profileRepository.updateProfile(companyId, null, null, null, " \t\n ", null, null, null);

        assertThat(find(companyId).address()).isNull();
    }

    @Test
    @DisplayName("주소를 비우면서 새 좌표를 함께 보내면 보낸 좌표가 남는다 — 명시한 값이 부수효과보다 우선한다")
    void explicitCoordinatesSurviveAddressClear() {
        Long companyId = register();
        profileRepository.updateProfile(companyId, null, null, null, "서울시 강남구 테헤란로 123", 1.0, 2.0, null);

        profileRepository.updateProfile(companyId, null, null, null, "", 37.5006, 127.0366, null);

        Company company = find(companyId);
        assertThat(company.address()).isNull();
        assertThat(company.latitude()).isEqualTo(37.5006);
        assertThat(company.longitude()).isEqualTo(127.0366);
    }

    private Long register() {
        return registrationRepository.register("NOVA-7K3D", "(주)테크스타트", "123-45-67890", "김서준",
                "owner@company.kr", "010-1234-5678", null, "1-10", "회의록", false,
                LocalDateTime.of(2026, 8, 12, 10, 0));
    }

    private Company find(Long companyId) {
        return companyRepository.findById(companyId).orElseThrow();
    }
}
