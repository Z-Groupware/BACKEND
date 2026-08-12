# 그라파나 모니터링 대시보드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redis 메트릭 익스포터 + Prometheus 스크레이프 + Grafana 로그/메트릭 대시보드 2개 + Slack 알림 3규칙을, 전부 코드(Dashboard as Code)로 `infra/monitoring/` 밑에 구성한다.

**Architecture:** Spring(`/actuator/prometheus`, 이미 구현됨) + Redis(신규 `redis_exporter` sidecar) → Prometheus 스크레이프. 기존 Loki 로그 파이프라인은 그대로 유지. Grafana가 Loki+Prometheus 둘 다 조회하고, 내장 Alerting이 Prometheus 룰 3개를 감시해 Slack Webhook으로 쏜다. 전부 파일 기반 provisioning이라 Grafana 컨테이너가 뜰 때 자동 로드된다.

**Tech Stack:** Docker Compose, Prometheus, Grafana 12.4.0 file provisioning(datasources/dashboards/alerting), `oliver006/redis_exporter`.

## Global Constraints

- 대시보드/설정 전부 레포에 코드로 존재해야 한다 (Grafana UI에서 직접 클릭클릭 금지) — [[project_z_grafana_monitoring_handoff]] IaC 미반영 부채 재발 방지
- Monitoring EC2 보안그룹은 SG 참조 방식만 사용, `0.0.0.0/0` 금지
- Monitoring EC2 작업 시 `docker compose down -v` 절대 금지 (볼륨 삭제됨)
- Monitoring EC2 반영 전 반드시 백업 + `docker compose config -q` 문법 검증
- 비밀값(Slack Webhook URL 등)은 레포에 하드코딩 금지, 환경변수/SSM Parameter Store로 주입
- Spring 쪽 코드 변경 없음 — `/actuator/prometheus`는 이미 노출·인증예외 처리 완료됨 ([build.gradle:52](../../../build.gradle), [application.yaml:138-142](../../../src/main/resources/application.yaml), [SecurityConfig.java:96](../../../src/main/java/com/module06/backend/global/security/SecurityConfig.java))
- 타 BC 클래스에 메트릭 코드를 심을 경우 클래스 위 한 줄 주석으로 이유 명시 (이번 계획에서는 해당 없음 — 커스텀 메트릭 코드 추가가 없기 때문)

---

### Task 1: Backend EC2 — Redis exporter 컨테이너 추가

**Files:**
- Modify: `infra/docker-compose.yml`

**Interfaces:**
- Consumes: 기존 `redis` 서비스(healthcheck 통과 후 의존), `RUNTIME_ENV_FILE`의 `REDIS_PASSWORD`
- Produces: `9121` 포트에서 Redis 메트릭 노출 (Task 4의 Prometheus 스크레이프 대상)

- [ ] **Step 1: `infra/docker-compose.yml`에 `redis_exporter` 서비스 추가**

`redis` 서비스 블록과 `volumes:` 선언 사이에 삽입:

```yaml
  redis_exporter:
    image: oliver006/redis_exporter:v1.66.0
    container_name: z-redis-exporter

    env_file:
      - path: "${RUNTIME_ENV_FILE:-./.env.runtime}"
        format: raw

    environment:
      REDIS_ADDR: "redis://redis:6379"

    depends_on:
      redis:
        condition: service_healthy

    ports:
      - "9121:9121"

    restart: unless-stopped

    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "3"
```

`REDIS_ADDR`는 컴포즈 내부 네트워크의 서비스명(`redis`)을 쓴다. `REDIS_PASSWORD`는 `env_file`로 이미 주입되는 `.env.runtime`에서 `redis_exporter`가 `REDIS_PASSWORD` 환경변수를 자동으로 읽는다(공식 이미지 지원 변수명, 별도 매핑 불필요).

포트는 `9121:9121`로 전체 바인딩한다 (Prometheus가 다른 EC2에서 스크레이프해야 하므로 `127.0.0.1` 제한 불가 — 접근 통제는 AWS 보안그룹으로 한다, Task 6 참고).

