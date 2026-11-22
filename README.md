# Smart ERP

상품 담당자가 상품코드, 이름, 가격, 재고 수량을 관리하는 개인 프로젝트입니다. Spring Boot는 상품 목록 조회·등록·수정·삭제를 제공하고, Oracle은 상품과 재고를 저장합니다. Windows용 Nexacro 상품 관리 화면을 다음 단계로 개발합니다.

| 구성 | 기술 | 담당 기능 |
|---|---|---|
| 백엔드 | Java 17, Spring Boot 3.5.7, Spring JDBC | HTTP 요청 검증, 상품 CRUD, 오류 응답 |
| 데이터베이스 | Oracle AI Database 26ai Free | 상품 ID 자동 생성, 상품코드 중복 방지, 가격·재고 저장 |
| 화면 (예정) | Nexacro N v24 | 상품 목록을 Dataset과 Grid에 표시하고 변경 사항 전송 |

## 실행하기

이미 개발 DB에 `SMARTERP.PRODUCT`가 있다면 Docker Desktop에서 `smart-erp-oracle`을 실행한 뒤 아래 단계로 시작합니다. 새 환경은 [Oracle 초기 설정](docs/development-notes.md#oracle-초기-설정)을 먼저 진행합니다.

| 접속 설정 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1` |
| `DB_USER` | `SMARTERP` |
| `DB_PASSWORD` | 로컬 Oracle에서 설정한 계정 비밀번호 |

**IntelliJ IDEA**에서 `backend`를 Gradle 프로젝트로 열고 Project SDK와 Gradle JVM에 JDK 17을 지정합니다. `SmartErpApplication` 실행 구성의 **환경 변수**에 `DB_PASSWORD`를 설정하고 실행합니다.

**macOS 터미널(zsh)**에서는 저장소 루트에서 다음을 실행합니다. 비밀번호는 프롬프트에 입력합니다.

```zsh
cd backend
read -rs 'DB_PASSWORD?SMARTERP password: '
echo
export DB_PASSWORD
./gradlew bootRun
```

서버 실행 후 [로컬 상품 목록](http://localhost:8080/api/products)을 엽니다. 정상 응답에는 `products` 배열이 들어 있습니다. 초기 상품이 있는 경우 첫 행은 다음과 같습니다.

```json
{"productId":1,"productCode":"P001","productName":"무선 마우스","price":"25000.00","stockQty":12}
```

## 상품 API

| 기능 | 요청 | 성공 시 |
|---|---|---|
| 목록 조회 | `GET /api/products` | 200 · ID순 상품 목록 |
| 등록 | `POST /api/products` | 201 · 저장 완료 |
| 전체 수정 | `PUT /api/products/{id}` | 200 · 수정 완료 |
| 삭제 | `DELETE /api/products/{id}` | 200 · 삭제 완료 |

등록·수정에는 JSON으로 `productCode`, `productName`, `price`, `stockQty`를 전달합니다. 중복 코드는 409, 없는 상품 ID는 404, 잘못된 입력은 400으로 처리합니다. [요청·응답 예시와 입력 범위](docs/product-api.md)

## 테스트

`backend`에서 실행합니다.

```bash
./gradlew test
./gradlew oracleTest
```

`test`는 H2의 Oracle 호환 모드에서 HTTP 계약, 입력 검증, 중복 코드와 없는 상품을 확인합니다. `oracleTest`는 Docker Desktop에 **별도 Oracle 26ai Free 컨테이너**를 띄우고 실제 HTTP 서버에서 JDBC 저장·조회, 한글 상품명과 초기 SQL 재실행을 검증합니다. 테스트용 포트와 계정은 실행할 때 생성합니다. 첫 Oracle 테스트는 이미지 준비와 DB 기동에 시간이 걸립니다.

2026년 11월 23일 맥북에서 API 테스트 11건과 Oracle 통합 테스트 2건이 통과했습니다. 기존 개발 컨테이너에 접속할 때는 해당 컨테이너의 `SMARTERP` 비밀번호를 사용합니다.

## 프로젝트 문서

- [Oracle 테이블 설계와 ERD](docs/product-erd.md)
- [백엔드 클래스와 등록 시퀀스 다이어그램](docs/product-uml.md)
- [개발 진행 상황, Oracle 설정, 복습 실습](docs/development-notes.md)
