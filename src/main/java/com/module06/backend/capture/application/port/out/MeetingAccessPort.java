package com.module06.backend.capture.application.port.out;

/*
 * "이 회의가 그 회사 것인가"를 묻는 포트다.
 *
 * <h2>왜 별도 포트인가</h2>
 * 캡처 파이프라인의 공개 API 18개는 전부 {@code /api/meetings/{meetingId}/...} 형태이고,
 * companyId 는 토큰에서만 온다. 즉 **모든 API 가 같은 검증을 필요로 한다.** 각 유스케이스가
 * 각자 조회 조건에 회사를 끼워 넣는 방식으로 두면 언젠가 한 곳이 빠지고, 그 API 만 조용히
 * 뚫린다 — 실제로 CAP-06 이 그렇게 뚫려 있었다(CodeRabbit PR #84 · 이슈 #100).
 *
 * <h2>왜 meeting 을 JPA 로 매핑하지 않는가</h2>
 * meeting 은 D(회의) 도메인 소유다. 여기서 엔티티를 새로 매핑하면 같은 테이블에 매핑이 셋이
 * 되는데, 그게 정확히 2026-08-05 에 테스트 9건을 깨뜨린 사고다(MeetingJpaEntity 와
 * MeetingReferenceEntity 가 같은 meeting 을 매핑하면서 스키마 생성이 환경에 따라 갈렸다).
 * 그래서 읽기 쿼리 하나로 끝낸다({@code MeetingParticipantJdbcProvider} 와 같은 방식).
 */
public interface MeetingAccessPort {

    /*
     * 그 회사에 속한 회의가 존재하는가.
     *
     * 존재하지 않는 회의와 다른 회사의 회의를 **구분하지 않는다.** 호출자가 404 로 응답하므로,
     * 구분하면 "이 회의는 있지만 당신 것이 아니다"가 새어 나가 회의 id 를 훑어 남의 회사
     * 회의 개수를 셀 수 있다.
     */
    boolean existsInCompany(long companyId, long meetingId);
}
