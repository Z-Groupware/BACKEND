package com.module06.backend.identity.company.infrastructure.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * {@code @DynamicUpdate} — §4-3 부분 수정이 실제로 바뀐 컬럼만 UPDATE에 싣게 한다. 기본
 * 동작(모든 컬럼을 매번 UPDATE)이면 동시에 서로 다른 필드를 고친 두 PATCH 중 나중에 flush된
 * 쪽이 자기가 안 건드린 필드까지 자기 스냅샷 값으로 덮어써 먼저 커밋된 변경을 지운다.
 */
@Entity
@Table(name = "company")
@DynamicUpdate
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

    /**
     * 등록 신청에서 선택으로 받고, 기업 설정(API 08)에서 고칠 수 있다. 신청 화면이 지도로 위치를
     * 고르게 하는데 온보딩을 마치기 전까지는 기업 설정이 열리지 않아, 신청 때 받지 않으면 그 값이
     * 갈 곳이 없다. 지도가 뜨지 않는 환경에서는 비어 오므로 NULL 을 허용한다.
     */
    @Column(name = "address", length = 255)
    private String address;

    /**
     * 지도에서 고른 회사 위치(V2.3.22). address 와 짝이지만 서로를 강제하지 않는다 — 지도 SDK 가
     * 뜨지 않는 환경에서는 주소만 채워지고 좌표는 비어 있다.
     */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

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
                                     String address, String employeeScale, String purpose,
                                     boolean agreedMarketing, LocalDateTime now) {
        CompanyJpaEntity company = new CompanyJpaEntity();
        company.code = code;
        company.name = name;
        company.registrationNo = registrationNo;
        company.representativeName = representativeName;
        company.managerEmail = managerEmail;
        company.managerPhone = managerPhone;
        company.address = address;
        company.employeeScale = employeeScale;
        company.purpose = purpose;
        company.termsAgreedAt = now;
        company.privacyAgreedAt = now;
        company.marketingAgreedAt = agreedMarketing ? now : null;
        return company;
    }

    /**
     * §4-3. null 인 인자는 건드리지 않는다 — 부분 수정 자체를 여기서 구현한다.
     *
     * <p>호출자가 미리 현재 값과 병합해서 넘기지 않는 이유: 그 병합은 이 메서드가 실행되기 전에
     * 읽은 스냅샷을 기준으로 하므로, 동시에 다른 필드를 고친 PATCH가 그 사이 커밋되면 이 호출이
     * 그 변경을 자기 스냅샷 값으로 되돌려 버린다(lost update). 이 엔티티가 지금 이 순간 실제로
     * 갖고 있는 값 위에 "바뀐 필드만" 얹으면 그 문제가 없다 — {@code @DynamicUpdate}가 실제로
     * 대입된 필드만 UPDATE에 싣는 것과 짝을 이룬다.
     */
    void updateProfile(String name, String registrationNo, String representativeName,
                        String address, Double latitude, Double longitude, String mainPhone) {
        if (name != null) {
            this.name = name;
        }
        if (registrationNo != null) {
            this.registrationNo = registrationNo;
        }
        if (representativeName != null) {
            this.representativeName = representativeName;
        }
        if (address != null) {
            this.address = address;
        }
        /*
         * 좌표는 각각 독립으로 얹는다. "주소를 바꿨으니 좌표도 같이 와야 한다"를 여기서 강제하지
         * 않는다 — 부분 수정 계약상 주소만 손으로 고치는 요청이 정상이고, 그때 좌표를 지우면
         * 지도에 찍혀 있던 위치가 소리 없이 사라진다. 두 값을 맞추는 책임은 화면에 있다.
         */
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
        if (mainPhone != null) {
            this.mainPhone = mainPhone;
        }
    }

    /** §4-1 온보딩 커밋 마지막 단계. */
    void markOnboarded(LocalDateTime now) {
        this.onboardedAt = now;
    }
}
