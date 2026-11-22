package com.smarterp;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {
    public record Product(long productId, String productCode, String productName,
                          String price, long stockQty) {}
    public record ProductList(List<Product> products) {}

    @GetMapping("/api/products")
    public ProductList list() {
        return new ProductList(List.of(
            new Product(1, "P001", "무선 마우스", "25000.00", 12)
        ));
    }
}
