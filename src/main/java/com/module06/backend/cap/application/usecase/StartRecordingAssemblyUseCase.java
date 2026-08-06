package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;

import java.util.List;

// 컨트롤러가 부르는 "명찰" — 녹음 종료/조립(CAP-05)의 실제 구현체(RecordingAssemblyService)를 몰라도 되게 한다.
public interface StartRecordingAssemblyUseCase {

    // seq 연속성 검증 후 조립을 트리거한다. 구멍이 있으면 예외(409)로 조립을 막는다.
    Result startRecordingAssembly(StartRecordingAssemblyCommand command);

    /**
     * 조립 트리거 결과.
     *
     * @param status      조립 상태. 성공 시 "ASSEMBLING".
     * @param missingSeqs 연속성 검증 결과 비어있는 순번. 구멍이 있으면 409로 막으므로 이 성공 응답에선 항상 빈 목록이다
     *                    (어느 순번이 빠졌는지는 CAP-08 status 조회로 확인).
     */
    record Result(
            String status,
            List<Integer> missingSeqs
    ) {
    }
}
