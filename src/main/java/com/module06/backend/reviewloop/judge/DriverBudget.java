package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 드라이버 루프 예산 — 무한 루프 방지. AutoFix(수정 라운드) ≤ 3, Total(push 재시도) ≤ 6.
 *
 * <p>{@link ReviewBudget}(인메모리, 단일 프로세스 라운드용)의 <b>영속 버전</b>. 드라이버 루프는 push를
 * 넘나드는 별도 프로세스라 상태를 파일에 남겨야 한다: {@code .git/reviewloop-budget}(로컬·브랜치별).
 * HEAD 브랜치가 바뀌면 리셋된다(새 작업=새 예산).
 *
 * <p>예산은 <b>드라이버(Claude Code)가 소유</b>한다(훅 아님 — 훅은 순수 게이트). 드라이버가 각 라운드 전에
 * 증가·검사하고, 한도를 넘으면(exit 1) 감사 로그를 남기고 사람에게 인계한다.
 *
 * <p>실행:
 * <pre>
 *   ./gradlew reviewBudget                          # 현황
 *   ./gradlew reviewBudget --args="--inc-autofix"   # 수정 라운드 +1 (한도 초과면 exit 1)
 *   ./gradlew reviewBudget --args="--inc-total"     # push 재시도 +1 (한도 초과면 exit 1)
 *   ./gradlew reviewBudget --args="--reset"         # 0으로
 * </pre>
 */
public final class DriverBudget {

    static final int MAX_AUTOFIX = 3;
    static final int MAX_TOTAL = 6;

    private static final Path STATE_FILE = Path.of(".git/reviewloop-budget");
    private static final Path HEAD_FILE = Path.of(".git/HEAD");

    /** 예산 상태 — 브랜치별 누적. */
    record State(String branch, int autofix, int total) {
        /** 한도 초과 = 다음 라운드 불가(종료 신호). */
        boolean exhausted() {
            return autofix > MAX_AUTOFIX || total > MAX_TOTAL;
        }
    }

    /** 저장 상태 + 현재 브랜치 + 연산 → 새 상태. 순수(파일 IO 없음)라 테스트 가능. */
    static State applied(State stored, String currentBranch, String op) {
        // 브랜치가 바뀌었으면(또는 저장 없음) 리셋 — 새 작업=새 예산.
        State s = (stored != null && currentBranch.equals(stored.branch()))
                ? stored
                : new State(currentBranch, 0, 0);
        return switch (op) {
            case "--inc-autofix" -> new State(s.branch(), s.autofix() + 1, s.total());
            case "--inc-total" -> new State(s.branch(), s.autofix(), s.total() + 1);
            case "--reset" -> new State(s.branch(), 0, 0);
            default -> s;   // --status(기본): 브랜치 리셋만 반영, 카운트 불변
        };
    }

    static String render(State s) {
        return String.format("[budget] %s · AutoFix %d/%d · Total %d/%d%s",
                s.branch(), s.autofix(), MAX_AUTOFIX, s.total(), MAX_TOTAL,
                s.exhausted() ? " · ⚠️ 한도 초과 → 종료·사람 인계" : "");
    }

    public static void main(String[] args) throws IOException {
        String op = args.length > 0 ? args[0] : "--status";
        State next = applied(load(), currentBranch(), op);
        save(next);
        System.out.println(render(next));
        if (next.exhausted()) {
            System.exit(1);
        }
    }

    /** 현재 브랜치 — .git/HEAD 에서. detached면 SHA. */
    private static String currentBranch() throws IOException {
        if (!Files.exists(HEAD_FILE)) {
            return "(unknown)";
        }
        String head = Files.readString(HEAD_FILE, StandardCharsets.UTF_8).strip();
        return head.startsWith("ref: refs/heads/")
                ? head.substring("ref: refs/heads/".length())
                : head;   // detached HEAD
    }

    /** 상태 로드 — 없거나 손상되면 null(호출부가 리셋). 형식: branch\tautofix\ttotal */
    private static State load() {
        try {
            if (!Files.exists(STATE_FILE)) {
                return null;
            }
            String[] p = Files.readString(STATE_FILE, StandardCharsets.UTF_8).strip().split("\t");
            if (p.length != 3) {
                return null;
            }
            return new State(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (IOException | NumberFormatException e) {
            return null;   // 손상 → 0에서 시작(크래시 금지)
        }
    }

    private static void save(State s) throws IOException {
        Files.writeString(STATE_FILE, s.branch() + "\t" + s.autofix() + "\t" + s.total(),
                StandardCharsets.UTF_8);
    }

    private DriverBudget() {
    }
}
