package com.module06.backend.identity.member.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.PendingHandoverType;

import lombok.RequiredArgsConstructor;

/** 구성원 관리 화면(§7)의 읽기 창구 — {@link MemberDirectoryQueryPort} 구현. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberDirectoryQueryAdapter implements MemberDirectoryQueryPort {

    /* 역할 "없음" — 전 회사 공용 시스템 행이다(V2.3.9). company_id 가 NULL 이라 회사 조회로는 안 잡힌다. */
    private static final long ROLE_NONE_ID = 2L;
    private static final String ROLE_NONE_NAME = "없음";

    /* 아래 세 값은 handover 도메인의 HandoverStatus·HandoverType 과 값이 같다. */
    private static final String HANDOVER_REJECTED = "REJECTED";
    private static final String HANDOVER_FINALIZED = "FINALIZED";
    private static final String HANDOVER_TYPE_VACATION = "VACATION";

    private final SpringDataMemberRepository memberRepository;
    private final SpringDataHandoverRefRepository handoverRepository;
    private final SpringDataRoleWriteRepository roleRepository;

    @Override
    public List<MemberRow> findActiveByCompany(Long companyId) {
        List<MemberJpaEntity> members = memberRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        HandoverContext handovers = handoversOf(members);
        return members.stream()
                .map(member -> toRow(member, handovers))
                .toList();
    }

    @Override
    public Optional<MemberRow> findActiveById(Long companyId, Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .filter(member -> member.getCompany().getId().equals(companyId))
                .map(member -> toRow(member, handoversOf(List.of(member))));
    }

    @Override
    public boolean existsActiveEmail(Long companyId, String email) {
        return memberRepository.existsByCompanyIdAndEmailAndDeletedAtIsNull(companyId, email);
    }

    /**
     * "없음"만 시드 id 로 바로 답한다 — 그 행은 어느 회사 소유도 아니라(company_id IS NULL) 회사
     * 조건이 붙은 조회로는 절대 안 잡히는데, 역할을 비우는 유일한 값이라 못 찾으면 사용자가
     * 역할을 되돌릴 방법이 없어진다. 나머지는 회사 안에서 이름으로 찾는다.
     */
    @Override
    public Optional<Long> findRoleIdByLabel(Long companyId, String label) {
        if (ROLE_NONE_NAME.equals(label)) {
            return Optional.of(ROLE_NONE_ID);
        }
        return roleRepository.findFirstByCompanyIdAndName(companyId, label)
                .map(RoleWriteEntity::getId);
    }

    /**
     * handover 를 볼 필요가 있는 구성원(WAITING·VACATION)만 배치로 읽는다. 반려된 행은 어느 쪽에도
     * 쓰이지 않으므로 쿼리에서 미리 떨군다 — 대기 유형(§7-1)과 휴직 기간을 한 번에 읽고 자바에서 가른다.
     */
    private HandoverContext handoversOf(List<MemberJpaEntity> members) {
        List<Long> memberIds = members.stream()
                .filter(member -> member.getStatus() == MemberStatus.WAITING
                        || member.getStatus() == MemberStatus.VACATION)
                .map(MemberJpaEntity::getId)
                .toList();
        if (memberIds.isEmpty()) {
            return HandoverContext.empty();
        }

        List<HandoverRefEntity> handovers =
                handoverRepository.findByWriterMemberIdInAndStatusNot(memberIds, HANDOVER_REJECTED);

        Map<Long, PendingHandoverType> pendingTypes = latestByWriter(handovers,
                handover -> !HANDOVER_FINALIZED.equals(handover.getStatus()),
                handover -> PendingHandoverType.valueOf(handover.getHandoverType()));
        Map<Long, LeaveWindow> leaveWindows = latestByWriter(handovers,
                handover -> HANDOVER_TYPE_VACATION.equals(handover.getHandoverType())
                        && HANDOVER_FINALIZED.equals(handover.getStatus()),
                handover -> new LeaveWindow(toDate(handover.getLeaveStartAt()), toDate(handover.getLeaveEndAt())));

        return new HandoverContext(pendingTypes, leaveWindows);
    }

    /**
     * 한 작성자가 같은 종류의 handover 를 두 개 이상 가질 수 없다는 게 업무 규칙이지만, 데이터가
     * 어긋나 있으면 안전하게 가장 최근 것(id 최대)을 취한다.
     */
    private <T> Map<Long, T> latestByWriter(List<HandoverRefEntity> handovers,
                                            Predicate<HandoverRefEntity> filter,
                                            Function<HandoverRefEntity, T> mapper) {
        return handovers.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        HandoverRefEntity::getWriterMemberId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(HandoverRefEntity::getId)),
                                latest -> mapper.apply(latest.orElseThrow()))));
    }

    private LocalDate toDate(LocalDateTime at) {
        return at == null ? null : at.toLocalDate();
    }

    private MemberRow toRow(MemberJpaEntity member, HandoverContext handovers) {
        TeamRefEntity team = member.getTeam();
        PositionRefEntity position = member.getPosition();
        RoleRefEntity role = member.getRole();
        /*
         * 휴직 기간은 지금 휴직 중인 사람에게만 붙인다. 복직하면 승인 이력은 남아 있어도 화면에는
         * "재직 + 기간 없음"으로 보여야 한다 — 지난 휴직 날짜가 계속 따라다니면 오해를 부른다.
         */
        LeaveWindow leave = member.getStatus() == MemberStatus.VACATION
                ? handovers.leaveWindows().getOrDefault(member.getId(), LeaveWindow.empty())
                : LeaveWindow.empty();
        return new MemberRow(
                member.getId(),
                member.getName(),
                member.getEmail(),
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                position == null ? null : position.getId(),
                position == null ? null : position.getName(),
                role == null ? null : role.getName(),
                member.getAuthority(),
                member.isAdmin(),
                member.getStatus(),
                member.getJoinedOn(),
                handovers.pendingTypes().get(member.getId()),
                leave.startDate(),
                leave.endDate());
    }

    /** 한 번 읽은 handover 배치를 두 용도(대기 유형·휴직 기간)로 나눠 든다. */
    private record HandoverContext(Map<Long, PendingHandoverType> pendingTypes, Map<Long, LeaveWindow> leaveWindows) {

        static HandoverContext empty() {
            return new HandoverContext(Map.of(), Map.of());
        }
    }

    private record LeaveWindow(LocalDate startDate, LocalDate endDate) {

        static LeaveWindow empty() {
            return new LeaveWindow(null, null);
        }
    }
}
