package com.module06.backend.notice.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.module06.backend.notice.domain.repository.NoticeQueryRepository;
import com.module06.backend.notice.infrastructure.persistence.entity.NoticeJpaEntity;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository.NoticeListProjection;

/* NOTI-01·02 회사별 활성 공지 목록과 상세 계약을 Spring Data JPA로 구현하는 어댑터다. */
@Component
@RequiredArgsConstructor
public class NoticeQueryPersistenceAdapter implements NoticeQueryRepository {

    /* notice 테이블에 회사·삭제·정렬 조건을 적용하는 기술 저장소다. */
    private final SpringDataNoticeRepository springDataNoticeRepository;

    /* 회사의 활성 공지를 최신 생성 시각과 식별자 순으로 한 페이지만 읽어 목록 스냅샷으로 변환한다. */
    @Override
    public NoticePage findActiveNoticesByCompanyId(Long companyId, int page, int size) {
        /* 같은 생성 시각에도 결과가 흔들리지 않도록 식별자를 두 번째 내림차순 키로 사용한다. */
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        /* 파생 쿼리가 적용한 테넌트·소프트 삭제 조건을 유지하며 요청한 페이지만 조회한다. */
        Page<NoticeListProjection> noticePage = springDataNoticeRepository
                .findAllProjectedByCompanyIdAndDeletedAtIsNull(companyId, pageRequest);

        List<NoticeListSnapshot> notices = noticePage.getContent().stream()
                .map(this::toSnapshot)
                .toList();

        return new NoticePage(notices, noticePage.getTotalElements(), noticePage.getTotalPages());
    }

    /* 회사와 식별자가 일치하는 활성 공지 한 건을 상세 읽기 모델로 조회한다. */
    @Override
    public Optional<NoticeDetailSnapshot> findActiveNotice(Long companyId, Long noticeId) {
        /* 삭제·타 회사 공지를 파생 쿼리에서 제외하고 조회된 엔티티만 상세 스냅샷으로 변환한다. */
        return springDataNoticeRepository
                .findByIdAndCompanyIdAndDeletedAtIsNull(noticeId, companyId)
                .map(this::toDetailSnapshot);
    }

    /* 닫힌 JPA 프로젝션 한 건을 목록 조회 전용 읽기 모델로 변환한다. */
    private NoticeListSnapshot toSnapshot(NoticeListProjection notice) {
        /* 목록에서 사용하지 않는 본문과 작성자 정보는 저장소 경계 밖으로 전달하지 않는다. */
        return new NoticeListSnapshot(notice.getId(), notice.getTitle(), notice.getCreatedAt());
    }

    /* JPA 엔티티 한 건을 공지 상세 조회 전용 읽기 모델로 변환한다. */
    private NoticeDetailSnapshot toDetailSnapshot(NoticeJpaEntity notice) {
        /* 상세 화면이 사용하는 제목·본문·생성 및 수정 시각만 저장소 경계 밖으로 전달한다. */
        return new NoticeDetailSnapshot(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
