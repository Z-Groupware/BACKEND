package com.module06.backend.identity.company.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.RegisterCompanyCommand;
import com.module06.backend.identity.company.application.dto.CompanyRegistrationResult;
import com.module06.backend.identity.company.application.port.out.AccountMailPort;
import com.module06.backend.identity.company.application.port.out.OwnerAccountPort;
import com.module06.backend.identity.company.domain.policy.CompanyCodeGenerator;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyRegistrationRepository;

@DisplayName("기업 등록 신청 (API 27)")
class CompanyRegistrationServiceTest {

    private static final String EMAIL = "contact@company.com";

    @Test
    @DisplayName("회사와 오너가 만들어지고 계정 정보가 메일로 나간다")
    void registersCompanyAndOwner() {
        RecordingRepository repository = new RecordingRepository();
        RecordingOwner owner = new RecordingOwner();
        RecordingMail mail = new RecordingMail();

        CompanyRegistrationResult result = service(repository, owner, mail).register(command());

        assertThat(result.companyId()).isEqualTo(1L);
        assertThat(result.ownerEmail()).isEqualTo(EMAIL);
        assertThat(owner.createdEmails).containsExactly(EMAIL);
        assertThat(mail.sentTo).containsExactly(EMAIL);
    }

    @Test
    @DisplayName("비밀번호는 응답에 담기지 않는다 — 메일로만 나간다")
    void neverReturnsPassword() {
        RecordingMail mail = new RecordingMail();
        RecordingOwner owner = new RecordingOwner();

        CompanyRegistrationResult result = service(new RecordingRepository(), owner, mail)
                .register(command());

        /*
         * 응답에 실으면 브라우저 개발자도구·프록시 로그에 평문이 남는다. 필드가 없다는 것만으로는
         * 부족해서, 실제로 발송된 비밀번호가 응답 어느 값과도 같지 않은지 본다.
         */
        String sent = mail.sentPassword;
        assertThat(sent).hasSize(12);
        assertThat(result.companyCode()).isNotEqualTo(sent);
        assertThat(result.ownerEmail()).isNotEqualTo(sent);
    }

    @Test
    @DisplayName("오너에게 넘어가는 비밀번호는 해시다 — 평문이 경계를 넘지 않는다")
    void passesHashedPasswordToOwnerPort() {
        RecordingOwner owner = new RecordingOwner();
        RecordingMail mail = new RecordingMail();

        service(new RecordingRepository(), owner, mail).register(command());

        assertThat(owner.passwordHash).isNotEqualTo(mail.sentPassword);
        assertThat(owner.passwordHash).startsWith("$2a$");
    }