- [ ] **Step 2: 문법 검증**

Run: `cd infra && docker compose config -q`
Expected: 에러 없이 종료 (exit code 0)

- [ ] **Step 3: 로컬에서 기동 확인 (선택, Docker 사용 가능 환경이면)**

Run:
```bash
cd infra
REDIS_PASSWORD=$(openssl rand -hex 32) \
SPRING_IMAGE=busybox \
docker compose config redis_exporter
```
Expected: `redis_exporter` 서비스 정의가 에러 없이 렌더링됨

- [ ] **Step 4: Commit**

```bash
git add infra/docker-compose.yml
git commit -m "[FEAT] redis_exporter 컨테이너 추가 (#399)"
```

---

### Task 2: Prometheus 스크레이프 설정 작성

**Files:**
- Create: `infra/monitoring/prometheus/prometheus.yml`

**Interfaces:**
- Consumes: Backend EC2 프라이빗 IP (배치 시점에 실제 값으로 채워야 함), Task 1에서 만든 `9121` 포트, 기존 `/actuator/prometheus`(이미 존재)
- Produces: Prometheus가 수집하는 메트릭 (Task 6~7 대시보드가 이 메트릭을 조회)

- [ ] **Step 1: `infra/monitoring/prometheus/prometheus.yml` 작성**

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "z-spring"
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["<BACKEND_EC2_PRIVATE_IP>:8080"]
        labels:
          service: z-spring

  - job_name: "z-redis"
    static_configs:
      - targets: ["<BACKEND_EC2_PRIVATE_IP>:9121"]
        labels:
          service: z-redis
```

`<BACKEND_EC2_PRIVATE_IP>`는 배치 담당자가 Backend EC2에서 아래 명령으로 확인해서 실제 값으로 바꿔야 한다 (기존 Promtail 설정의 `172.31.41.26`과 같은 방식):

```bash
curl -s http://169.254.169.254/latest/meta-data/local-ipv4
```

- [ ] **Step 2: YAML 문법 검증**

Run: `python3 -c "import yaml; yaml.safe_load(open('infra/monitoring/prometheus/prometheus.yml'))"`
Expected: 에러 없이 종료

- [ ] **Step 3: Commit**

```bash
git add infra/monitoring/prometheus/prometheus.yml
git commit -m "[FEAT] Prometheus scrape target 설정 추가 (#399)"
```

---

### Task 3: Grafana 데이터소스 provisioning

**Files:**
- Create: `infra/monitoring/grafana/provisioning/datasources/datasources.yml`

**Interfaces:**
- Consumes: 없음 (Loki·Prometheus는 같은 Monitoring compose 네트워크의 서비스명으로 접근)
- Produces: `uid: loki`, `uid: prometheus` — Task 7의 Alerting 규칙이 `datasourceUid`로 참조

- [ ] **Step 1: `datasources.yml` 작성**

```yaml
apiVersion: 1

datasources:
  - name: Loki
    uid: loki
    type: loki
    access: proxy
    url: http://loki:3100
    isDefault: false
    editable: false

  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

`http://loki:3100`, `http://prometheus:9090`은 Monitoring EC2 compose 네트워크 내부 서비스명 — 인수인계서 8.1에서 이미 확인된 "Grafana 컨테이너 내부에서 localhost는 자기 자신" 원칙 그대로 따른다. 퍼블릭 IP·localhost 쓰지 않는다.

- [ ] **Step 2: YAML 문법 검증**

Run: `python3 -c "import yaml; yaml.safe_load(open('infra/monitoring/grafana/provisioning/datasources/datasources.yml'))"`
Expected: 에러 없이 종료

- [ ] **Step 3: Commit**

```bash
git add infra/monitoring/grafana/provisioning/datasources/datasources.yml
git commit -m "[FEAT] Grafana Loki/Prometheus 데이터소스 provisioning 추가 (#399)"
```

