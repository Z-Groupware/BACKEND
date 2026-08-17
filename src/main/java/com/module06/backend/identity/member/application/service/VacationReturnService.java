package com.module06.backend.identity.member.application.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.module06.backend.identity.member.application.port.out.VacationReturnPort;
import com.module06.backend.identity.member.application.usecase.ReturnExpiredVacationsUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 휴직 자동 복귀.
 *
 * <p>포트를 경유하는 이유는 ARCH_003 이다 — application 은 infrastructure 를 직접 참조하지 않는다.
 *
 * <p>0건일 때 로그를 남기지 않는다. 이 배치는 매일 도는데 대부분의 날은 0건이라, 남기면 하루 한 줄씩
 * 아무 일도 없었다는 기록만 쌓여 정작 복직이 일어난 날을 찾기 어려워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VacationReturnService implements ReturnExpiredVacationsUseCase {

    private final VacationReturnPort vacationReturnPort;

    @Override
    public int returnExpired(LocalDate today) {
        List<Long> returned = vacationReturnPort.returnExpiredVacations(today);
        if (!returned.isEmpty()) {
            /*
             * memberId 까지 남긴다. 상태를 되돌린 것은 사람이 아니라 배치라 감사 테이블에 남는
             * 행이 없고, "누가 왜 재직이 됐나"를 나중에 답할 수 있는 곳이 이 로그뿐이다.
             */
            log.info("휴직 자동 복귀 — {}명 재직 전환. memberIds={}", returned.size(), returned);
        }
        return returned.size();
    }
}
