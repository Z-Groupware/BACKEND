package com.module06.backend.notice.infrastructure.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.notice.domain.repository.NoticeQueryRepository;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository.NoticeListProjection;

/* NOTI-01 회사별 활성 공지 목록 계약을 Spring Data JPA로 구현하는 어댑터다. */
@Component
@RequiredArgsConstructor
public class NoticeQueryPersistenceAdapter implements NoticeQueryRepository {

    /* notice 테이블에 회사·삭제·정렬 조건을 적용하는 기술 저장소다. */
    private final SpringDataNoticeRepository springDataNoticeRepository;

    /* 회사의 활성 공지를 최신 생성 시각과 식별자 순으로 읽어 목록 스냅샷으로 변환한다. */
    @Override
    public List<NoticeListSnapshot> findActiveNoticesByCompanyId(Long companyId) {
        /* 파생 쿼리가 적용한 테넌트·소프트 삭제·정렬 조건을 유지하며 최소 필드만 반환한다. */
        return springDataNoticeRepository
                .findAllProjectedByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(companyId)
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    /* 닫힌 JPA 프로젝션 한 건을 목록 조회 전용 읽기 모델로 변환한다. */
    private NoticeListSnapshot toSnapshot(NoticeListProjection notice) {
        /* 목록에서 사용하지 않는 본문과 작성자 정보는 저장소 경계 밖으로 전달하지 않는다. */
        return new NoticeListSnapshot(notice.getId(), notice.getTitle(), notice.getCreatedAt());
    }
}
