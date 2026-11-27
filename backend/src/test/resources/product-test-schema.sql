CREATE TABLE product (
    product_id NUMBER(10) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_code VARCHAR2(30) NOT NULL UNIQUE,
    product_name VARCHAR2(100) NOT NULL,
    price NUMBER(12,2) DEFAULT 0 NOT NULL CHECK (price >= 0),
    stock_qty NUMBER(10) DEFAULT 0 NOT NULL CHECK (stock_qty >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE stock_movement (
    movement_id NUMBER(10) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id NUMBER(10) NOT NULL REFERENCES product (product_id),
    direction VARCHAR2(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    quantity NUMBER(10) NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
