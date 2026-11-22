package com.smarterp;

import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final JdbcTemplate jdbc;

    public ProductService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Product> list() {
        return jdbc.query("""
                SELECT product_id, product_code, product_name, price, stock_qty
                FROM product
                ORDER BY product_id
                """, (row, index) -> new Product(
                row.getLong("product_id"),
                row.getString("product_code"),
                row.getString("product_name"),
                row.getBigDecimal("price").setScale(2).toPlainString(),
                row.getLong("stock_qty")));
    }

    @Transactional
    public void create(ProductInput product) {
        jdbc.update("""
                INSERT INTO product (product_code, product_name, price, stock_qty)
                VALUES (?, ?, ?, ?)
                """, product.productCode(), product.productName(), product.price(), product.stockQty());
    }

    @Transactional
    public void update(long id, ProductInput product) {
        int count = jdbc.update("""
                UPDATE product SET product_code = ?, product_name = ?, price = ?, stock_qty = ?
                WHERE product_id = ?
                """, product.productCode(), product.productName(), product.price(), product.stockQty(), id);
        requireProduct(count);
    }

    @Transactional
    public void delete(long id) {
        requireProduct(jdbc.update("DELETE FROM product WHERE product_id = ?", id));
    }

    private static void requireProduct(int affectedRows) {
        if (affectedRows == 0) {
            throw new EmptyResultDataAccessException(1);
        }
    }
}
