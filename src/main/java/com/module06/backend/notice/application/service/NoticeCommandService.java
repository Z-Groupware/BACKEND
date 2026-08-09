package com.module06.backend.notice.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.notice.application.command.CreateNoticeCommand;
import com.module06.backend.notice.application.result.NoticeCreationResult;
import com.module06.backend.notice.application.usecase.CreateNoticeUseCase;
import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.domain.repository.NoticeCommandRepository;
import com.module06.backend.notice.exception.NoticeErrorCode;

/* NOTI-03 공지 작성의 입력·권한·저장을 조율하는 애플리케이션 서비스다. */
@Service
@RequiredArgsConstructor
public class NoticeCommandService implements CreateNoticeUseCase {

    /* 공지 도메인 모델을 notice 테이블에 저장하는 명령 저장소다. */
    private final NoticeCommandRepository noticeCommandRepository;

    /* 인증 회사에 OWNER·ADMIN 작성자의 공지를 저장하고 생성 식별자를 반환한다. */
    @Override
    @Transactional
    public NoticeCreationResult createNotice(CreateNoticeCommand command) {
        /* Controller를 거치지 않는 내부 호출에서도 인증 식별자와 제목·본문 계약을 검증한다. */
        validateRequiredValues(command);

        /* 애노테이션 인가를 우회한 내부 호출도 OWNER·ADMIN이 아니면 공지 작성을 거절한다. */
        if (!isNoticeManager(command.requesterRole())) {
            throw new BusinessException(NoticeErrorCode.NOTICE_MANAGEMENT_FORBIDDEN);
        }

        /* 제목 가장자리 공백만 제거하고 본문의 개행과 원문은 그대로 보존한다. */
        Notice notice = Notice.create(
                command.companyId(),
                command.requesterMemberId(),
                command.title().trim(),
                command.content()
        );

        /* 데이터베이스 생성 식별자가 반영된 공지를 저장 결과로 받는다. */
        Notice savedNotice = noticeCommandRepository.save(notice);

        /* 전체 공지 대신 작성 직후 필요한 생성 식별자만 외부 결과로 반환한다. */
        return new NoticeCreationResult(savedNotice.getId());
    }

    /* 회사·작성자·역할과 제목·본문이 NOTI-03 계약에 맞는지 확인한다. */
    private void validateRequiredValues(CreateNoticeCommand command) {
        /* 인증 식별자·역할과 공지 필드의 누락 또는 공백은 NT-003 입력 오류로 거절한다. */
        if (command == null
                || command.companyId() == null
                || command.companyId() <= 0L
                || command.requesterMemberId() == null
                || command.requesterMemberId() <= 0L
                || command.requesterRole() == null
                || command.requesterRole().isBlank()
                || command.title() == null
                || command.title().isBlank()
                || command.content() == null
                || command.content().isBlank()
                || command.title().length() > 200) {
            throw new BusinessException(NoticeErrorCode.INVALID_NOTICE_INPUT);
        }
    }

    /* 인증 역할이 공지 작성 권한을 가진 OWNER 또는 ADMIN인지 판단한다. */
    private boolean isNoticeManager(String role) {
        /* 명세에 확정된 두 역할만 쓰기 권한으로 인정한다. */
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }
}
