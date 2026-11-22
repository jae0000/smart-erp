# 상품 API 명세서

Smart ERP의 Nexacro 화면과 Spring Boot 사이에서 사용하는 JSON 계약이다. 기준 커밋은 `260a1fe`이며, 현재 실행 가능한 기능은 샘플 상품 목록 조회다. Oracle을 연결한 뒤에도 같은 조회 주소와 응답 필드를 유지한다.

기본 주소는 `http://localhost:8080`이다. POST와 PUT은 `Content-Type: application/json`을 사용한다.

| 상태 | 기능 | 메서드와 경로 | 요청 본문 | 성공 응답 |
|---|---|---|---|---|
| 구현됨 | 목록 조회 | GET /api/products | 없음 | 200 · products 배열 |
| 설계 | 상품 등록 | POST /api/products | 상품 입력 | 201 · {"success":true} |
| 설계 | 상품 전체 수정 | PUT /api/products/{id} | 상품 입력 | 200 · {"success":true} |
| 설계 | 상품 삭제 | DELETE /api/products/{id} | 없음 | 200 · {"success":true} |

`{id}`에는 조회 결과의 숫자형 `productId`를 넣는다. 예를 들어 ID가 1인 상품의 수정 주소는 `/api/products/1`이다.

## 조회 응답

```json
{
  "products": [
    {
      "productId": 1,
      "productCode": "P001",
      "productName": "무선 마우스",
      "price": "25000.00",
      "stockQty": 12
    }
  ]
}
```

Oracle 연동 후에는 productId 오름차순으로 반환한다. 상품이 없으면 200과 `{"products":[]}`을 반환한다. Nexacro에서는 응답의 products 배열을 Dataset 행으로 읽는다.

## 등록·수정 요청

```json
{
  "productCode": "P002",
  "productName": "무선 키보드",
  "price": 39000.00,
  "stockQty": 8
}
```

| 필드 | 입력 형식 | 규칙 |
|---|---|---|
| productCode | 문자열 | 필수, 공백만 입력 불가, 최대 30자, 상품 간 중복 불가 |
| productName | 문자열 | 필수, 공백만 입력 불가, 최대 100자 |
| price | 십진수 | 필수, 0 이상, 정수부 최대 10자리·소수부 최대 2자리. 조회 응답에서는 문자열 |
| stockQty | 정수 | 필수, 0부터 2,147,483,647까지 |

등록 시 DB가 ID와 생성 시각을 부여한다. PUT은 위 네 필드를 모두 받는다. 생성 시각은 현재 조회 응답에서 생략한다.

## 예정된 오류 응답

| HTTP | 적용 대상 | 조건 |
|---|---|---|
| 400 | POST, PUT | 필수값 누락 또는 입력 범위 위반 |
| 404 | PUT, DELETE | 해당 ID의 상품이 없음 |
| 409 | POST, PUT | 다른 상품이 같은 productCode를 사용 중 |

중복 코드의 응답 예시는 `{"message":"이미 존재하는 상품코드입니다"}`이다. 404 응답 본문의 형식은 아직 확정하지 않았다.
