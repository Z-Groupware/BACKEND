package com.module06.backend.reviewloop.judge;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * rules.yaml(카탈로그 SSOT) 로더 — Judge가 맡는 의미규칙(enforced_by=judge)과 NOTE(비채점 인지)만 추린다.
 * 여기서 뽑은 규칙이 JudgePromptBuilder를 통해 Judge 프롬프트가 된다 → 규칙은 rules.yaml 한 곳에서만 관리.
 */
public class RuleCatalog {

    /** Judge가 채점하는 의미규칙. domain="common"이면 전 도메인 공통, 아니면 해당 도메인 전용. */
    public record JudgeRule(String id, String text, Severity severity, String domain) {}

    /** 비채점 인지 항목(의도적 결정) — Judge가 flag하면 오판. */
    public record Note(String id, String text) {}

    /**
     * meta.score 채점 정책 — yaml이 SSOT. 코드에 가중치를 하드코딩하지 않기 위한 통로.
     * yaml에 meta.score가 없으면 DEFAULT(문서화된 기본값)로 떨어진다.
     */
    public record ScorePolicy(int passThreshold,
                              Map<Severity, Integer> defaultWeightBySeverity,
                              int judgeDefaultWeight) {

        public static final ScorePolicy DEFAULT = new ScorePolicy(
                80, Map.of(Severity.CRITICAL, 40, Severity.MINOR, 10), 15);

        public ScorePolicy {
            defaultWeightBySeverity = Map.copyOf(defaultWeightBySeverity);
        }
    }

    private final List<JudgeRule> judgeRules;
    private final List<Note> notes;
    private final Map<String, Severity> judgeSeverityById;   // ruleId → 카탈로그 severity (정규화용)
    private final Map<String, Integer> explicitWeightById;   // 규칙에 weight:가 명시된 경우만
    private final ScorePolicy scorePolicy;

    private RuleCatalog(List<JudgeRule> judgeRules, List<Note> notes,
                        Map<String, Integer> explicitWeightById, ScorePolicy scorePolicy) {
        this.judgeRules = List.copyOf(judgeRules);
        this.notes = List.copyOf(notes);
        Map<String, Severity> sev = new HashMap<>();
        for (JudgeRule r : judgeRules) {
            sev.put(r.id(), r.severity());
        }
        this.judgeSeverityById = Map.copyOf(sev);
        this.explicitWeightById = Map.copyOf(explicitWeightById);
        this.scorePolicy = scorePolicy;
    }

    public ScorePolicy scorePolicy() {
        return scorePolicy;
    }

    /**
     * 채점기에 넘길 ruleId→weight — 카탈로그의 judge 규칙 전부를 덮는다.
     * 우선순위: 규칙에 명시된 weight > judge_default_weight (의미규칙 기본 감점).
     * 새 judge 규칙을 yaml에 추가하면 코드 수정 없이 자동으로 가중치를 받는다(드리프트 방지).
     */
    public Map<String, Integer> effectiveWeights() {
        Map<String, Integer> weights = new HashMap<>();
        for (JudgeRule r : judgeRules) {
            weights.put(r.id(), explicitWeightById.getOrDefault(r.id(), scorePolicy.judgeDefaultWeight()));
        }
        return Map.copyOf(weights);
    }

    public List<JudgeRule> judgeRules() {
        return judgeRules;
    }

    /**
     * <b>Gate 2가 차단할 수 있게 만드는 규칙</b> — severity가 CRITICAL인 judge 규칙.
     *
     * <p>차단 결정은 두 가지뿐이다({@link ReviewLoopRunner#isBlocking}):
     * {@code AWAITING_HUMAN}은 CRITICAL finding에서만 나오고, {@code INCOMPLETE}는
     * {@code FindingSource.ACCEPTANCE} finding에서만 나온다(그 소스를 만드는 프로덕션 경로는 <b>아직 없다</b>).
     * 게다가 {@link #normalize}가 LLM severity를 카탈로그 값으로 덮어쓰므로,
     * <b>CRITICAL judge 규칙이 하나도 없으면 Gate 2는 구조적으로 어떤 코드도 차단할 수 없다</b>
     * — 훅·CI가 "Critical/미완성이면 차단"이라고 말해도 그 경로에 도달할 수 없다.
     *
     * <p>이게 조용히 방치되면 "게이트가 지켜준다"는 착각이 생긴다. 그래서 러너가 이 개수를 매번 출력하고,
     * {@code PrePushGatePolicyTest}가 실제 rules.yaml 기준으로 도달 가능성을 고정한다.
     * 차단이 필요하면 rules.yaml에 {@code severity: CRITICAL} 규칙을 추가하면 된다(코드 수정 불필요).
     */
    public List<JudgeRule> blockingRules() {
        return judgeRules.stream().filter(r -> r.severity() == Severity.CRITICAL).toList();
    }

    public List<Note> notes() {
        return notes;
    }

