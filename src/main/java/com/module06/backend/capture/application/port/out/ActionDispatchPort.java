package com.module06.backend.capture.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 검토가 끝난 액션을 보드로 내보내는 아웃바운드 포트다(RVW-05). A 가 선언하고 C(액션)가 배선한다 —
 * {@link ReviewActionCreatePort} · {@link ReviewActionDeletePort} 와 같은 방향이다.
 *
 * <h2>넘기는 형태는 "표시"다</h2>
 * 액션 자체는 분석 직후에 이미 만들어져 있다(08/05 확정 · ActionDistributionPort). 여기서 하는
 * 일은 그 액션에 **나갔다는 표시**를 찍는 것이고, 보드는 그 표시를 보고 카드를 띄운다.
 * 새로 만들지 않으므로 실패해도 중복 액션이 생기지 않는다 — 다시 부르면 된다.
 */
public interface ActionDispatchPort {

    /*
     * 액션들을 분배 확정으로 표시한다.
     *
     * @param dispatchedAt 확정 시각. **호출자가 정한다** — 한 번의 확정으로 나간 액션은 모두
     *                     같은 시각을 가져야 "이 회의를 언제 내보냈나"가 하나로 읽힌다.
     *                     어댑터가 각자 now() 를 찍으면 같은 확정 안에서 시각이 갈린다.
     */
    DispatchOutcome markDispatched(long companyId, List<Long> actionIds, LocalDateTime dispatchedAt);

    /*
     * 표시 결과.
     *
     * <h2>이미 나간 것을 따로 세는 이유</h2>
     * 확정은 한 번으로 끝나지 않는다 — 확정 뒤에 액션을 더 추가하고(RVW-03) 다시 누를 수 있다.
     * 그때 이전에 나간 액션들이 대상에 함께 들어오는데, 둘을 합쳐 세면 두 가지가 어긋난다.
     *
     *   ① "요청한 만큼 처리됐는가" 판정이 틀린다 — 이미 나간 것은 새로 찍히지 않으므로
     *      건수가 모자라고, 호출자는 **액션이 지워졌다고 오해해 경고를 남긴다**
     *   ② 응답의 dispatchedCount 가 부풀거나 줄어든다. 화면이 "몇 건이 방금 나갔나"를
     *      그 숫자로 말하는데, 이미 나간 것을 더하면 사람이 두 번 보낸 것으로 읽는다
     *
     * @param newlyDispatched 이번 호출로 처음 표시된 액션 수
     * @param alreadyDispatched 이미 나가 있던 액션. 시각은 **처음 나간 그때 것을 유지한다** —
     *                          회수 경로가 없으므로 나간 사실은 처음 것이 맞다
     */
    record DispatchOutcome(int newlyDispatched, List<Long> alreadyDispatched) {

        /* 표시할 대상이 하나도 없었을 때. 포트를 부르지 않고 이 값을 쓴다. */
        public static DispatchOutcome none() {
            return new DispatchOutcome(0, List.of());
        }

        /* 요청한 액션이 전부 확인됐는가. 모자라면 그 사이에 지워진 것이다. */
        public int accountedFor() {
            return newlyDispatched + alreadyDispatched.size();
        }
    }
}
