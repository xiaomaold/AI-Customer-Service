package com.airag.businesscenter.product.controller;

import com.airag.businesscenter.common.ApiResponse;
import com.airag.businesscenter.product.domain.Product;
import com.airag.businesscenter.product.dto.CreateProductRequest;
import com.airag.businesscenter.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<List<Product>> listProducts() {
        return ApiResponse.success(productService.listProducts());
    }

    @GetMapping("/{productNo}")
    public ApiResponse<Product> getProduct(@PathVariable String productNo) {
        return ApiResponse.success(productService.requireByProductNo(productNo));
    }

    @PostMapping
    public ApiResponse<Product> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.success("product created", productService.create(request));
    }
}
