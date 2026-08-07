package com.module06.backend.capture.application.port.out;

import java.util.OptionalLong;

/*
 * meeting_analysis_run(V5.16) 접근 포트다. 회의별 **실행 순서**의 기준점 하나만 갖는다.
 *
 * 왜 이게 필요한가 — 계층 잠금(analysis_layer)은 "동시에 같은 계층을 돌리는 것"만 막는다.
 * 실행 A 가 발화를 읽은 뒤 멈춰 있는 동안 실행 B 가 처음부터 끝까지 돌고, 그 뒤에 A 가
 * 깨어나 옛 입력으로 결과를 쓰는 순서를 잠금은 하나도 막지 못한다. 그때 덮이는 것은
 * 화자 판정·요약·tuple 전부다 — 저장 경로가 전부 replace(교체)이기 때문이다(#134).
 */
public interface AnalysisRunRepository {

    /*
     * 이 회의의 새 실행 번호를 발급한다. run_seq 를 1 올리고 그 값을 돌려준다.
     *
     * **반드시 발화를 읽기 전에 불러야 한다.** 읽은 뒤에 발급하면 읽기와 발급 사이에 시작한
     * 실행이 우리보다 낮은 번호를 갖게 되고, 그러면 더 새 입력을 본 실행이 오래된 것으로
     * 판정되어 거절된다 — 순서가 정확히 거꾸로 뒤집힌다.
     *
     * @return 발급된 실행 번호. **비어 있으면 같은 순간에 다른 실행이 먼저 시작한 것이다** —
     *         그 실행의 번호는 우리 것보다 크거나 같으므로 이번 실행은 물러나는 것이 맞다.
     *         오류가 아니라 순서가 정해진 것이다.
     */
    OptionalLong begin(long meetingId);
}
