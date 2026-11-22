# 개발 기록과 복습

## 2026년 11월 23일 진행 상황

| 단계 | 확인한 내용 |
|---|---|
| Spring 실행 | 맥북의 터미널과 IntelliJ에서 초기 상품 조회 API를 실행했다. |
| 개발 DB 준비 | Docker의 Oracle 리스너가 1521에서 동작하고 FREEPDB1 서비스가 READY인 것을 확인했다. SMARTERP 스키마에 PRODUCT를 생성하고 P001 한 건을 커밋했다. |
| 백엔드 | 샘플 반환 코드를 SQL 조회로 교체했다. 등록·전체 수정·삭제, 입력 검증, 중복 코드와 없는 ID 처리까지 구현했다. |
| 자동 검증 | H2에서 API 테스트 11건을 통과했다. 별도 Oracle 26ai 컨테이너에서 실제 HTTP 요청을 보내 CRUD·409·404를 확인하고, 초기 SQL의 한글 복구와 재실행까지 2건을 통과했다. |
| 기존 개발 DB 접속 | 관리자 세션에서 SMARTERP 스키마를 조회했다. 이 DB에 앱 계정 비밀번호를 넣어 IntelliJ에서 HTTP 응답을 받는 단계는 별도 확인 대상이다. |
| Nexacro | Windows에서 Dataset과 Grid를 구성하고 조회 버튼을 연결하는 단계가 남았다. |

## Oracle 초기 설정

**새 맥북 환경**에서 Docker Desktop을 시작하고 아래 명령을 실행한다. 기존에 만든 컨테이너는 Docker Desktop에서 다시 시작하면 된다.

```bash
docker run -d --name smart-erp-oracle \
  -p 127.0.0.1:1521:1521 \
  -v smart-erp-data:/opt/oracle/oradata \
  container-registry.oracle.com/database/free:latest-lite
docker inspect --format '{{.State.Health.Status}}' smart-erp-oracle
```

첫 기동에는 `healthy` 표시 직후에도 DB가 열리는 중일 수 있다. `docker logs -f smart-erp-oracle`에서 `DATABASE IS READY TO USE!`를 확인하고 Ctrl+C로 로그 보기를 종료한다. 이후 저장소 루트의 SQL 파일을 복사하고 SQL*Plus를 연다.

```bash
docker cp database/product.sql smart-erp-oracle:/tmp/product.sql
docker exec -it smart-erp-oracle sqlplus / as sysdba
```

새 DB에서만 테이블스페이스와 계정을 만든다. SQL*Plus에서 한 명령씩 실행한다. `ACCEPT` 프롬프트에 앱 계정용 비밀번호를 입력한다.

```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER SESSION SET DB_CREATE_FILE_DEST = '/opt/oracle/oradata';
CREATE TABLESPACE SMARTERP_DATA DATAFILE SIZE 50M AUTOEXTEND ON NEXT 10M MAXSIZE 200M;
SET VERIFY OFF
ACCEPT app_password CHAR PROMPT 'SMARTERP password: ' HIDE
CREATE USER SMARTERP IDENTIFIED BY "&app_password" DEFAULT TABLESPACE SMARTERP_DATA QUOTA 100M ON SMARTERP_DATA;
UNDEFINE app_password
GRANT CREATE SESSION, CREATE TABLE TO SMARTERP;
```

테이블과 초기 상품을 준비할 때는 다음을 실행한다. 기존 환경에서도 최신 스크립트를 복사한 뒤 이 부분만 다시 실행할 수 있다.

```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = SMARTERP;
@/tmp/product.sql
```

초기 스크립트는 P001이 없으면 추가한다. 과거에 확인된 손상 패턴(대체 문자 6개, 공백, 대체 문자 9개)과 상품명이 정확히 일치하면 이름만 복구한다. 사용자가 수정한 정상 이름, 가격, 재고는 유지한다.

## 다음에 확인할 것

1. 맥북에서 [README의 실행 방법](../README.md#실행하기)에 따라 IntelliJ에 `DB_PASSWORD`를 넣고 서버를 시작한다.
2. Postman에서 `GET http://localhost:8080/api/products`를 호출해 저장된 P001의 이름과 가격을 확인한다. DB 접속은 요청 시점에 일어날 수 있으므로 서버 시작 로그와 조회 응답을 함께 본다.
3. 별도 상품코드 `PRACTICE-01`로 하나 등록한다. 목록에 돌아온 **그 행의 productId**로 가격을 수정한 뒤 삭제해 본다. 매 요청의 상태 코드와 DB 행 변화를 비교한다.
4. Windows에서 현재 응답 예시로 Grid를 먼저 만들고, 실제 연동할 때 조회 요청 대상을 맥북의 Spring Boot 주소로 설정한다. Windows의 localhost는 Windows 자신을 가리킨다.

## 공부할 것

| 주제 | 코드에서 볼 부분 | 스스로 해 볼 실습 |
|---|---|---|
| 요청이 SQL이 되는 과정 | [ProductController](../backend/src/main/java/com/smarterp/ProductController.java), [ProductService](../backend/src/main/java/com/smarterp/ProductService.java) | PUT의 URL에 있는 id와 JSON의 productCode가 UPDATE의 어느 위치에 들어가는지 짚어 본다. |
| 입력 검증과 DB 제약 | [ProductInput](../backend/src/main/java/com/smarterp/ProductInput.java), [초기 SQL](../database/product.sql) | 재고 1.5를 보내 400을 확인한다. 같은 상품코드를 두 번 등록해 409를 확인한다. |
| ID와 금액 | [상품 ERD](product-erd.md) | 코드를 바꿔도 productId가 유지되는 이유와 입력 금액 59.4가 응답에서 문자열 `"59.40"`이 되는 과정을 설명한다. |
| 결과가 없는 경우 | [API 명세](product-api.md) | 빈 목록 조회가 200이고 이미 지운 ID를 다시 삭제하면 404인 이유를 설명한다. |
| 스키마와 로그인 | 아래 SQL | 관리자로 CURRENT_SCHEMA를 바꾼 세션과 앱 계정 세션에서 각각 SESSION_USER, CURRENT_SCHEMA를 비교한다. |

```sql
SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') AS login_user,
       SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS current_schema_name
FROM dual;

SELECT product_code, ASCIISTR(product_name), price, stock_qty
FROM product WHERE product_code = 'P001';
```

`무선 마우스`의 정상 저장값은 `\BB34\C120 \B9C8\C6B0\C2A4`다. SQL*Plus에서 글자가 깨져 보일 때 이 값으로 화면 표시와 저장값을 구분할 수 있다. `ORA-12541`이 발생하면 오류에 적힌 호스트·포트와 리스너를 확인하고, `ORA-01017`이면 서비스명과 해당 서비스의 계정·비밀번호를 확인한다.