---

### Task 4: Grafana 대시보드 provisioning 설정

**Files:**
- Create: `infra/monitoring/grafana/provisioning/dashboards/dashboards.yml`

**Interfaces:**
- Consumes: 없음
- Produces: `/var/lib/grafana/dashboards` 경로 — Task 5·6에서 만들 JSON 파일이 여기 마운트되어 자동 로드됨

- [ ] **Step 1: `dashboards.yml` 작성**

```yaml
apiVersion: 1

providers:
  - name: z-dashboards
    orgId: 1
    folder: Z
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards
```

`allowUiUpdates: true`로 둬서 급할 때 UI에서 임시 수정은 가능하게 하되, 원본은 항상 이 레포 JSON 파일이다 (`updateIntervalSeconds: 30`마다 파일 변경사항을 다시 읽어들여 UI 변경을 덮어씀 — 파일이 진본이라는 걸 강제하는 효과).

- [ ] **Step 2: YAML 문법 검증**

Run: `python3 -c "import yaml; yaml.safe_load(open('infra/monitoring/grafana/provisioning/dashboards/dashboards.yml'))"`
Expected: 에러 없이 종료

- [ ] **Step 3: Commit**

```bash
git add infra/monitoring/grafana/provisioning/dashboards/dashboards.yml
git commit -m "[FEAT] Grafana 대시보드 provisioning 설정 추가 (#399)"
```

---

### Task 5: 대시보드 A(로그) JSON 작성

**Files:**
- Create: `infra/monitoring/grafana/dashboards/logs-dashboard.json`

**Interfaces:**
- Consumes: Task 3의 Loki 데이터소스(`uid: loki`)
- Produces: 없음 (최종 산출물)

- [ ] **Step 1: `logs-dashboard.json` 작성**

```json
{
  "title": "Z 로그",
  "uid": "z-logs",
  "editable": true,
  "timezone": "browser",
  "time": { "from": "now-1h", "to": "now" },
  "refresh": "30s",
  "templating": {
    "list": [
      {
        "name": "container",
        "type": "custom",
        "query": "z-spring,z-redis,z-spring,z-redis",
        "current": { "text": "z-spring", "value": "z-spring" },
        "options": [
          { "text": "z-spring", "value": "z-spring" },
          { "text": "z-redis", "value": "z-redis" }
        ]
      }
    ]
  },
  "panels": [
    {
      "id": 1,
      "title": "로그 텍스트 뷰",
      "type": "logs",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 12, "w": 24, "x": 0, "y": 0 },
      "options": {
        "showTime": true,
        "sortOrder": "Descending",
        "wrapLogMessage": true
      },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "loki", "uid": "loki" },
          "expr": "{container=\"$container\"}"
        }
      ]
    },
    {
      "id": 2,
      "title": "로그 유입량 (1분 단위)",
      "type": "timeseries",
      "datasource": { "type": "loki", "uid": "loki" },
      "gridPos": { "h": 8, "w": 24, "x": 0, "y": 12 },
      "fieldConfig": {
        "defaults": {
          "custom": {
            "thresholdsStyle": { "mode": "line" }
          },
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "red", "value": 200 }
            ]
          }
        }
      },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "loki", "uid": "loki" },
          "expr": "sum by (container) (count_over_time({container=~\"z-spring|z-redis\"}[1m]))"
        }
      ]
    }
  ]
}
```

- [ ] **Step 2: JSON 문법 검증**

Run: `python3 -m json.tool infra/monitoring/grafana/dashboards/logs-dashboard.json > /dev/null`
Expected: 에러 없이 종료

- [ ] **Step 3: Commit**

```bash
git add infra/monitoring/grafana/dashboards/logs-dashboard.json
git commit -m "[FEAT] 로그 대시보드 JSON 작성 (#399)"
```

---

### Task 6: 대시보드 B(메트릭) JSON 작성

