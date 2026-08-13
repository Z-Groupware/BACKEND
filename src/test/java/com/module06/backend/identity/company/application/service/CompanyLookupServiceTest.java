package com.module06.backend.identity.company.application.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CompanyLookupService")
class CompanyLookupServiceTest {

    @Test
    @DisplayName("소문자·공백이 섞인 코드를 정규화해서 찾는다 — 메일에서 복사하면 공백이 붙는다")
    void normalizesCodeBeforeLookup() {
        RecordingRepository repository = new RecordingRepository(
                Optional.of(new Company(1L, "NOVA-7K3D", "(주)테크스타트", null, null, null, null, null, null, null)));
        CompanyLookupService service = new CompanyLookupService(repository);

        Company found = service.lookup("  nova-7k3d ");

        assertThat(repository.requestedCode).isEqualTo("NOVA-7K3D");
        assertThat(found.name()).isEqualTo("(주)테크스타트");
    }

    @Test
    @DisplayName("없는 코드는 COMPANY_CODE_NOT_FOUND")
    void unknownCodeThrows() {
        CompanyLookupService service = new CompanyLookupService(new RecordingRepository(Optional.empty()));

        assertThatThrownBy(() -> service.lookup("NOPE-0000"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.COMPANY_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("null 코드도 예외 없이 COMPANY_CODE_NOT_FOUND 로 떨어진다 — @NotBlank 를 우회한 호출 대비")
    void nullCodeThrowsNotFound() {
        CompanyLookupService service = new CompanyLookupService(new RecordingRepository(Optional.empty()));

        assertThatThrownBy(() -> service.lookup(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.COMPANY_CODE_NOT_FOUND);
    }

    private static final class RecordingRepository implements CompanyRepository {
        private final Optional<Company> result;
        private String requestedCode;

        private RecordingRepository(Optional<Company> result) {
            this.result = result;
        }

        @Override
        public Optional<Company> findByCode(String code) {
            this.requestedCode = code;
            return result;
        }

        @Override
        public Optional<Company> findById(Long id) {
            return result;
        }

        @Override
        public void lockForUpdate(Long companyId) {
        }
    }
}
