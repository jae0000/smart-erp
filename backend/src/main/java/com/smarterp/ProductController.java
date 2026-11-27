package com.smarterp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = {"http://127.0.0.1:4098", "http://192.168.0.104:4098"})
@RequestMapping("/api/products")
public class ProductController {
    public record ProductList(List<Product> products) {}

    private static final Map<String, Boolean> SUCCESS = Map.of("success", true);
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public ProductList list() {
        return new ProductList(service.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Boolean> create(@Valid @RequestBody ProductInput product) {
        service.create(product);
        return SUCCESS;
    }

    @PutMapping("/{id}")
    public Map<String, Boolean> update(@PathVariable("id") long id,
                                       @Valid @RequestBody ProductInput product) {
        service.update(id, product);
        return SUCCESS;
    }

    public enum Direction { IN, OUT }

    public record MovementInput(@NotNull Direction direction, @NotNull @Positive Integer quantity) {}

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Boolean> moveStock(@PathVariable("id") long id,
                                          @Valid @RequestBody MovementInput movement) {
        service.moveStock(id, movement.direction(), movement.quantity());
        return SUCCESS;
    }

    @GetMapping("/movements")
    public Map<String, List<ProductService.StockMovement>> movements() {
        return Map.of("movements", service.movements());
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable("id") long id) {
        service.delete(id);
        return SUCCESS;
    }
}
