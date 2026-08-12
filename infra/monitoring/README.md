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
