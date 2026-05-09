package com.airag.businesscenter.product.service;

import com.airag.businesscenter.common.BusinessException;
import com.airag.businesscenter.common.IdGenerator;
import com.airag.businesscenter.product.domain.Product;
import com.airag.businesscenter.product.dto.CreateProductRequest;
import com.airag.businesscenter.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final IdGenerator idGenerator;
    private final ProductRepository productRepository;

    public ProductService(IdGenerator idGenerator, ProductRepository productRepository) {
        this.idGenerator = idGenerator;
        this.productRepository = productRepository;
    }

    public Product create(CreateProductRequest request) {
        if (productRepository.findByProductNo(request.productNo()).isPresent()) {
            throw new BusinessException("PRODUCT_EXISTS", "productNo already exists");
        }
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product(
                idGenerator.nextNumericId(),
                request.productNo(),
                request.productName(),
                request.price(),
                request.description(),
                now,
                now
        );
        productRepository.insert(product);
        return product;
    }

    public Product requireByProductNo(String productNo) {
        return productRepository.findByProductNo(productNo)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "product not found: " + productNo));
    }

    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    public long count() {
        return productRepository.count();
    }

    public void save(Product product) {
        if (productRepository.findByProductNo(product.productNo()).isEmpty()) {
            productRepository.insert(product);
        }
    }
}