    @Test
    @DisplayName("이용약관·개인정보 동의가 없으면 거절한다 — 화면 체크박스만 믿지 않는다")
    void rejectsWhenRequiredTermsNotAgreed() {
        RecordingRepository repository = new RecordingRepository();

        assertThatThrownBy(() -> service(repository).register(
                command(true, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TERMS_NOT_AGREED);

        assertThat(repository.saved).isEmpty();
    }

    @Test
    @DisplayName("사업자등록번호 형식이 틀리면 거절한다")
    void rejectsMalformedRegistrationNo() {
        assertThatThrownBy(() -> service(new RecordingRepository()).register(
                new RegisterCompanyCommand("(주)테크스타트", "1234567890", "홍길동",
                        EMAIL, "010-0000-0000", null, null, true, true, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REGISTRATION_NO_INVALID);
    }

    @Test
    @DisplayName("마케팅 미동의는 시각을 남기지 않는다")
    void marketingAgreementIsOptional() {
        RecordingRepository repository = new RecordingRepository();

        service(repository).register(command());

        assertThat(repository.saved).singleElement()
                .extracting(Saved::agreedMarketing).isEqualTo(false);
    }

    @Test
    @DisplayName("기업코드가 겹치면 새 코드로 다시 넣는다")
    void retriesOnCodeCollision() {
        RecordingRepository repository = new RecordingRepository();
        repository.failFirst = 2;

        CompanyRegistrationResult result = service(repository).register(command());

        /* 세 번 시도했지만 저장된 회사는 하나다 — 앞의 두 번은 제약에 걸려 아무것도 남기지 않는다. */
        assertThat(repository.attempts).isEqualTo(3);
        assertThat(repository.saved).hasSize(1);

        /* 메일에 실리는 코드는 마지막으로 성공한 코드여야 한다. 앞서 버린 코드를 보내면 로그인이 막힌다. */
        assertThat(result.companyCode()).isEqualTo(repository.saved.get(0).code());
    }

    @Test
    @DisplayName("사업자등록번호가 이미 있으면 코드 재시도로 헛돌지 않고 409 로 끝낸다")
    void translatesRegistrationNoDuplicateTo409() {
        RecordingRepository repository = new RecordingRepository();
        repository.failFirst = Integer.MAX_VALUE;
        repository.registrationNoTaken = true;

        assertThatThrownBy(() -> service(repository).register(command()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.REGISTRATION_NO_DUPLICATED);

        /* 한 번 깨지고 바로 끝나야 한다 — 3번 헛돌면 엉뚱한 500 이 나간다. */
        assertThat(repository.saved).isEmpty();
        assertThat(repository.attempts).isEqualTo(1);
    }

    @Test
    @DisplayName("코드를 3번 뽑아도 전부 겹치면 500 으로 드러낸다")
    void givesUpAfterThreeCodeCollisions() {
        RecordingRepository repository = new RecordingRepository();
        repository.failFirst = Integer.MAX_VALUE;

        assertThatThrownBy(() -> service(repository).register(command()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        AuthErrorCode.COMPANY_CODE_GENERATION_FAILED);

        assertThat(repository.attempts).isEqualTo(3);
    }

    @Test
    @DisplayName("메일 발송이 실패해도 회사는 남는다 — 커밋 뒤라서 롤백되지 않는다")
    void mailFailureDoesNotUndoRegistration() {
        RecordingRepository repository = new RecordingRepository();
        AccountMailPort exploding = (to, code, password) -> {
            throw new IllegalStateException("SMTP down");
        };

        assertThatThrownBy(() -> service(repository, new RecordingOwner(), exploding)
                .register(command()))
                .isInstanceOf(IllegalStateException.class);

        /*
         * 이 테스트가 지키는 것은 "회사가 저장된 뒤에 메일이 나간다"는 순서다. 실제 롤백 여부는
         * 트랜잭션 경계(CompanyRegistrar)가 정하며, 여기서는 저장이 이미 끝났음을 확인한다.
         */
        assertThat(repository.saved).hasSize(1);
    }

    /* ── 조립 ─────────────────────────────────────────────────────────────── */

    private CompanyRegistrationService service(RecordingRepository repository) {
        return service(repository, new RecordingOwner(), new RecordingMail());
    }

    private CompanyRegistrationService service(RecordingRepository repository,
                                               OwnerAccountPort owner, AccountMailPort mail) {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        return new CompanyRegistrationService(
                repository,
                new CompanyRegistrar(repository, owner),
                mail,
                CompanyCodeGenerator.secure(),
                PasswordGenerator.secure(),
                encoder);
    }

    private RegisterCompanyCommand command() {
        return command(true, true);
    }

    private RegisterCompanyCommand command(boolean agreedTerms, boolean agreedPrivacy) {
        return new RegisterCompanyCommand("(주)테크스타트", "123-45-67890", "홍길동",
                EMAIL, "010-0000-0000", "6-20", "회의록 자동화 도입 검토",
                agreedTerms, agreedPrivacy, false);
    }

    private record Saved(String code, boolean agreedMarketing) {
    }

    private static final class RecordingRepository implements CompanyRegistrationRepository {

        private final List<Saved> saved = new ArrayList<>();
        private int attempts;
        private int failFirst;
        private boolean registrationNoTaken;

        @Override
        public Long register(String code, String name, String registrationNo,
                             String representativeName, String managerEmail, String managerPhone,
                             String employeeScale, String purpose, boolean agreedMarketing,
                             LocalDateTime now) {
            attempts++;
            if (attempts <= failFirst) {
                throw new DataIntegrityViolationException("UK violation");
            }
            saved.add(new Saved(code, agreedMarketing));
            return (long) saved.size();
        }

        @Override
        public boolean existsByRegistrationNo(String registrationNo) {
            return registrationNoTaken;
        }
    }

    private static final class RecordingOwner implements OwnerAccountPort {

        private final List<String> createdEmails = new ArrayList<>();
        private String passwordHash;

        @Override
        public Long createOwner(Long companyId, String name, String email, String passwordHash) {
            createdEmails.add(email);
            this.passwordHash = passwordHash;
            return 1L;
        }
    }

    private static final class RecordingMail implements AccountMailPort {

        private final List<String> sentTo = new ArrayList<>();
        private String sentPassword;

        @Override
        public void sendAccountIssued(String toEmail, String companyCode, String password) {
            sentTo.add(toEmail);
            sentPassword = password;
        }
    }
}