**Files:**
- Create: `infra/monitoring/grafana/dashboards/metrics-dashboard.json`

**Interfaces:**
- Consumes: Task 3의 Prometheus 데이터소스(`uid: prometheus`), Task 2에서 스크레이프한 메트릭
- Produces: 없음 (최종 산출물)

- [ ] **Step 1: `metrics-dashboard.json` 작성**

```json
{
  "title": "Z 메트릭",
  "uid": "z-metrics",
  "editable": true,
  "timezone": "browser",
  "time": { "from": "now-1h", "to": "now" },
  "refresh": "30s",
  "panels": [
    {
      "id": 1,
      "title": "5xx 에러율",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 0 },
      "fieldConfig": { "defaults": { "unit": "percentunit" } },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))"
        }
      ]
    },
    {
      "id": 2,
      "title": "응답시간 p95",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 8, "x": 8, "y": 0 },
      "fieldConfig": { "defaults": { "unit": "s" } },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))"
        }
      ]
    },
    {
      "id": 3,
      "title": "TPS",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 8, "x": 16, "y": 0 },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count[1m]))"
        }
      ]
    },
    {
      "id": 4,
      "title": "힙 메모리 사용률",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 8 },
      "fieldConfig": { "defaults": { "unit": "percentunit" } },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(jvm_memory_used_bytes{area=\"heap\"}) / sum(jvm_memory_max_bytes{area=\"heap\"})"
        }
      ]
    },
    {
      "id": 5,
      "title": "GC 시간 / CPU 사용률",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 8, "x": 8, "y": 8 },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "rate(jvm_gc_pause_seconds_sum[1m])",
          "legendFormat": "GC pause"
        },
        {
          "refId": "B",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "system_cpu_usage",
          "legendFormat": "CPU"
        }
      ]
    },
    {
      "id": 6,
      "title": "스레드 수",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 8, "x": 16, "y": 8 },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "jvm_threads_live_threads"
        }
      ]
    },
    {
      "id": 7,
      "title": "Redis 커넥션 / 커맨드 처리량",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 16 },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "redis_connected_clients",
          "legendFormat": "connections"
        },
        {
          "refId": "B",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "rate(redis_commands_processed_total[1m])",
          "legendFormat": "commands/s"
        }
      ]
    },
    {
      "id": 8,
      "title": "Redis 메모리 사용률",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 16 },
      "fieldConfig": { "defaults": { "unit": "percentunit" } },
      "targets": [
        {
          "refId": "A",
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "redis_memory_used_bytes / redis_memory_max_bytes"
        }
      ]
    }
  ]
}
```

- [ ] **Step 2: JSON 문법 검증**

Run: `python3 -m json.tool infra/monitoring/grafana/dashboards/metrics-dashboard.json > /dev/null`
Expected: 에러 없이 종료

- [ ] **Step 3: Commit**

```bash
git add infra/monitoring/grafana/dashboards/metrics-dashboard.json
git commit -m "[FEAT] 메트릭 대시보드 JSON 작성 (#399)"
```

---

### Task 7: Grafana Alerting — Slack 알림 3규칙

**Files:**
- Create: `infra/monitoring/grafana/provisioning/alerting/contactpoints.yml`
- Create: `infra/monitoring/grafana/provisioning/alerting/rules.yml`

**Interfaces:**
- Consumes: Task 3의 `datasourceUid: prometheus`, 환경변수 `SLACK_WEBHOOK_URL`(Monitoring EC2에서 주입)
- Produces: 없음 (최종 산출물)

- [ ] **Step 1: `contactpoints.yml` 작성**

```yaml
apiVersion: 1

contactPoints:
  - orgId: 1
    name: slack-alerts
    receivers:
      - uid: slack-webhook-1
        type: slack
        settings:
          url: $__env{SLACK_WEBHOOK_URL}
        disableResolveMessage: false
```

