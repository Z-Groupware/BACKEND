package com.module06.backend.notice.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.notice.application.query.GetNoticeDetailQuery;
import com.module06.backend.notice.application.query.GetNoticeListQuery;
import com.module06.backend.notice.application.result.NoticeDetailResult;
import com.module06.backend.notice.application.result.NoticeListResult;
import com.module06.backend.notice.application.usecase.GetNoticeDetailUseCase;
import com.module06.backend.notice.application.usecase.GetNoticeListUseCase;
import com.module06.backend.notice.domain.repository.NoticeQueryRepository;
import com.module06.backend.notice.exception.NoticeErrorCode;

/*
 * NOTI-01·02 회사별 공지 목록과 상세 조회를 조율하는 애플리케이션 서비스다.
 *
 * 인증 principal에서 전달된 회사 식별자만 저장소 조건으로 사용해 다른 회사 공지가 섞이지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class NoticeQueryService implements GetNoticeListUseCase, GetNoticeDetailUseCase {

    /* 페이지 번호가 생략됐을 때 적용하는 첫 페이지 번호다. */
    private static final int DEFAULT_PAGE = 0;

    /* 페이지 크기가 생략됐을 때 적용하는 기본 공지 개수다. */
    private static final int DEFAULT_SIZE = 10;

    /* 한 페이지에서 허용하는 최대 공지 개수다. */
    private static final int MAX_SIZE = 100;

    /* 회사별 활성 공지를 최신순으로 읽는 도메인 저장소다. */
    private final NoticeQueryRepository noticeQueryRepository;

    /* 로그인 사용자의 회사에서 삭제되지 않은 공지를 페이지 단위 목록 응답용 결과로 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public NoticeListResult getNotices(GetNoticeListQuery query) {
        /* 인증 회사 식별자가 없거나 올바르지 않으면 저장소 접근 전에 공통 입력 오류로 거절한다. */
        if (query == null || query.companyId() == null || query.companyId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 내부 호출에서 페이지 값이 생략돼도 REST 기본값(page=0, size=10)과 동일하게 처리한다. */
        int page = query.page() == null ? DEFAULT_PAGE : query.page();
        int size = query.size() == null ? DEFAULT_SIZE : query.size();

        /* 음수 페이지나 1~100 범위를 벗어난 페이지 크기는 공통 입력 오류로 거절한다. */
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 저장소가 반환한 현재 페이지를 목록 화면에 필요한 최소 애플리케이션 모델로 변환한다. */
        NoticeQueryRepository.NoticePage noticePage = noticeQueryRepository
                .findActiveNoticesByCompanyId(query.companyId(), page, size);
        List<NoticeListResult.NoticeItem> notices = noticePage.notices().stream()
                .map(notice -> new NoticeListResult.NoticeItem(
                        notice.noticeId(),
                        notice.title(),
                        notice.createdAt()
                ))
                .toList();

        /* 조회 결과가 없어도 null 대신 빈 notices 배열과 페이지 메타를 가진 정상 결과를 반환한다. */
        return new NoticeListResult(
                notices,
                new NoticeListResult.Page(page, size, noticePage.totalElements(), noticePage.totalPages())
        );
    }

    /* 같은 회사의 삭제되지 않은 공지 한 건을 상세 결과로 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public NoticeDetailResult getNotice(GetNoticeDetailQuery query) {
        /* 인증 회사와 경로 공지 식별자가 양수인지 저장소 접근 전에 검증한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.noticeId() == null
                || query.noticeId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 미존재·삭제·타 회사 공지를 구분하지 않는 회사 범위 저장소 계약으로 조회한다. */
        NoticeQueryRepository.NoticeDetailSnapshot notice = noticeQueryRepository
                .findActiveNotice(query.companyId(), query.noticeId())
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));

        /* 저장소 상세 스냅샷을 프레젠테이션 계층에 전달할 애플리케이션 결과로 변환한다. */
        return new NoticeDetailResult(
                notice.noticeId(),
                notice.title(),
                notice.content(),
                notice.createdAt(),
                notice.updatedAt()
        );
    }
}
