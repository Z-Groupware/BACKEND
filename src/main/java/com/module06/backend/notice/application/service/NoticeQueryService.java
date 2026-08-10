package com.module06.backend.notice.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.notice.application.query.GetNoticeListQuery;
import com.module06.backend.notice.application.result.NoticeListResult;
import com.module06.backend.notice.application.usecase.GetNoticeListUseCase;
import com.module06.backend.notice.domain.repository.NoticeQueryRepository;

/*
 * NOTI-01 회사별 공지 목록 조회를 조율하는 애플리케이션 서비스다.
 *
 * 인증 principal에서 전달된 회사 식별자만 저장소 조건으로 사용해 다른 회사 공지가 섞이지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class NoticeQueryService implements GetNoticeListUseCase {

    /* 회사별 활성 공지를 최신순으로 읽는 도메인 저장소다. */
    private final NoticeQueryRepository noticeQueryRepository;

    /* 로그인 사용자의 회사에서 삭제되지 않은 공지를 목록 응답용 결과로 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public NoticeListResult getNotices(GetNoticeListQuery query) {
        /* 인증 회사 식별자가 없거나 올바르지 않으면 저장소 접근 전에 공통 입력 오류로 거절한다. */
        if (query == null || query.companyId() == null || query.companyId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 저장소의 최신순 결과를 목록 화면에 필요한 최소 애플리케이션 모델로 변환한다. */
        List<NoticeListResult.NoticeItem> notices = noticeQueryRepository
                .findActiveNoticesByCompanyId(query.companyId())
                .stream()
                .map(notice -> new NoticeListResult.NoticeItem(
                        notice.noticeId(),
                        notice.title(),
                        notice.createdAt()
                ))
                .toList();

        /* 조회 결과가 없어도 null 대신 빈 notices 배열을 가진 정상 결과를 반환한다. */
        return new NoticeListResult(notices);
    }
}
