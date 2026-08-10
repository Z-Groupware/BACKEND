package com.module06.backend.capture.application.port.out;

import java.util.List;

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

    /*
     * 그 회사 것만 남긴다(배치).
     *
     * <h2>왜 던지지 않고 걸러내는가</h2>
     * 단건 경로는 관문이 404 를 던진다 — 사용자가 그 회의 하나를 열려고 한 것이기 때문이다.
     * 배치는 다르다. 마이페이지가 자기 회의 목록을 보내는데 그중 하나가 남의 회사 것이면
     * (D 쪽 버그이거나 회의가 옮겨진 경우) 던지면 **카드 전체가 사라진다.** 남의 것을 조용히
     * 빼고 나머지를 답하는 것이 화면을 살리는 쪽이고, 유출도 막는다.
     *
     * <h2>왜 default 인가 — 이 포트는 람다로 쓰인다</h2>
     * 검증 하나만 있는 포트라 테스트 20여 곳이 {@code (companyId, meetingId) -> true} 로 넘긴다.
     * 추상 메서드를 하나 더 두면 그 전부가 익명 클래스로 바뀌어야 하고, 이 변경과 관계없는
     * 파일이 스무 개 흔들린다. 그래서 **뜻이 같은 기본 구현**을 둔다 — 단건 검증을 반복하는
     * 것과 결과가 다르지 않다.
     *
     * ⚠ 기본 구현은 id 수만큼 쿼리를 던진다. 실제 어댑터는 **IN 절 하나로 재정의한다**
     * (MeetingAccessJdbcAdapter). 새 구현체를 만들 때 재정의를 빠뜨리면 조용히 N 번 나간다.
     *
     * @return 입력 순서를 보장하지 않는다. 호출자가 id 로 다시 맞춘다
     */
    default List<Long> filterInCompany(long companyId, List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }
        return meetingIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .filter(meetingId -> existsInCompany(companyId, meetingId))
                .toList();
    }
}
