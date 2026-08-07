package com.module06.backend.identity.company.application.service;

import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.RegisterCompanyCommand;
import com.module06.backend.identity.company.application.dto.CompanyRegistrationResult;
import com.module06.backend.identity.company.application.port.out.AccountMailPort;
import com.module06.backend.identity.company.application.usecase.RegisterCompanyUseCase;
import com.module06.backend.identity.company.domain.policy.CompanyCodeGenerator;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyRegistrationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 기업 등록 신청(API 27). 운영자 승인 절차가 없어 신청 하나가 회사 생성·오너 계정 발급·메일 발송을
 * 전부 떠맡는다.
 *
 * <p>이 클래스의 핵심은 <b>트랜잭션 경계</b>다.
 *
 * <pre>
 *   검증 → 비밀번호 생성
 *   [TX] company INSERT → 오너 member INSERT   ← CompanyRegistrar (코드 충돌 시 새 TX 로 재시도)
 *   메일 발송
 * </pre>
 *
 * <p>메일이 커밋 밖인 이유 — 메일 서버 타임아웃으로 방금 만든 회사가 롤백되면 복구 경로가 없다.
 * 회사는 정상적으로 만들어졌고 메일만 못 보낸 것이므로, 코드는 이미 DB 에 있고 재발송으로 해결한다.
 *
 * <p>반대로 회사와 오너는 반드시 같은 트랜잭션이다({@link CompanyRegistrar}). 나뉘면 오너 없는
 * 회사가 남고, 그 회사는 아무도 로그인할 수 없어 되살릴 방법이 없다.
 */
@Service
@RequiredArgsConstructor
public class CompanyRegistrationService implements RegisterCompanyUseCase {

    /** 국세청 진위확인은 외부 키가 필요해 제외한다. 형식만 본다. */
    private static final Pattern REGISTRATION_NO = Pattern.compile("\\d{3}-\\d{2}-\\d{5}");

    /** 코드 재생성 횟수. 32^8 에서 3회 연속 충돌은 무작위 원천 고장을 뜻하므로 덮지 않고 드러낸다. */
    private static final int CODE_RETRY = 3;

    private final CompanyRegistrationRepository companyRepository;
    private final CompanyRegistrar registrar;
    private final AccountMailPort accountMailPort;
    private final CompanyCodeGenerator codeGenerator;
    private final PasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;

    @Override
    public CompanyRegistrationResult register(RegisterCompanyCommand command) {
        validate(command);

        String password = passwordGenerator.generate();
        String passwordHash = passwordEncoder.encode(password);

        Registered registered = persistWithUniqueCode(command, passwordHash);

        /*
         * 커밋 뒤다. persistWithUniqueCode 가 반환됐다는 것은 회사·오너가 확정됐다는 뜻이고,
         * 여기서 무슨 일이 생겨도 그 사실은 바뀌지 않는다.
         */
        accountMailPort.sendAccountIssued(command.managerEmail(), registered.code(), password);

        return new CompanyRegistrationResult(
                registered.companyId(), registered.code(), command.managerEmail());
    }

    /**
     * 코드가 겹치면 새 코드로 다시 넣는다. 시도마다 트랜잭션이 새로 열린다.
     *
     * <p>미리 조회해 비어 있는지 확인하고 INSERT 하지 않는다 — 확인과 INSERT 사이에 다른 요청이 같은
     * 코드를 쓸 수 있어 확인이 통과해도 깨진다. {@code UK_COMPANY_CODE} 가 최종 방어선이고 여기서는
     * 그 위반을 잡아 다시 뽑기만 한다.
     *
     * <p>{@code registration_no} 중복도 같은 예외로 올라온다. 구분하지 않으면 사업자번호 때문에 깨진
     * 요청이 코드 충돌로 오해되어 3번 헛돈 뒤 엉뚱한 500 이 나간다. 그래서 위반이 나면 사업자번호가
     * 선점됐는지 먼저 확인하고, 그쪽이면 409 로 즉시 끝낸다.
     */
    private Registered persistWithUniqueCode(RegisterCompanyCommand command, String passwordHash) {
        DataIntegrityViolationException lastViolation = null;

        for (int attempt = 0; attempt < CODE_RETRY; attempt++) {
            String code = codeGenerator.generate();
            try {
                return new Registered(registrar.persist(command, code, passwordHash), code);
            } catch (DataIntegrityViolationException e) {
                lastViolation = e;
                if (companyRepository.existsByRegistrationNo(command.registrationNo())) {
                    throw new BusinessException(AuthErrorCode.REGISTRATION_NO_DUPLICATED, e);
                }
            }
        }
        throw new BusinessException(AuthErrorCode.COMPANY_CODE_GENERATION_FAILED, lastViolation);
    }

    /**
     * 화면 체크박스만 믿지 않는다. API 를 직접 부르면 동의 없이 통과하므로 서버가 다시 본다.
     *
     * <p>사업자번호는 형식만 검사한다. 진위확인은 외부 키 발급이 필요해 범위에서 뺐고, 사업자등록번호가
     * 사실상 공개 정보라 이 검사만으로는 실질적 관문이 되지 못한다 — 운영으로 가면 메일 인증이나
     * 진위확인 중 하나가 반드시 필요하다.
     */
    private void validate(RegisterCompanyCommand command) {
        if (!command.agreedTerms() || !command.agreedPrivacy()) {
            throw new BusinessException(AuthErrorCode.TERMS_NOT_AGREED);
        }
        if (command.registrationNo() == null
                || !REGISTRATION_NO.matcher(command.registrationNo()).matches()) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_NO_INVALID);
        }
    }

    /** 재시도 루프가 확정한 회사 id 와 그때 쓰인 코드. 코드는 메일에 실어야 해서 함께 들고 나온다. */
    private record Registered(Long companyId, String code) {
    }
}
