package com.module06.backend.identity.company.application.command;

/**
 * 기업 등록 신청. 비로그인 화면에서 대표가 직접 넣는 값이다.
 *
 * <p>{@code authority}·{@code role} 류를 받지 않는다. 오너로 고정이며, 받으면 아무나 권한을 지정해
 * 보낼 수 있다.
 *
 * <p>동의는 boolean 으로 받고 시각은 서버가 찍는다. 클라이언트가 보낸 시각을 믿으면 "동의한 적
 * 없다"는 분쟁에서 증거가 되지 못한다.
 *
 * <p>{@code agreedTerms}·{@code agreedPrivacy} 는 필수, {@code agreedMarketing} 은 선택이다.
 * 화면의 "전체 동의"는 체크박스 편의일 뿐 서버로 오지 않는다.
 *
 * <p>{@code address} 는 선택이며 없으면 NULL 이다. 온보딩을 마치기 전에도 회사 주소가 필요한
 * 화면이 있어 신청 시점에 받아 둔다 — 기업 설정(§4-3)은 오너 로그인 뒤에야 열린다.
 */
public record RegisterCompanyCommand(
        String companyName,
        String registrationNo,
        String representativeName,
        String managerEmail,
        String managerPhone,
        String address,
        String employeeScale,
        String purpose,
        boolean agreedTerms,
        boolean agreedPrivacy,
        boolean agreedMarketing
) {
}
