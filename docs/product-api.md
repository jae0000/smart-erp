# 상품 API 명세서

Spring Boot가 Oracle의 `PRODUCT`를 읽고 쓰는 HTTP 계약이다. Windows에서 개발할 Nexacro 화면도 같은 필드명을 사용한다.

기본 주소는 `http://localhost:8080`이다. POST와 PUT은 `Content-Type: application/json`을 사용한다.

| 상태 | 기능 | 메서드와 경로 | 요청 본문 | 성공 응답 |
|---|---|---|---|---|
| 구현됨 | 목록 조회 | GET /api/products | 없음 | 200 · products 배열 |
| 구현됨 | 상품 등록 | POST /api/products | 상품 입력 | 201 · {"success":true} |
| 구현됨 | 상품 전체 수정 | PUT /api/products/{id} | 상품 입력 | 200 · {"success":true} |
| 구현됨 | 상품 삭제 | DELETE /api/products/{id} | 없음 | 200 · {"success":true} |

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

목록은 `productId` 오름차순이다. 빈 목록도 200과 `{"products":[]}`을 반환한다. Nexacro에서는 응답의 `products` 배열을 Dataset 행으로 읽으면 된다. `price`는 소수점 이하 두 자리까지 표현한 문자열이다.

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

## 오류 응답

| HTTP | 적용 대상 | 조건 |
|---|---|---|
| 400 | POST, PUT | 필수값 누락, 잘못된 JSON, 음수 가격·재고, 소수 재고 또는 입력 범위 위반 |
| 400 | PUT, DELETE | 숫자로 해석할 수 없는 경로 ID |
| 404 | PUT, DELETE | 해당 ID의 상품이 없음 |
| 409 | POST, PUT | 다른 상품이 같은 productCode를 사용 중 |

중복 코드는 `{"message":"이미 존재하는 상품코드입니다"}`, 존재하지 않는 상품은 `{"message":"상품을 찾을 수 없습니다."}`를 반환한다. 등록·수정·삭제에 성공하면 화면에서 목록을 다시 조회해 DB에 반영된 결과를 표시한다.