`$__env{SLACK_WEBHOOK_URL}`은 Grafana 파일 provisioning의 환경변수 치환 문법이다. Monitoring EC2 compose에서 Grafana 서비스에 `SLACK_WEBHOOK_URL` 환경변수를 주입해야 동작한다 (Task 8에서 안내).

- [ ] **Step 2: `rules.yml` 작성**

```yaml
apiVersion: 1

groups:
  - orgId: 1
    name: z-alerts
    folder: Z
    interval: 1m
    rules:
      - uid: z-alert-5xx-rate
        title: "5xx 에러율 급증"
        condition: C
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "z-spring 5xx 에러율이 5%를 초과했습니다"
        data:
          - refId: A
            datasourceUid: prometheus
            relativeTimeRange: { from: 300, to: 0 }
            model:
              refId: A
              instant: true
              expr: "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))"
          - refId: C
            datasourceUid: "__expr__"
            model:
              refId: C
              type: threshold
              expression: A
              conditions:
                - evaluator: { type: gt, params: [0.05] }

      - uid: z-alert-heap
        title: "힙 메모리 위험"
        condition: C
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "z-spring 힙 메모리 사용률이 85%를 초과했습니다"
        data:
          - refId: A
            datasourceUid: prometheus
            relativeTimeRange: { from: 300, to: 0 }
            model:
              refId: A
              instant: true
              expr: "sum(jvm_memory_used_bytes{area=\"heap\"}) / sum(jvm_memory_max_bytes{area=\"heap\"})"
          - refId: C
            datasourceUid: "__expr__"
            model:
              refId: C
              type: threshold
              expression: A
              conditions:
                - evaluator: { type: gt, params: [0.85] }

      - uid: z-alert-redis-memory
        title: "Redis 메모리 위험"
        condition: C
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "z-redis 메모리 사용률이 80%를 초과했습니다"
        data:
          - refId: A
            datasourceUid: prometheus
            relativeTimeRange: { from: 300, to: 0 }
            model:
              refId: A
              instant: true
              expr: "redis_memory_used_bytes / redis_memory_max_bytes"
          - refId: C
            datasourceUid: "__expr__"
            model:
              refId: C
              type: threshold
              expression: A
              conditions:
                - evaluator: { type: gt, params: [0.80] }

policies:
  - orgId: 1
    receiver: slack-alerts
    group_by: ['alertname']
```

- [ ] **Step 3: YAML 문법 검증**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('infra/monitoring/grafana/provisioning/alerting/contactpoints.yml'))"
python3 -c "import yaml; yaml.safe_load(open('infra/monitoring/grafana/provisioning/alerting/rules.yml'))"
```
Expected: 둘 다 에러 없이 종료

- [ ] **Step 4: Commit**

```bash
git add infra/monitoring/grafana/provisioning/alerting/
git commit -m "[FEAT] Grafana Alerting Slack 알림 3규칙 추가 (#399)"
```

---

### Task 8: Monitoring EC2 반영 가이드 문서화

**Files:**
- Create: `infra/monitoring/README.md`

**Interfaces:**
- Consumes: Task 1~7의 산출물 전체
- Produces: 없음 (배치 담당자가 따라 할 절차 문서)

- [ ] **Step 1: `infra/monitoring/README.md` 작성**

```markdown
# Monitoring EC2 반영 가이드

이 폴더(`infra/monitoring/`)는 Monitoring EC2의 `/opt/monitoring/`에 반영해야 할 설정 원본이다.
Monitoring EC2 자체는 별도 배포 스크립트 없이 수동 반영한다 (모성진 확인, 2026-08-12).

## 사전 준비

1. `infra/monitoring/prometheus/prometheus.yml`의 `<BACKEND_EC2_PRIVATE_IP>`를 실제 Backend EC2 프라이빗 IP로 교체
2. Monitoring EC2의 Grafana 서비스에 `SLACK_WEBHOOK_URL` 환경변수 주입 (SSM Parameter Store 또는 `.env`, 레포에 하드코딩 금지)
3. Backend EC2 보안그룹에 Monitoring EC2 보안그룹을 소스로 하는 인바운드 규칙 추가: 8080(Spring), 9121(redis_exporter)