    /**
     * 특정 도메인용 카탈로그 — common(공통) + 그 도메인 전용 규칙만 남긴다.
     * 도메인 owner가 "내 도메인 것만" 루프를 돌릴 수 있게 한다.
     */
    public RuleCatalog forDomain(String domain) {
        List<JudgeRule> filtered = judgeRules.stream()
                .filter(r -> "common".equalsIgnoreCase(r.domain()) || r.domain().equalsIgnoreCase(domain))
                .toList();
        return new RuleCatalog(filtered, notes, explicitWeightById, scorePolicy);
    }

    /**
     * Judge finding을 카탈로그로 정규화한다(채점 직전):
     *  - 카탈로그에 없는 ruleId(LLM 환각)는 제거한다 — Judge는 카탈로그 규칙만 인용할 수 있다(화이트리스트).
     *  - severity는 LLM 출력이 아니라 카탈로그 값으로 덮어쓴다 — 치명적(CRITICAL) 판정을 LLM 변덕에 맡기지 않는다.
     * 이로써 "MINOR 규칙을 LLM이 CRITICAL로 잘못 매겨 사람 승인으로 오라우팅"되는 경로가 닫힌다.
     */
    public List<Finding> normalize(List<Finding> findings) {
        List<Finding> out = new ArrayList<>();
        for (Finding f : findings) {
            Severity catalogSeverity = judgeSeverityById.get(f.ruleId());
            if (catalogSeverity == null) {
                continue;   // 카탈로그에 없는 규칙 = 환각, 채점 제외
            }
            out.add(f.severity() == catalogSeverity ? f : f.withSeverity(catalogSeverity));
        }
        return out;
    }

    public static RuleCatalog fromFile(Path path) throws IOException {
        return fromYaml(Files.readString(path));
    }

    @SuppressWarnings("unchecked")
    public static RuleCatalog fromYaml(String yaml) {
        Map<String, Object> root = new Yaml().load(yaml);

        List<JudgeRule> judge = new ArrayList<>();
        Map<String, Integer> explicitWeights = new HashMap<>();
        for (Map<String, Object> rule : asList(root.get("rules"))) {
            if (isJudge(rule.get("enforced_by"))) {
                String id = str(rule.get("id"));
                judge.add(new JudgeRule(
                        id,
                        str(rule.get("text")),
                        parseSeverity(rule.get("severity")),
                        rule.get("domain") == null ? "common" : str(rule.get("domain"))));
                Integer explicit = asInt(rule.get("weight"));
                if (explicit != null) {
                    explicitWeights.put(id, explicit);
                }
            }
        }

        List<Note> notes = new ArrayList<>();
        for (Map<String, Object> note : asList(root.get("notes"))) {
            notes.add(new Note(str(note.get("id")), str(note.get("text"))));
        }

        return new RuleCatalog(judge, notes, explicitWeights, parseScorePolicy(root));
    }

    /** meta.score 파싱 — 없거나 일부만 있으면 그 항목만 DEFAULT로 채운다. */
    @SuppressWarnings("unchecked")
    private static ScorePolicy parseScorePolicy(Map<String, Object> root) {
        Object meta = root.get("meta");
        if (!(meta instanceof Map<?, ?> metaMap)) {
            return ScorePolicy.DEFAULT;
        }
        Object score = ((Map<String, Object>) metaMap).get("score");
        if (!(score instanceof Map<?, ?> scoreMap)) {
            return ScorePolicy.DEFAULT;
        }
        Map<String, Object> s = (Map<String, Object>) scoreMap;

        Integer threshold = asInt(s.get("pass_threshold"));
        Integer judgeDefault = asInt(s.get("judge_default_weight"));

        Map<Severity, Integer> bySeverity = new HashMap<>(ScorePolicy.DEFAULT.defaultWeightBySeverity());
        if (s.get("default_weight") instanceof Map<?, ?> dw) {
            for (Map.Entry<?, ?> e : dw.entrySet()) {
                Integer w = asInt(e.getValue());
                if (w == null) {
                    continue;
                }
                try {
                    bySeverity.put(Severity.valueOf(str(e.getKey()).trim().toUpperCase()), w);
                } catch (IllegalArgumentException ignored) {
                    // 알 수 없는 severity 키는 무시(카탈로그 오타가 채점을 깨뜨리지 않게)
                }
            }
        }

        return new ScorePolicy(
                threshold == null ? ScorePolicy.DEFAULT.passThreshold() : threshold,
                bySeverity,
                judgeDefault == null ? ScorePolicy.DEFAULT.judgeDefaultWeight() : judgeDefault);
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isJudge(Object enforcedBy) {
        if (enforcedBy instanceof String s) {
            return s.equals("judge");
        }
        if (enforcedBy instanceof List<?> list) {
            return list.contains("judge");
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    /**
     * severity 파싱 — 조용히 MINOR로 폴백하지 않는다.
     * 폴백하면 {@code CRITICAL} 오타(예: Critial)가 MINOR로 강등되어 "Critical은 항상 사람 승인"
     * 안전장치를 우회하고, 자율 루프가 자동수정 대상으로 삼아버린다. 카탈로그 로드 자체를 실패시킨다.
     */
    private static Severity parseSeverity(Object raw) {
        try {
            return Severity.valueOf(str(raw).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "rules.yaml: 알 수 없는 severity 값 '" + raw + "' — CRITICAL/MINOR만 허용", e);
        }
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
