package com.airag.businesscenter.product.repository;

import com.airag.businesscenter.product.domain.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Product> findByProductNo(String productNo) {
        List<Product> products = jdbcTemplate.query(
                "SELECT id, product_no, product_name, price, description, created_time, updated_time FROM bc_product WHERE product_no = ?",
                rowMapper(),
                productNo
        );
        return products.stream().findFirst();
    }

    public List<Product> findAll() {
        return jdbcTemplate.query(
                "SELECT id, product_no, product_name, price, description, created_time, updated_time FROM bc_product ORDER BY product_no",
                rowMapper()
        );
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM bc_product", Long.class);
        return count == null ? 0L : count;
    }

    public void insert(Product product) {
        jdbcTemplate.update(
                "INSERT INTO bc_product (id, product_no, product_name, price, description, created_time, updated_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                product.id(),
                product.productNo(),
                product.productName(),
                product.price(),
                product.description(),
                product.createdTime(),
                product.updatedTime()
        );
    }

    private RowMapper<Product> rowMapper() {
        return (rs, rowNum) -> new Product(
                rs.getLong("id"),
                rs.getString("product_no"),
                rs.getString("product_name"),
                rs.getBigDecimal("price"),
                rs.getString("description"),
                toLocalDateTime(rs, "created_time"),
                toLocalDateTime(rs, "updated_time")
        );
    }

    private LocalDateTime toLocalDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toLocalDateTime();
    }
}
