package com.module06.backend.identity.company.domain.repository;

import java.time.LocalDateTime;

/**
 * 기업 등록의 쓰기 경로. {@link CompanyRepository}(읽기)와 나눠 둔다.
 *
 * <p>한 인터페이스에 합치지 않는 이유 — 로그인 1단계는 {@code findByCode} 하나만 필요한데, 거기에
 * 쓰기 메서드가 붙으면 그 경로를 쓰는 모든 곳이 회사를 만들 수 있는 능력을 함께 받는다.
 * 나눠 두면 무엇을 할 수 있는 자리인지가 타입에 드러난다.
 */
public interface CompanyRegistrationRepository {

    /**
     * 등록 신청으로 회사를 만든다.
     *
     * <p>{@code code} 중복을 미리 확인하지 않는다 — 조회 후 INSERT 는 동시 요청에서 둘 다 통과한다.
     * {@code UK_COMPANY_CODE} 위반이 그대로 올라오게 두고 호출자가 잡아 다시 뽑는다.
     * {@code registration_no} 도 같다({@code UK_COMPANY_REGISTRATION_NO}).
     *
     * @param address 회사 주소. 선택이라 NULL 일 수 있다 — 주소 없음은 빈 문자열이 아니라 NULL 이다
     * @param now 약관 동의 3종이 공유할 시각. 컬럼마다 따로 찍으면 한 번의 동의가 서로 다른 시각이 된다
     * @return 저장된 회사의 id
     */
    Long register(String code, String name, String registrationNo, String representativeName,
                  String managerEmail, String managerPhone, String address,
                  String employeeScale, String purpose,
                  boolean agreedMarketing, LocalDateTime now);

    /** 사업자등록번호 선점 여부. 안내용이고, 최종 방어선은 UNIQUE 제약이다. */
    boolean existsByRegistrationNo(String registrationNo);
}
