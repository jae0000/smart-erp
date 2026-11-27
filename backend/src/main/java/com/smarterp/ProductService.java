package com.smarterp;

import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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

    public record StockMovement(long movementId, String productCode, String direction,
                                long quantity, OffsetDateTime createdAt) {}

    public List<StockMovement> movements() {
        return jdbc.query("""
                SELECT m.movement_id, p.product_code, m.direction, m.quantity, m.created_at
                FROM stock_movement m JOIN product p ON p.product_id = m.product_id
                ORDER BY m.movement_id DESC
                FETCH FIRST 100 ROWS ONLY
                """, (row, index) -> new StockMovement(
                row.getLong("movement_id"), row.getString("product_code"),
                row.getString("direction"), row.getLong("quantity"),
                row.getObject("created_at", OffsetDateTime.class)));
    }

    @Transactional
    public void moveStock(long id, ProductController.Direction direction, int quantity) {
        long delta = direction == ProductController.Direction.IN ? quantity : -(long) quantity;
        int updated = jdbc.update("""
                UPDATE product SET stock_qty = stock_qty + ?
                WHERE product_id = ? AND stock_qty + ? BETWEEN 0 AND 9999999999
                """, delta, id, delta);
        if (updated == 0) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_id = ?", Integer.class, id) == 0) {
                throw new EmptyResultDataAccessException(1);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    delta < 0 ? "출고할 재고가 부족합니다." : "입고 후 재고가 저장 한도를 초과합니다.");
        }

        jdbc.update("""
                INSERT INTO stock_movement (product_id, direction, quantity)
                VALUES (?, ?, ?)
                """, id, direction.name(), quantity);
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
