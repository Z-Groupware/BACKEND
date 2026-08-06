package com.module06.backend.meeting.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;
import com.module06.backend.meeting.application.usecase.StartCaptureSessionUseCase;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.repository.MeetingEntryRepository;
import com.module06.backend.meeting.domain.repository.MeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataCaptureSessionRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingReservationSlotRepository;

/*
 * CAP-01의 실제 JPA 저장과 meeting_id UNIQUE 동시성 관문을 H2 스키마에서 검증한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cap01db;MODE=MySQL;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"
})
@DisplayName("CAP-01 캡처 세션 영속성 어댑터")
class CaptureSessionPersistenceAdapterTest {

    /* 실제 트랜잭션 프록시를 거쳐 CAP-01 전체 흐름을 실행하는 인바운드 Port다. */
    @Autowired
    private StartCaptureSessionUseCase startCaptureSessionUseCase;

    /* 테스트용 회의와 예약 슬롯·참석자를 원자적으로 저장하는 도메인 저장소다. */
    @Autowired
    private MeetingRepository meetingRepository;

    /* 테스트 회의를 IN_PROGRESS로 전이하는 도메인 저장소다. */
    @Autowired
    private MeetingEntryRepository meetingEntryRepository;

    /* 저장된 캡처 세션 행을 검증하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataCaptureSessionRepository springDataCaptureSessionRepository;

    /* 저장된 회의 기본 행을 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* 저장된 회의 참석자 행을 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* 저장된 회의실 슬롯 행을 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingReservationSlotRepository springDataMeetingReservationSlotRepository;

    /* 테스트 데이터 준비를 명시적 실제 트랜잭션에서 실행하기 위한 관리자다. */
    @Autowired
    private PlatformTransactionManager transactionManager;

    /* B 도메인 미연동 상태에서도 CAP-01의 D 영속성만 검증하기 위한 구성원 Port 대역이다. */
    @MockitoBean
    private MemberQueryPort memberQueryPort;

    /* 동시 시작 요청을 실행한 작업 스레드를 테스트 종료 후 정리하기 위한 실행기다. */
    private ExecutorService executorService;

    /* 각 테스트 전에 테이블을 초기화하고 정상 참석자 조회 대역을 준비한다. */
    @BeforeEach
    void setUp() {
        /* 자식 캡처·슬롯·참석자 행부터 지운 뒤 회의 기본 행을 삭제한다. */
        springDataCaptureSessionRepository.deleteAll();
        springDataMeetingReservationSlotRepository.deleteAll();
        springDataMeetingAttendeeRepository.deleteAll();
        springDataMeetingRepository.deleteAll();

        /* 요청받은 참석자 ID 전체를 같은 회사의 활성 구성원으로 반환한다. */
        when(memberQueryPort.findActiveMembers(eq(10L), anyList()))
                .thenAnswer(invocation -> {
                    /* 실제 서비스가 넘긴 배치 ID 순서를 유지해 표시 이름을 구성한다. */
                    List<Long> memberIds = invocation.getArgument(1);
                    return memberIds.stream()
                            .map(memberId -> new MemberSnapshot(
                                    memberId,
                                    "구성원-" + memberId,
                                    100L,
                                    "플랫폼팀"
                            ))
                            .toList();
                });

        /* 동시성 테스트의 두 요청을 별도 데이터베이스 커넥션에서 실행할 스레드를 준비한다. */
        executorService = Executors.newFixedThreadPool(2);
    }

    /* 작업 스레드가 다음 테스트나 Gradle 프로세스를 붙잡지 않도록 종료한다. */
    @AfterEach
    void tearDown() throws InterruptedException {
        /* 새 작업을 받지 않고 이미 제출된 요청이 짧은 시간 안에 끝나도록 기다린다. */
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }

    /* CAP-01 서비스가 실제 capture_session 행을 저장하고 응답 ID를 반환하는지 검증한다. */
    @Test
    @DisplayName("진행 중인 회의에 ACTIVE 캡처 세션을 저장한다")
    void savesActiveCaptureSession() {
        /* 회의·슬롯·참석자를 저장하고 진행 상태로 전이한다. */
        Meeting meeting = saveInProgressMeeting();

        /* 실제 트랜잭션 서비스로 host 3번의 세션 시작 요청을 실행한다. */
        CaptureSessionStartResult result = startCaptureSessionUseCase.startCaptureSession(
                new StartCaptureSessionCommand(10L, 3L, meeting.getId())
        );

        /* 생성 ID와 최초 상태·시작자·epoch 기준점이 응답에 반영돼야 한다. */
        assertThat(result.captureSessionId()).isNotNull();
        assertThat(result.status().name()).isEqualTo("ACTIVE");
        assertThat(result.startedBy()).isEqualTo(3L);
        assertThat(result.startedAtEpochMs()).isPositive();

        /* 실제 테이블에는 회의당 한 행과 D 소유 값만 저장돼야 한다. */
        assertThat(springDataCaptureSessionRepository.findAll())
                .singleElement()
                .satisfies(entity -> {
                    /* 저장된 회의 연결과 최초 생명주기 값을 확인한다. */
                    assertThat(entity.getMeetingId()).isEqualTo(meeting.getId());
                    assertThat(entity.getStartedBy()).isEqualTo(3L);
                    assertThat(entity.getStatus().name()).isEqualTo("ACTIVE");
                    assertThat(entity.getPausedAt()).isNull();
                    assertThat(entity.getEndedAt()).isNull();
                });
    }

    /* 같은 회의에 동시에 들어온 두 시작 요청이 하나의 세션만 만드는지 검증한다. */
    @Test
    @DisplayName("동일 회의 동시 시작은 성공 1건과 CS-002 1건으로 직렬화한다")
    void allowsOnlyOneConcurrentCaptureSessionStart() throws Exception {
        /* 두 스레드가 공유할 진행 중 회의를 먼저 커밋한다. */
        Meeting meeting = saveInProgressMeeting();
        StartCaptureSessionCommand command = new StartCaptureSessionCommand(10L, 3L, meeting.getId());

        /* 두 요청이 준비된 뒤 같은 순간에 서비스를 호출하도록 시작 장벽을 만든다. */
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        /* 첫 번째 요청의 성공 또는 공개 오류 코드를 비동기로 수집한다. */
        CompletableFuture<String> first = CompletableFuture.supplyAsync(
                () -> executeConcurrentStart(command, ready, start),
                executorService
        );

        /* 두 번째 요청도 동일한 회사·host·회의 값으로 실행한다. */
        CompletableFuture<String> second = CompletableFuture.supplyAsync(
                () -> executeConcurrentStart(command, ready, start),
                executorService
        );

        /* 두 작업이 시작선에 도달한 뒤 동시에 데이터베이스 트랜잭션을 열게 한다. */
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        /* meeting 행 잠금과 UNIQUE 제약으로 한 요청만 성공하고 다른 요청은 CS-002가 돼야 한다. */
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("SUCCESS", "CS-002");

        /* 경합이 끝난 뒤 실제 캡처 세션 행도 정확히 하나만 남아야 한다. */
        assertThat(springDataCaptureSessionRepository.count()).isEqualTo(1L);
    }

    /* 동시 요청 하나를 실행하고 성공 또는 BusinessException 공개 코드를 문자열로 반환한다. */
    private String executeConcurrentStart(
            StartCaptureSessionCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        try {
            /* 현재 스레드가 준비됐음을 알리고 두 요청의 공통 시작 신호를 기다린다. */
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                /* 장벽 시간 초과는 비즈니스 오류가 아닌 테스트 실패 원인으로 명확히 표시한다. */
                return "START_TIMEOUT";
            }

            /* Spring 프록시의 실제 트랜잭션 안에서 CAP-01을 실행한다. */
            startCaptureSessionUseCase.startCaptureSession(command);
            return "SUCCESS";
        } catch (BusinessException exception) {
            /* 동시 중복 요청은 서비스 또는 DB 경계에서 동일한 CS-002로 수렴해야 한다. */
            return exception.getErrorCode().getCode();
        } catch (InterruptedException exception) {
            /* 인터럽트 상태를 복원해 테스트 실행기의 종료 신호를 잃지 않게 한다. */
            Thread.currentThread().interrupt();
            return "INTERRUPTED";
        }
    }

    /* 예약 회의를 저장한 뒤 실제 입장 저장소로 IN_PROGRESS 상태를 커밋한다. */
    private Meeting saveInProgressMeeting() {
        /* 데이터 준비 단계마다 실제 커밋이 일어나도록 트랜잭션 템플릿을 사용한다. */
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Meeting savedMeeting = transaction.execute(status -> meetingRepository.saveReservation(Meeting.create(
                10L,
                12L,
                100L,
                2L,
                3L,
                "CAP-01 영속성 테스트 회의",
                LocalDateTime.of(2026, 8, 6, 14, 0),
                LocalDateTime.of(2026, 8, 6, 15, 0),
                true,
                null,
                List.of(3L, 7L, 11L)
        )));

        /* 저장된 회의 행을 잠그고 최초 입장 시각과 IN_PROGRESS 상태를 반영한다. */
        return transaction.execute(status -> {
            /* 같은 회사 범위에서 방금 저장한 회의를 조회해 실제 상태를 변경한다. */
            Meeting lockedMeeting = meetingEntryRepository
                    .findForEntry(10L, savedMeeting.getId())
                    .orElseThrow();
            return meetingEntryRepository.saveState(
                    lockedMeeting.enter(LocalDateTime.of(2026, 8, 6, 13, 58))
            );
        });
    }
}
