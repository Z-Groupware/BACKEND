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
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, "서울시 강남구 테헤란로 123", null, null, null));

        assertThat(updated.address()).isEqualTo("서울시 강남구 테헤란로 123");
        assertThat(updated.name()).isEqualTo("(주)테크스타트");
        assertThat(updated.registrationNo()).isEqualTo("123-45-67890");
    }

    @Test
    @DisplayName("동시에 다른 필드를 고친 PATCH끼리는 서로의 변경을 지우지 않는다")
    void concurrentPartialUpdatesDoNotClobberEachOther() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        /*
         * 두 PATCH가 같은 낡은 스냅샷을 각자 읽었다고 가정해도(동시성 시나리오), 서비스는 이제
         * 그 스냅샷과 병합하지 않고 null 을 그대로 넘긴다 — 나중에 실행되는 쪽이 먼저 커밋된
         * address 변경을 되돌리면 이 테스트가 잡아낸다.
         */
        service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, "서울시 강남구 테헤란로 123", null, null, null));
        Company afterSecondPatch = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, "김서준", null, null, null, null));

        assertThat(afterSecondPatch.address()).isEqualTo("서울시 강남구 테헤란로 123");
        assertThat(afterSecondPatch.representativeName()).isEqualTo("김서준");
    }

    @Test
    @DisplayName("좌표만 보내면 좌표만 바뀐다 — 주소는 그대로다")
    void updatesCoordinatesWithoutTouchingAddress() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));
        service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, "서울시 강남구 테헤란로 123", null, null, null));

        Company updated = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, null, 37.5006, 127.0366, null));

        assertThat(updated.latitude()).isEqualTo(37.5006);
        assertThat(updated.longitude()).isEqualTo(127.0366);
        assertThat(updated.address()).isEqualTo("서울시 강남구 테헤란로 123");
    }

    @Test
    @DisplayName("주소만 고치면 이미 찍혀 있던 좌표는 지워지지 않는다")
    void addressOnlyUpdateKeepsCoordinates() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));
        service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, null, 37.5006, 127.0366, null));

        Company updated = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, "서울시 강남구 테헤란로 45", null, null, null));

        assertThat(updated.address()).isEqualTo("서울시 강남구 테헤란로 45");
        assertThat(updated.latitude()).isEqualTo(37.5006);
        assertThat(updated.longitude()).isEqualTo(127.0366);
    }

    @Test
    @DisplayName("주소를 빈 값으로 보내면 주소와 좌표가 함께 지워진다 — 주소 없는 핀은 가리킬 곳이 없다")
    void blankAddressClearsAddressAndCoordinates() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));
        service(repository).updateProfile(new UpdateCompanyCommand(
                COMPANY_ID, null, null, null, "서울시 강남구 테헤란로 123", 37.5006, 127.0366, null));

        Company cleared = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, "  ", null, null, null));

        assertThat(cleared.address()).isNull();
        assertThat(cleared.latitude()).isNull();
        assertThat(cleared.longitude()).isNull();
    }

    @Test
    @DisplayName("주소를 비우면서 새 좌표를 함께 보내면 보낸 좌표가 남는다 — 명시한 값이 부수효과보다 우선한다")
    void explicitCoordinatesSurviveAddressClear() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        Company updated = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, null, null, "", 37.5006, 127.0366, null));

        assertThat(updated.address()).isNull();
        assertThat(updated.latitude()).isEqualTo(37.5006);
        assertThat(updated.longitude()).isEqualTo(127.0366);
    }

    @Test
    @DisplayName("사업자등록번호 형식이 틀리면 거절한다")
    void rejectsMalformedRegistrationNo() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        assertThatThrownBy(() -> service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, "1234567890", null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REGISTRATION_NO_INVALID);
    }

    @Test
    @DisplayName("다른 회사가 쓰고 있는 사업자등록번호면 거절한다")
    void rejectsDuplicateRegistrationNo() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));
        repository.taken.add("999-99-99999");

        assertThatThrownBy(() -> service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, "999-99-99999", null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REGISTRATION_NO_DUPLICATED);
    }

    @Test
    @DisplayName("값을 바꾸지 않은 PATCH는 자기 자신과 겹쳐도 통과한다")
    void samValueDoesNotCountAsDuplicate() {
        FakeRepository repository = new FakeRepository(company("123-45-67890"));

        Company updated = service(repository).updateProfile(
                new UpdateCompanyCommand(COMPANY_ID, null, "123-45-67890", null, null, null, null, null));

        assertThat(updated.registrationNo()).isEqualTo("123-45-67890");
    }

    private CompanyProfileService service(FakeRepository repository) {
        return new CompanyProfileService(repository, repository);
    }

    private Company company(String registrationNo) {
        return new Company(COMPANY_ID, "NOVA-7K3D", "(주)테크스타트", registrationNo,
                "김서준", null, null, null, null, null);
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

        /**
         * 실제 {@code CompanyJpaEntity.updateProfile} 과 같다 — null 인자는 기존 값을 그대로 두고,
         * 빈 주소는 주소·좌표를 함께 지운다.
         */
        @Override
        public void updateProfile(Long id, String name, String registrationNo, String representativeName,
                                   String address, Double latitude, Double longitude, String mainPhone) {
            boolean clearingAddress = address != null && address.isBlank();
            String mergedAddress = address == null ? company.address() : (clearingAddress ? null : address);
            Double mergedLatitude = clearingAddress ? null : company.latitude();
            Double mergedLongitude = clearingAddress ? null : company.longitude();
            company = new Company(id, company.code(),
                    name != null ? name : company.name(),
                    registrationNo != null ? registrationNo : company.registrationNo(),
                    representativeName != null ? representativeName : company.representativeName(),
                    mergedAddress,
                    latitude != null ? latitude : mergedLatitude,
                    longitude != null ? longitude : mergedLongitude,
                    mainPhone != null ? mainPhone : company.mainPhone(),
                    company.onboardedAt());
        }

        @Override
        public void markOnboarded(Long id, LocalDateTime now) {
            company = new Company(id, company.code(), company.name(), company.registrationNo(),
                    company.representativeName(), company.address(), company.latitude(), company.longitude(),
                    company.mainPhone(), now);
        }
    }
}
