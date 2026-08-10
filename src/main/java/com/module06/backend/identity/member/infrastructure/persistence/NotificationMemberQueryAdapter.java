package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.notification.application.port.out.CompanyMemberQueryPort;

/*
 * notification이 요청한 회사 구성원 조회 계약을 identity의 정본 member 데이터로 구현한다.
 * 다른 도메인에는 MemberJpaEntity를 노출하지 않고 알림 수신에 필요한 식별자만 반환한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationMemberQueryAdapter implements CompanyMemberQueryPort {

    /* 회사와 소프트 삭제 조건을 데이터베이스 조회에 적용하는 identity 저장소다. */
    private final SpringDataMemberRepository springDataMemberRepository;

    /* 퇴사하지 않은 회사 구성원을 조회해 안정적인 식별자 목록으로 변환한다. */
    @Override
    public List<Long> findActiveMemberIds(Long companyId) {
        /* 중복을 제거하고 식별자순으로 정렬해 회원별 알림 처리 순서를 결정적으로 유지한다. */
        return springDataMemberRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(MemberJpaEntity::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
