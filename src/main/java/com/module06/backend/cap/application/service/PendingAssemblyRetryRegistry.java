package com.module06.backend.cap.application.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/*
 * 디스크 용량 부족으로 실패한 조립을 "재시도 대기" 상태로 인메모리에 들고 있는다.
 *
 * <h2>왜 DB가 아니라 메모리인가</h2>
 * 이 대기가 버텨야 하는 시간은 "동시에 돌던 다른 조립이 끝날 때까지"뿐이다 — 길어야
 * FFMPEG_TIMEOUT(10분) 한 사이클이다. 그 사이 서버가 재시작되면 대기 상태가 사라지긴 하지만,
 * 그 결과는 지금까지의 기본 동작(사람이 CAP-05로 수동 재시도)과 같아질 뿐이라 DB로 영속화할
 * 값어치가 없다. 인스턴스가 여럿이 되면 이 방어는 인스턴스별로 따로 돈다는 점을
 * 감안해야 한다(SttResultPollingScheduler와 동일한 처지 — 지금은 단일 인스턴스).
 *
 * <h2>inFlight를 따로 두는 이유</h2>
 * 재시도 스케줄러 주기(3분)가 조립 자체의 최대 소요 시간(FFMPEG_TIMEOUT, 10분)보다 짧다 —
 * 그대로 두면 이전 재시도가 아직 끝나기도 전에 다음 주기가 같은 회의를 또 디스패치해서,
 * "디스크 경합을 풀려던" 재시도가 오히려 동시 조립 개수를 늘려 경합을 키울 수 있다.
 * tryMarkInFlight로 "이미 재시도가 진행 중인 회의"는 다음 주기가 건너뛰게 한다.
 */
@Component
public class PendingAssemblyRetryRegistry {

    private final Map<Long, PendingRetry> pending = new ConcurrentHashMap<>();

    // 디스크 부족으로 실패했을 때 기록한다(최초 실패든, 재시도의 실패든 동일하게 이 메서드를 거친다).
    // 이미 대기 중이면 시도 횟수만 늘리고 inFlight는 false로 되돌린다 — 이 실패로 이번 시도는 끝났다.
    public void recordFailure(Long meetingId, int lastSegmentSeq, int lastSeq) {
        pending.merge(meetingId, new PendingRetry(1, lastSegmentSeq, lastSeq, false),
                (existing, fresh) -> new PendingRetry(existing.attempts() + 1, lastSegmentSeq, lastSeq, false));
    }

    // 조립이 성공하면 이 회의의 대기 상태를 지운다 — 나중에 같은 meetingId로 사람이 다시 CAP-05를
    // 부를 때 예전 시도 횟수가 남아있으면 안 된다.
    public void clear(Long meetingId) {
        pending.remove(meetingId);
    }

    // 대기 중이고 아직 재시도가 진행 중이 아니면(inFlight=false) 원자적으로 진행 중으로 표시하고
    // true를 돌려준다 — 스케줄러는 이때만 실제로 dispatch한다. 이미 진행 중이거나 대기 목록에
    // 없으면(그 사이 성공해서 clear됐거나) false — 이번 주기는 건너뛴다.
    public boolean tryMarkInFlight(Long meetingId) {
        PendingRetry[] marked = new PendingRetry[1];
        pending.computeIfPresent(meetingId, (id, current) -> {
            if (current.inFlight()) {
                return current;
            }
            marked[0] = new PendingRetry(current.attempts(), current.lastSegmentSeq(), current.lastSeq(), true);
            return marked[0];
        });
        return marked[0] != null;
    }

    // 스케줄러가 매 주기 순회할 스냅샷.
    public Map<Long, PendingRetry> snapshot() {
        return Map.copyOf(pending);
    }

    // inFlight만 false로 되돌리고 대기 목록·시도 횟수는 그대로 둔다(CodeRabbit 지적) — dispatch
    // 자체가 던지거나(비동기 풀 포화 등), 조립이 디스크 부족이 아닌 다른 이유로 실패하면
    // recordFailure/clear 어느 쪽도 안 불려서 inFlight=true가 영원히 안 풀린다. 그러면
    // retryOne이 매 주기 "이미 진행 중"으로 오판해 이 회의는 재시도도, MAX_ATTEMPTS 소진에 따른
    // 포기 로그도 영영 못 밟는다 — 메모리에 stuck 상태로 계속 남는다. 대기 목록에 없으면(이미
    // 성공해서 clear됐으면) 아무 일도 하지 않는다.
    public void releaseInFlight(Long meetingId) {
        pending.computeIfPresent(meetingId, (id, current) ->
                new PendingRetry(current.attempts(), current.lastSegmentSeq(), current.lastSeq(), false));
    }

    public record PendingRetry(int attempts, int lastSegmentSeq, int lastSeq, boolean inFlight) {
    }
}
