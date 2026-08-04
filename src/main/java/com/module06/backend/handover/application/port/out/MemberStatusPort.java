package com.module06.backend.handover.application.port.out;

/**
 * 인수인계·휴직 → 계정/조직(B 도메인, 담당 윤종호) 크로스도메인 계약.
 *
 * <p>컨벤션 §1.1에 따라 인수인계 도메인은 {@code MemberStatus} enum을 직접 참조하지 않고,
 * <b>의도(intent) 기반</b> 메서드로만 멤버 생애주기를 요청한다. 실제 상태 enum·전이 규칙·감사는
 * 계정 도메인이 소유한다.
 *
 * <pre>
 * ACTIVE ─[신청 즉시 toWaiting]→ WAITING ─[VACATION 최종승인 toVacation]→ VACATION
 *                                   │        └[OFFBOARDING 최종승인 offboard]→ DELETED(soft)
 *                                   └────────[반려 restoreActive]────────→ ACTIVE
 * </pre>
 */
public interface MemberStatusPort {

    /** 인수인계서 생성(상신) 즉시: 작성자를 대기 상태로. (ACTIVE → WAITING) */
    void toWaiting(Long memberId);

    /** 휴직 최종승인: 대기 → 휴직. (WAITING → VACATION) */
    void toVacation(Long memberId);

    /**
     * 오프보딩 최종승인: 계정 soft delete + 권한·자원 회수 + '잔여 0' 감사.
     * 물리 삭제는 하지 않는다(데이터 보존, 하드삭제는 추후). (→ DELETED)
     */
    void offboard(Long memberId);

    /** 반려: 작성자 상태를 재직으로 원복. (WAITING → ACTIVE) */
    void restoreActive(Long memberId);
}
