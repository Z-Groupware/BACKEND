package com.module06.backend.identity.company.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.UpdateCompanyCommand;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyProfileRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

@DisplayName("CompanyProfileService (§4-2·4-3)")
class CompanyProfileServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Test
    @DisplayName("기업 기본 정보를 조회한다")
    void getsProfile() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        Company found = service(repository).getProfile(COMPANY_ID);

        assertThat(found.name()).isEqualTo("(주)테크스타트");
        assertThat(found.registrationNo()).isEqualTo("123-45-67890");
    }

    @Test
    @DisplayName("보낸 필드만 바뀐다 — 나머지는 기존 값을 유지한다")
    void partialUpdateKeepsUntouchedFields() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        Company updated = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, "서울시 강남구 테헤란로 123", null));

        assertThat(updated.address()).isEqualTo("서울시 강남구 테헤란로 123");
        assertThat(updated.name()).isEqualTo("(주)테크스타트");
        assertThat(updated.registrationNo()).isEqualTo("123-45-67890");
    }

    @Test
    @DisplayName("사업자등록번호 형식이 틀리면 거절한다")
    void rejectsMalformedRegistrationNo() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        assertThatThrownBy(() -> service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, "1234567890", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REGISTRATION_NO_INVALID);
    }

    @Test
    @DisplayName("다른 회사가 쓰고 있는 사업자등록번호면 거절한다")
    void rejectsDuplicateRegistrationNo() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));
        repository.taken.add("999-99-99999");

        assertThatThrownBy(() -> service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, "999-99-99999", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REGISTRATION_NO_DUPLICATED);
    }

    @Test
    @DisplayName("값을 바꾸지 않은 PATCH는 자기 자신과 겹쳐도 통과한다")
    void samValueDoesNotCountAsDuplicate() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        Company updated = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, "123-45-67890", null, null, null));

        assertThat(updated.registrationNo()).isEqualTo("123-45-67890");
    }

    private CompanyProfileService service(FakeRepository repository) {
        return new CompanyProfileService(repository, repository);
    }

    private Company company(String registrationNo) {
        return new Company(COMPANY_ID, "NOVA-7K3D", "(주)테크스타트", registrationNo,
                "김서준", null, null, null);
    }

    private static final class FakeRepository implements CompanyRepository, CompanyProfileRepository {

        private Company company;
        private final Set<String> taken = new HashSet<>();

        FakeRepository(Company company) {
            this.company = company;
        }

        @Override
        public Optional<Company> findByCode(String code) {
            return Optional.of(company);
        }

        @Override
        public Optional<Company> findById(Long id) {
            return id.equals(company.id()) ? Optional.of(company) : Optional.empty();
        }

        @Override
        public void lockForUpdate(Long companyId) {
        }

        /** {@code taken} 은 "다른 회사가 쓰고 있는" 번호만 담는다 — 자기 자신의 값은 절대 넣지 않는다. */
        @Override
        public boolean existsByRegistrationNoAndIdNot(String registrationNo, Long id) {
            return taken.contains(registrationNo);
        }

        @Override
        public void updateProfile(Long id, String name, String registrationNo, String representativeName,
                                   String address, String mainPhone) {
            company = new Company(id, company.code(), name, registrationNo, representativeName,
                    address, mainPhone, company.onboardedAt());
        }

        @Override
        public void markOnboarded(Long id, LocalDateTime now) {
            company = new Company(id, company.code(), company.name(), company.registrationNo(),
                    company.representativeName(), company.address(), company.mainPhone(), now);
        }
    }
}
