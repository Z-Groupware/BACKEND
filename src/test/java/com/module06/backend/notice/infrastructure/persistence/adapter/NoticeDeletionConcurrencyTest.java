package com.module06.backend.notice.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.notice.application.command.DeleteNoticeCommand;
import com.module06.backend.notice.application.service.NoticeCommandService;
import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.infrastructure.persistence.entity.NoticeJpaEntity;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository;

/* NOTI-05 동시 삭제가 한 공지의 활성 상태를 두 번 소비하지 않는지 실제 트랜잭션으로 검증한다. */
@SpringBootTest
@DisplayName("공지 삭제 동시성")
class NoticeDeletionConcurrencyTest {

    /* 별도 스레드마다 트랜잭션을 여는 실제 공지 명령 서비스다. */
    @Autowired
    private NoticeCommandService noticeCommandService;

    /* 테스트 공지 저장과 최종 삭제 상태 확인에 사용하는 기술 저장소다. */
    @Autowired
    private SpringDataNoticeRepository springDataNoticeRepository;

    /* 준비 데이터를 테스트 스레드 실행 전에 별도 트랜잭션으로 확정한다. */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /* 두 삭제 요청이 함께 사용할 활성 공지 식별자다. */
    private Long noticeId;

    /* 각 테스트가 커밋된 활성 공지 한 건에서 시작하도록 데이터베이스를 초기화한다. */
    @BeforeEach
    void setUpActiveNotice() {
        /* 준비 트랜잭션 안에서 이전 공지를 지우고 동시 삭제 대상 한 건을 저장한다. */
        noticeId = transactionTemplate.execute(status -> {
            /* 이전 테스트 데이터가 잠금 결과에 영향을 주지 않도록 공지 테이블을 비운다. */
            springDataNoticeRepository.deleteAll();

            /* 회사 10의 OWNER가 삭제할 활성 공지를 생성해 식별자를 확정한다. */
            Notice notice = Notice.create(10L, 3L, "동시 삭제 공지", "동시 삭제 검증 본문");
            return springDataNoticeRepository.saveAndFlush(NoticeJpaEntity.from(notice)).getId();
        });
    }

    /* 동시에 시작한 두 삭제 중 한 요청만 성공하고 나머지는 이미 삭제된 공지로 처리되는지 검증한다. */
    @Test
    @DisplayName("동일 공지 동시 삭제는 한 건만 성공하고 나머지는 NT-001을 반환한다")
    void allowsOnlyOneConcurrentDeletion() throws Exception {
        /* 두 작업이 모두 준비된 뒤 같은 순간에 삭제를 시작하도록 동기화 장벽을 만든다. */
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        /* 실제 서비스 트랜잭션을 호출하고 성공 또는 비즈니스 오류 코드를 결과로 반환한다. */
        Callable<String> deleteTask = () -> {
            /* 두 스레드가 모두 실행 대기 상태에 도달했음을 알린다. */
            ready.countDown();
            start.await();

            try {
                /* 같은 회사·공지에 OWNER 삭제 요청을 보내 잠금 경쟁을 발생시킨다. */
                noticeCommandService.deleteNotice(new DeleteNoticeCommand(10L, noticeId, 3L, "OWNER"));
                return "SUCCESS";
            } catch (BusinessException exception) {
                /* 두 번째 요청이 받은 공지 미존재 코드를 외부 Future 결과로 전달한다. */
                return exception.getErrorCode().getCode();
            }
        };

        try {
            /* 같은 작업을 두 스레드에 제출하고 모두 준비된 뒤 동시에 출발시킨다. */
            Future<String> first = executor.submit(deleteTask);
            Future<String> second = executor.submit(deleteTask);
            ready.await();
            start.countDown();

            /* 비관적 잠금으로 한 요청만 삭제하고 대기 요청은 활성 행을 찾지 못해야 한다. */
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("SUCCESS", "NT-001");
        } finally {
            /* 테스트가 성공하거나 실패해도 작업 스레드를 남기지 않는다. */
            executor.shutdownNow();
        }

        /* 실제 데이터베이스에는 공지 행이 남고 deletedAt만 한 번 채워져야 한다. */
        transactionTemplate.executeWithoutResult(status -> assertThat(springDataNoticeRepository.findById(noticeId))
                .isPresent()
                .get()
                .extracting(NoticeJpaEntity::getDeletedAt)
                .isNotNull());
    }
}
