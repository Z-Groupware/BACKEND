package com.module06.backend.identity.company.infrastructure.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    private LocalDateTime onboardedAt;

    /* ── 등록 신청 정보 (V2.2.8 · V2.3.11) ────────────────────────────────── */

    @Column(name = "registration_no", length = 20)
    private String registrationNo;

    @Column(name = "representative_name", length = 50)
    private String representativeName;

    /** 오너 계정의 로그인 아이디가 되는 값이다. */
    @Column(name = "manager_email", length = 255)
    private String managerEmail;

    @Column(name = "manager_phone", length = 30)
    private String managerPhone;

    /** 등록 폼에 없다. 기업 설정(API 08)에서 나중에 채운다. */
    @Column(name = "address", length = 255)
    private String address;

    /** 회사 대표번호. managerPhone(담당자 개인 연락처)과 다르다. */
    @Column(name = "main_phone", length = 30)
    private String mainPhone;

    @Column(name = "employee_scale", length = 20)
    private String employeeScale;

    @Column(name = "purpose", length = 500)
    private String purpose;

    /*
     * 약관 동의를 boolean 이 아니라 시각으로 남긴다.
     *
     * 개인정보 처리방침 동의는 수집·이용의 법적 근거이고, 마케팅 수신은 정보통신망법상 별도 동의가
     * 필요하다. "동의한 적 없다"는 분쟁에서 동의 시각이 유일한 증거다. true/false 로 두면
     * 언제 동의했는지가 사라져 증거로 쓸 수 없다.
     */
    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Column(name = "privacy_agreed_at")
    private LocalDateTime privacyAgreedAt;

    /** 선택 동의. NULL 이면 미동의다. */
    @Column(name = "marketing_agreed_at")
    private LocalDateTime marketingAgreedAt;

    /**
     * 기업 등록 신청으로 회사를 만든다. 승인 절차가 없어 신청이 곧 생성이다.
     *
     * <p>{@code onboardedAt} 을 채우지 않는다 — 조직이 아직 없으므로 온보딩 미완료가 맞다.
     * 이 값이 null 인 동안 오너는 온보딩 화면으로 착지한다.
     *
     * <p>필수 동의 2종의 시각은 호출자가 한 번 읽은 {@code now} 를 그대로 넘겨받는다.
     * 여기서 각각 {@code LocalDateTime.now()} 를 부르면 같은 요청의 동의 시각이 미세하게 어긋난다.
     */
    static CompanyJpaEntity register(String code, String name, String registrationNo,
                                     String representativeName, String managerEmail, String managerPhone,
                                     String employeeScale, String purpose,
                                     boolean agreedMarketing, LocalDateTime now) {
        CompanyJpaEntity company = new CompanyJpaEntity();
        company.code = code;
        company.name = name;
        company.registrationNo = registrationNo;
        company.representativeName = representativeName;
        company.managerEmail = managerEmail;
        company.managerPhone = managerPhone;
        company.employeeScale = employeeScale;
        company.purpose = purpose;
        company.termsAgreedAt = now;
        company.privacyAgreedAt = now;
        company.marketingAgreedAt = agreedMarketing ? now : null;
        return company;
    }

    /** §4-3. 호출자가 이미 기존 값과 병합해 넘긴 값을 그대로 반영한다 — 부분 수정 로직은 여기 없다. */
    void updateProfile(String name, String registrationNo, String representativeName,
                        String address, String mainPhone) {
        this.name = name;
        this.registrationNo = registrationNo;
        this.representativeName = representativeName;
        this.address = address;
        this.mainPhone = mainPhone;
    }

    /** §4-1 온보딩 커밋 마지막 단계. */
    void markOnboarded(LocalDateTime now) {
        this.onboardedAt = now;
    }
}
