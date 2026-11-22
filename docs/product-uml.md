# 상품 관리 UML

## 클래스 다이어그램 · Oracle 연동 후

현재 ProductController는 ProductList에 샘플 Product를 담아 반환한다. 아래는 DB 연동 후의 구조다. 컨트롤러가 HTTP 요청과 응답을 맡고 ProductService가 JdbcTemplate으로 SQL을 실행한다.

```mermaid
classDiagram
    direction TB
    class ProductController {
        +list()
        +create(ProductInput input)
        +update(long id, ProductInput input)
        +delete(long id)
    }
    class ProductService {
        +list() List~Product~
        +create(ProductInput input)
        +update(long id, ProductInput input)
        +delete(long id)
    }
    class ProductInput {
        String productCode
        String productName
        BigDecimal price
        Long stockQty
    }
    class Product {
        Long productId
        String productCode
        String productName
        String price
        Long stockQty
    }
    ProductController --> ProductService : 호출
    ProductController ..> ProductInput : 요청 검증
    ProductService ..> Product : 조회 결과 생성
    ProductService --> JdbcTemplate : SQL 실행
```

## 시퀀스 다이어그램 · 상품 등록과 목록 갱신

```mermaid
sequenceDiagram
    participant N as Nexacro 상품 화면
    participant C as ProductController
    participant S as ProductService
    participant D as Oracle PRODUCT
    N->>C: POST /api/products
    alt 입력값 검증 실패
        C-->>N: 400 입력 오류
    else 입력값 검증 통과
        C->>S: create(input)
        S->>D: INSERT (JdbcTemplate)
        alt 상품코드 중복
            D-->>S: 고유 제약 위반
            S-->>C: 중복 키 예외 전파
            C-->>N: 409 중복 오류
        else 저장 성공
            D-->>S: 등록 완료
            S-->>C: 트랜잭션 완료
            C-->>N: 201 등록 성공
            N->>C: GET /api/products
            C->>S: list()
            S->>D: SELECT (PRODUCT_ID 순)
            D-->>S: 상품 행
            S-->>C: 상품 목록
            C-->>N: 200 products 배열
            N->>N: Dataset 갱신 및 Grid 표시
        end
    end
```

그림에서 400·409 응답은 컨트롤러 계층의 예외 처리기(ApiExceptionHandler)가 작성한다. 등록 성공 뒤 재조회하면 Oracle이 생성한 productId를 화면에서도 사용할 수 있다.
