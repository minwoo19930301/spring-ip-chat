# 상시가동(24시간) 배포 가이드

이 문서는 "슬립 없는 상시가동" 기준으로 앱 + DB를 함께 운영하는 방법입니다.

## 핵심 요약
- Render 무료처럼 자동 슬립되는 플랫폼 대신 VM에 직접 배포합니다.
- `docker-compose.always-on.yml`로 Spring 앱 + PostgreSQL을 함께 띄웁니다.
- 컨테이너 `restart: always`로 재부팅 후 자동 복구됩니다.

## 권장 대상
- 작은 트래픽의 개인/사이드 프로젝트
- 앱/DB를 한 서버에서 단순 운영하고 싶은 경우

## 1) VM 준비
Oracle Cloud VM(Always Free 또는 유료 VM) 1대를 준비합니다.

최소 권장:
- 1 vCPU 이상
- RAM 2GB 이상
- 디스크 30GB 이상

## 2) Cloud Shell (SSH 없이) 배포 - OCI Run Command
SSH가 막혀 있을 때 Cloud Shell에서 Run Command로 VM 안에 배포를 실행할 수 있습니다.

필요 조건:
- Cloud Shell에서 `oci` CLI 사용 가능
- IAM 정책/동적 그룹 생성 권한(없으면 관리자에게 요청)

준비:
- OCI Console에서 대상 VM의 `Instance OCID`와 `Compartment OCID` 확인

Cloud Shell에서 실행:
```bash
export INSTANCE_ID="ocid1.instance.oc1..xxxx"
export COMPARTMENT_ID="ocid1.compartment.oc1..xxxx"

# 이 레포가 Cloud Shell에 있다면 아래 스크립트로 실행
bash spring-ip-chat/.deploy/deploy_via_run_command.sh "$INSTANCE_ID" "$COMPARTMENT_ID"
```

옵션:
- IAM 정책이 이미 있다면 `SKIP_IAM=1` 환경변수로 IAM 생성 단계를 건너뛸 수 있습니다.
- 다른 번들/스크립트를 쓰려면 3,4번째 인자로 `BUNDLE_URL`, `SCRIPT_URL`을 넘기세요.

Run Command가 `ACCEPTED`에 오래 머무르면:
- 정책/동적 그룹 전파가 지연된 경우가 많아 10~30분 대기 필요
- 그래도 안 되면 VM reboot 또는 SSH 경로로 전환

## 3) 서버 초기 설정
```bash
sudo apt update
sudo apt install -y ca-certificates curl git

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
```

Docker Compose 확인:
```bash
docker compose version
```

## 4) 프로젝트 배포
```bash
git clone <YOUR_REPO_URL>
cd spring-ip-chat
cp .env.always-on.example .env
```

`.env`에서 비밀번호를 강한 값으로 변경:
```bash
POSTGRES_PASSWORD=<VERY_STRONG_PASSWORD>
```

실행:
```bash
docker compose -f docker-compose.always-on.yml up -d --build
```

상태 확인:
```bash
docker compose -f docker-compose.always-on.yml ps
```

## 5) 방화벽/포트
- VM 보안그룹(또는 Security List)에서 `80/tcp` 오픈
- 접속: `http://<VM_PUBLIC_IP>`

## 6) 운영 명령어
재시작:
```bash
docker compose -f docker-compose.always-on.yml restart
```

로그:
```bash
docker compose -f docker-compose.always-on.yml logs -f app
```

업데이트 배포:
```bash
git pull
docker compose -f docker-compose.always-on.yml up -d --build
```

## 주의사항
- 앱과 DB를 같은 VM에 두면 단순하지만, VM 장애 시 둘 다 영향받습니다.
- 운영 안정성을 높이려면 추후 DB를 별도 관리형으로 분리하세요.

## 관리형 DB를 쓰고 싶다면
- Aiven Free PostgreSQL: 무료지만 비활성 상태가 길면 자동으로 전원 종료될 수 있습니다.
- Aiven Developer PostgreSQL: 유료 플랜이며, 비활성 상태에서도 자동 종료되지 않는 옵션입니다.
