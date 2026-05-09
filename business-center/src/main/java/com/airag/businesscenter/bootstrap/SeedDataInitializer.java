package com.airag.businesscenter.bootstrap;

import com.airag.businesscenter.product.domain.Product;
import com.airag.businesscenter.product.service.ProductService;
import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.domain.UserType;
import com.airag.businesscenter.user.service.UserDirectoryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "business-center.seed-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeedDataInitializer implements ApplicationRunner {

    private final UserDirectoryService userDirectoryService;
    private final ProductService productService;

    public SeedDataInitializer(UserDirectoryService userDirectoryService,
                               ProductService productService) {
        this.userDirectoryService = userDirectoryService;
        this.productService = productService;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        if (userDirectoryService.count() == 0) {
            userDirectoryService.saveAll(List.of(
                    new BusinessUser(1001L, "employee.demo", "员工演示账号", UserType.EMPLOYEE, now, now),
                    new BusinessUser(2001L, "customer.demo", "客户演示账号", UserType.CUSTOMER, now, now)
            ));
        }

        if (productService.count() == 0) {
            productService.save(new Product(3001L, "P-1001", "商务笔记本电脑", new BigDecimal("5999.00"), "14 英寸轻薄办公笔记本", now, now));
            productService.save(new Product(3002L, "P-1002", "无线鼠标", new BigDecimal("129.00"), "人体工学无线鼠标", now, now));
        }
    }
}
