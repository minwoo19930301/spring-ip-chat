# 무료 배포 가이드 (2026-03-14 확인)

요구사항(채팅 히스토리 영구 보존)을 만족하려면, 앱 서버와 DB를 분리하는 것이 안전합니다.

권장 조합:
- 앱 서버: Render Free Web Service
- DB: Aiven Free PostgreSQL

## 왜 이 조합인가
- Render Free Web Service는 WebSocket을 지원합니다.
- Render Free Web Service는 15분 비활성 시 인스턴스가 슬립됩니다.
- Render 디스크는 임시(ephemeral)라서, 로컬 파일 DB(H2)만 쓰면 재배포/재시작 때 데이터 유실 위험이 있습니다.
- Render Free PostgreSQL은 생성 후 30일 뒤 만료됩니다. 장기 히스토리 보존 용도에는 부적합합니다.
- Aiven Free PostgreSQL은 "시간 제한 없음"과 1GB 스토리지를 제공합니다.

## 1) Aiven Free PostgreSQL 생성
1. Aiven 가입 후 Free 플랜 PostgreSQL 서비스 생성
2. DB 접속 정보(host, port, dbname, username, password) 확인

## 2) Render에 앱 배포
1. 이 프로젝트를 GitHub에 push
2. Render에서 New + Blueprint 선택 후 저장소 연결
3. `render.yaml`로 웹 서비스 생성
4. Render 서비스의 Environment에 아래 변수 입력

```bash
DATABASE_URL=jdbc:postgresql://<HOST>:<PORT>/<DBNAME>?sslmode=require
DATABASE_USERNAME=<USERNAME>
DATABASE_PASSWORD=<PASSWORD>
```

5. Deploy 실행 후 URL 접속

## 3) 동작 확인
1. 브라우저 A에서 메시지 1개 전송
2. 브라우저 B(또는 시크릿 창)에서 접속
3. 기존 메시지가 히스토리로 보이면 DB 저장 정상

## 참고 링크
- Render WebSockets: https://render.com/docs/websocket
- Render Free Web Service 제한(슬립/ephemeral): https://render.com/docs/free
- Render Free PostgreSQL 30일 만료: https://render.com/docs/free#free-postgresql
- Aiven Free plan(무기한/1GB): https://aiven.io/plans
