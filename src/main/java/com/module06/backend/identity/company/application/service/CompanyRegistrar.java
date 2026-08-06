package com.module06.backend.identity.company.application.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.company.application.command.RegisterCompanyCommand;
import com.module06.backend.identity.company.application.port.out.OwnerAccountPort;
import com.module06.backend.identity.company.domain.repository.CompanyRegistrationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회사와 오너를 한 트랜잭션에 넣는 부분만 떼어 놓은 협력자.
 *
 * <p>{@link CompanyRegistrationService} 안의 {@code private} 메서드로 두지 않은 이유가 있다.
 * 같은 빈 안에서 자기 메서드를 부르면 스프링 프록시를 타지 않아 {@code @Transactional} 이
 * <b>아무 일도 하지 않는다.</b> 경계가 없는데 있는 것처럼 보이는 쪽이 없는 것보다 나쁘다.
 *
 * <p>빈을 나눈 덕에 재시도도 성립한다. 제약 위반이 나면 그 트랜잭션은 rollback-only 로 찍혀
 * 영속성 컨텍스트를 더 쓸 수 없으므로, 같은 트랜잭션 안에서 코드만 바꿔 다시 넣는 것은 불가능하다.
 * 호출자가 이 메서드를 다시 부르면 <b>새 트랜잭션</b>이 열려 깨끗한 상태에서 재시도된다.
 */
@Component
@RequiredArgsConstructor
class CompanyRegistrar {

    private final CompanyRegistrationRepository companyRepository;
    private final OwnerAccountPort ownerAccountPort;

    /**
     * @param code         이번 시도에 쓸 기업코드. 재시도는 호출자가 새 코드로 다시 부른다
     * @param passwordHash 이미 해싱된 값. 평문은 이 경계를 넘지 않는다
     * @return 저장된 회사 id
     */
    @Transactional
    Long persist(RegisterCompanyCommand command, String code, String passwordHash) {
        /*
         * 동의 시각 3종이 같은 값이어야 한다. 컬럼마다 now() 를 부르면 한 번의 동의가 서로 다른
         * 시각으로 남아, 분쟁에서 "언제 동의했나"에 답이 두 개가 된다.
         */
        LocalDateTime now = LocalDateTime.now();

        Long companyId = companyRepository.register(
                code, command.companyName(), command.registrationNo(), command.representativeName(),
                command.managerEmail(), command.managerPhone(),
                command.employeeScale(), command.purpose(), command.agreedMarketing(), now);

        ownerAccountPort.createOwner(companyId, command.representativeName(),
                command.managerEmail(), passwordHash);

        return companyId;
    }
}
