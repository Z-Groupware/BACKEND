package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.port.out.VacationReturnPort;
import com.module06.backend.identity.member.domain.model.MemberStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 휴직 종료일이 지난 계정을 재직으로 되돌린다.
 *
 * <p>이 패키지에 있어야 한다 — {@code SpringDataMemberRepository} 와
 * {@code SpringDataHandoverRefRepository} 가 둘 다 package-private 이다.
 *
 * <p>휴직자가 있을 때만 handover 를 읽는다. 반대로 handover 부터 훑으면 이미 복직한 사람의
 * 지난 휴직 기록까지 매일 읽게 된다 — 대상은 언제나 "지금 VACATION 인 사람" 쪽이 훨씬 적다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional
public class VacationReturnAdapter implements VacationReturnPort {

    private static final String HANDOVER_REJECTED = "REJECTED";
    private static final String HANDOVER_FINALIZED = "FINALIZED";
    private static final String HANDOVER_TYPE_VACATION = "VACATION";

    private final SpringDataMemberRepository memberRepository;
    private final SpringDataHandoverRefRepository handoverRepository;

    @Override
    public List<Long> returnExpiredVacations(LocalDate today) {
        List<MemberJpaEntity> onLeave = memberRepository.findByStatusAndDeletedAtIsNull(MemberStatus.VACATION);
        if (onLeave.isEmpty()) {
            return List.of();
        }

        Map<Long, LocalDate> endByWriter = leaveEndDates(onLeave);

        List<Long> returned = new ArrayList<>();
        for (MemberJpaEntity member : onLeave) {
            LocalDate endDate = endByWriter.get(member.getId());
            if (endDate == null) {
                /*
                 * 데이터 이상이다 — 휴직 상태인데 승인된 휴직 기록이 없다. 종료일을 모르므로
                 * 언제 풀어야 할지도 알 수 없어 건드리지 않는다. 다만 이 사람은 배치로는 영원히
                 * 안 풀리므로(그리고 그 사이 휴직·퇴사 신청도 막힌다) 운영이 볼 수 있게 남긴다.
                 */
                log.warn("휴직 자동 복귀 대상에서 제외 — 승인된 휴직(FINALIZED VACATION) 기록이 없다. memberId={}",
                        member.getId());
                continue;
            }
            if (!endDate.isBefore(today)) {
                continue;                        // 종료일 당일까지는 휴직이다(endDate < today 일 때만 복직)
            }
            member.returnFromVacation();         // 더티 체킹으로 UPDATE 된다
            returned.add(member.getId());
        }
        return List.copyOf(returned);
    }

    /**
     * 휴직자별 종료일. 승인된(FINALIZED) 휴직(VACATION) 중 <b>가장 최근 행(id 최대)</b> 하나만 본다 —
     * {@code MemberDirectoryQueryAdapter.latestByWriter} 와 같은 규칙이다(재신청으로 행이 여러 개일 수 있다).
     *
     * <p>조회는 handover 도메인의 기존 메서드를 그대로 재사용한다. 반려(REJECTED)는 쿼리에서 미리
     * 떨구고 나머지 판정은 자바에서 한다 — 남의 도메인 리포지터리에 이 배치 전용 쿼리를 새로 파지 않는다.
     */
    private Map<Long, LocalDate> leaveEndDates(List<MemberJpaEntity> onLeave) {
        List<Long> ids = onLeave.stream().map(MemberJpaEntity::getId).toList();
        return handoverRepository.findByWriterMemberIdInAndStatusNot(ids, HANDOVER_REJECTED).stream()
                .filter(handover -> HANDOVER_TYPE_VACATION.equals(handover.getHandoverType()))
                .filter(handover -> HANDOVER_FINALIZED.equals(handover.getStatus()))
                .filter(handover -> handover.getLeaveEndAt() != null)
                .collect(Collectors.groupingBy(
                        HandoverRefEntity::getWriterMemberId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(HandoverRefEntity::getId)),
                                /* 시각은 버린다 — "종료일 당일까지"는 날짜 단위 규칙이다. */
                                latest -> latest.orElseThrow().getLeaveEndAt().toLocalDate())));
    }
}