## 반영 절차

```bash
# 1. Monitoring EC2에서 기존 설정 백업
cd /opt/monitoring
sudo cp docker-compose.yml "docker-compose.yml.bak-$(date +%Y%m%d-%H%M%S)"
sudo cp prometheus/prometheus.yml "prometheus/prometheus.yml.bak-$(date +%Y%m%d-%H%M%S)"

# 2. 이 레포의 infra/monitoring/ 내용을 /opt/monitoring/에 복사
#    (scp 또는 git clone 후 rsync — 배치 방식은 담당자 재량)

# 3. Monitoring EC2의 docker-compose.yml에 아래 볼륨 마운트 반영
#    grafana 서비스:
#      volumes:
#        - ./grafana/provisioning:/etc/grafana/provisioning:ro
#        - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
#      environment:
#        - SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL}
#    prometheus 서비스:
#      volumes:
#        - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro

# 4. 문법 검증 (반드시 apply 전에)
sudo docker compose config -q

# 5. 반영 — docker compose down -v 절대 금지
sudo docker compose up -d
sudo docker compose ps
```

## 확인

```bash
# Prometheus target 정상 등록 확인
curl -s http://127.0.0.1:9090/api/v1/targets | python3 -m json.tool

# Grafana에서 대시보드 자동 로드 확인
# → Grafana UI → Dashboards → Z 폴더 → "Z 로그", "Z 메트릭" 존재 확인

# Alerting 규칙 로드 확인
# → Grafana UI → Alerting → Alert rules → z-alerts 그룹에 규칙 3개 존재 확인
```
```

- [ ] **Step 2: Commit**

```bash
git add infra/monitoring/README.md
git commit -m "[DOCS] Monitoring EC2 반영 가이드 작성 (#399)"
```

---

### Task 9: 종합 검증 체크리스트 (실제 EC2 반영 후 수행)

이 태스크는 코드 변경이 아니라, Task 1~8을 실제 EC2에 반영한 뒤 수행하는 수동 검증이다.

- [ ] `curl -s http://<BACKEND_EC2_PRIVATE_IP>:8080/actuator/prometheus | head -5` — Monitoring EC2에서 실행, 메트릭 텍스트 출력 확인
- [ ] `curl -s http://<BACKEND_EC2_PRIVATE_IP>:9121/metrics | head -5` — Monitoring EC2에서 실행, redis_exporter 메트릭 확인
- [ ] Prometheus UI(`:9090/targets`)에서 `z-spring`, `z-redis` 둘 다 `UP` 상태 확인
- [ ] Grafana에서 "Z 로그" 대시보드 열어서 로그 텍스트/유입량 그래프에 실제 데이터 표시되는지 확인
- [ ] Grafana에서 "Z 메트릭" 대시보드 열어서 8패널 전부 데이터 표시되는지 확인
- [ ] 알림 테스트 — 힙 임계값(85%)을 잠깐 낮게 바꿔서(예: 1%) Slack 알림이 실제로 오는지 확인한 뒤 원복
- [ ] 확인 끝나면 PR 생성 (`Closes #399`, 대상 브랜치 `develop`)

---

## 자기 점검 (Self-Review)

- **스펙 커버리지**: 설계 문서 4~9절(로그 대시보드, 메트릭 대시보드, 알림, 저장구조, EC2 반영, 후속확인) 전부 Task 1~9에 매핑됨
- **플레이스홀더**: `<BACKEND_EC2_PRIVATE_IP>`만 남아있음 — 이건 배치 시점에만 정해지는 환경값이라 의도된 파라미터 (기존 Promtail 설정도 동일 패턴)
- **타입/이름 일관성**: 데이터소스 `uid: loki`/`uid: prometheus`가 대시보드 JSON·Alerting rules.yml에서 동일하게 참조됨 확인
