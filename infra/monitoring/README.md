# Monitoring EC2 반영 가이드

이 폴더(`infra/monitoring/`)는 Monitoring EC2의 `/opt/monitoring/`에 반영해야 할 설정 원본이다.
Monitoring EC2 자체는 별도 배포 스크립트 없이 수동 반영한다 (모성진 확인, 2026-08-12).

## 사전 준비

1. `infra/monitoring/prometheus/prometheus.yml`의 `<BACKEND_EC2_PRIVATE_IP>`를 실제 Backend EC2 프라이빗 IP로 교체
2. Monitoring EC2의 Grafana 서비스에 `SLACK_WEBHOOK_URL` 환경변수 주입 (SSM Parameter Store 또는 `.env`, 레포에 하드코딩 금지)
3. Backend EC2 보안그룹에 Monitoring EC2 보안그룹을 소스로 하는 인바운드 규칙 추가: 8080(Spring), 9121(redis_exporter)

## 사전 준비 (계속) — 기존 Loki 데이터소스/조직 기본값 확인

Monitoring EC2의 Grafana에는 이미 수기로 등록된 `Loki` 데이터소스가 운영 중이다
(로그 파이프라인 인수인계 문서 참조). 이 레포의 `datasources.yml`을 반영하면
Grafana의 file provisioner가 **이름 기준으로** 기존 데이터소스를 찾아 매칭하므로,
기존 `Loki` 데이터소스의 UID가 이 파일에 적힌 `loki`로 강제로 재기록될 수 있고
(기존 UID를 참조하던 대시보드·Explore 링크가 깨질 수 있음), 새 Prometheus
데이터소스의 `isDefault: true` 때문에 조직 기본 데이터소스도 함께 바뀐다.

**반드시 반영 전에 현재 상태를 기록해 둔다:**

```bash
curl -s http://127.0.0.1:3000/api/datasources -u admin:<password> | python3 -m json.tool
```

이 출력에서 기존 `Loki` 데이터소스의 UID와 현재 org 기본 데이터소스가 무엇인지
확인하고, 이대로 덮어써도 괜찮은지 판단한다. 문제가 있다면 `datasources.yml`의
`uid`/`isDefault` 값을 기존 상태에 맞게 조정한 뒤 반영한다.

## 반영 절차

```bash
# 1. Monitoring EC2에서 기존 설정 백업
cd /opt/monitoring
sudo cp docker-compose.yml "docker-compose.yml.bak-$(date +%Y%m%d-%H%M%S)"
sudo cp prometheus/prometheus.yml "prometheus/prometheus.yml.bak-$(date +%Y%m%d-%H%M%S)"

# 2. 이 레포의 infra/monitoring/ 내용을 /opt/monitoring/에 복사
#    (scp 또는 git clone 후 rsync — 배치 방식은 담당자 재량)
#
#    ⚠️ prometheus/prometheus.yml은 절대로 통째로 덮어쓰지 않는다.
#    이 레포의 prometheus/prometheus.yml에는 이번에 추가하는 스크레이프 잡
#    (z-spring, z-redis) 2개만 들어있고, Monitoring EC2의 기존
#    prometheus.yml에는 이미 운영 중인 다른 scrape_configs(예: 로그
#    파이프라인 관련 잡)가 들어있다. 반드시 기존 /opt/monitoring/prometheus/prometheus.yml을
#    열어서 이 레포의 z-spring, z-redis 두 scrape_configs 항목만 "병합"
#    (append)한다. cp/scp로 파일 전체를 교체하지 않는다.

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

# 4. 문법 검증 (반드시 apply 전에, 반드시 둘 다 실행)
sudo docker compose config -q

# Prometheus 설정 자체는 docker compose config -q로 검증되지 않으므로 별도로 확인한다.
# promtool이 호스트에 설치되어 있으면:
promtool check config prometheus/prometheus.yml
# 설치되어 있지 않으면 Docker 기반으로 동일하게 확인:
docker run --rm -v "$(pwd)/prometheus/prometheus.yml:/prometheus.yml" prom/prometheus:latest promtool check config /prometheus.yml

# 검증 실패 시 반영을 중단한다 (STOP if either validation step fails).

# 5. 반영 — docker compose down -v 절대 금지
sudo docker compose up -d
sudo docker compose ps
```

### 롤백 (문제 발생 시)

```bash
cd /opt/monitoring
sudo cp docker-compose.yml.bak-<타임스탬프> docker-compose.yml
sudo cp prometheus/prometheus.yml.bak-<타임스탬프> prometheus/prometheus.yml
sudo docker compose up -d
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

## 배포 후 스모크 테스트

```bash
# redis_exporter 메트릭 노출 확인
curl -s http://127.0.0.1:9121/metrics | grep '^redis_up'

# Prometheus 타겟 전부 up 상태인지 확인
curl -s http://127.0.0.1:9090/api/v1/targets | python3 -m json.tool
```

- Grafana UI → Alerting → Contact points → `slack-alerts` → **Test** 버튼으로 실제
  Slack 채널에 테스트 알림이 도착하는지 확인한다 (`$__env{SLACK_WEBHOOK_URL}`
  환경변수 치환이 Alerting provisioning에서도 동작하는지는 문서로 검증되지
  않았으므로, 반드시 실제로 테스트해야 한다 — 실패 시 Grafana Alerting API 또는
  `envsubst`로 배포 시점에 값을 직접 주입하는 방식으로 전환).
