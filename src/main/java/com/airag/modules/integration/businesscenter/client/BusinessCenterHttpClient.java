package com.airag.modules.integration.businesscenter.client;

import com.airag.modules.integration.businesscenter.config.BusinessCenterProperties;
import com.airag.modules.integration.businesscenter.dto.BusinessCenterApiResponse;
import com.airag.modules.integration.businesscenter.dto.CreateOrderCommand;
import com.airag.modules.integration.businesscenter.dto.CreateWorkOrderCommand;
import com.airag.modules.integration.businesscenter.dto.OrderRecord;
import com.airag.modules.integration.businesscenter.dto.ProductRecord;
import com.airag.modules.integration.businesscenter.dto.WorkOrderRecord;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BusinessCenterHttpClient implements BusinessCenterClient {

    private static final ParameterizedTypeReference<BusinessCenterApiResponse<OrderRecord>> ORDER_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<BusinessCenterApiResponse<ProductRecord>> PRODUCT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<BusinessCenterApiResponse<WorkOrderRecord>> WORK_ORDER_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public BusinessCenterHttpClient(RestClient.Builder restClientBuilder, BusinessCenterProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public OrderRecord createOrder(CreateOrderCommand command) {
        BusinessCenterApiResponse<OrderRecord> response = execute(() -> restClient.post()
                .uri("/orders")
                .body(command)
                .retrieve()
                .body(ORDER_RESPONSE_TYPE));
        return unwrap(response);
    }

    @Override
    public OrderRecord getOrder(String orderNo) {
        BusinessCenterApiResponse<OrderRecord> response = execute(() -> restClient.get()
                .uri("/orders/{orderNo}", orderNo)
                .retrieve()
                .body(ORDER_RESPONSE_TYPE));
        return unwrap(response);
    }

    @Override
    public ProductRecord getProduct(String productNo) {
        BusinessCenterApiResponse<ProductRecord> response = execute(() -> restClient.get()
                .uri("/products/{productNo}", productNo)
                .retrieve()
                .body(PRODUCT_RESPONSE_TYPE));
        return unwrap(response);
    }

    @Override
    public WorkOrderRecord createWorkOrder(CreateWorkOrderCommand command) {
        BusinessCenterApiResponse<WorkOrderRecord> response = execute(() -> restClient.post()
                .uri("/work-orders")
                .body(command)
                .retrieve()
                .body(WORK_ORDER_RESPONSE_TYPE));
        return unwrap(response);
    }

    @Override
    public WorkOrderRecord getWorkOrder(String workOrderNo) {
        BusinessCenterApiResponse<WorkOrderRecord> response = execute(() -> restClient.get()
                .uri("/work-orders/{workOrderNo}", workOrderNo)
                .retrieve()
                .body(WORK_ORDER_RESPONSE_TYPE));
        return unwrap(response);
    }

    private <T> T unwrap(BusinessCenterApiResponse<T> response) {
        if (response == null) {
            throw new BusinessCenterClientException("EMPTY_RESPONSE", "业务中心没有返回数据");
        }
        if (!response.success()) {
            throw new BusinessCenterClientException(response.code(), response.message());
        }
        return response.data();
    }

    private <T> T execute(RestCall<T> restCall) {
        try {
            return restCall.execute();
        } catch (RestClientResponseException exception) {
            throw new BusinessCenterClientException(
                    "HTTP_" + exception.getStatusCode().value(),
                    exception.getResponseBodyAsString()
            );
        } catch (BusinessCenterClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessCenterClientException("CALL_FAILED", "调用业务中心失败，请稍后重试");
        }
    }

    @FunctionalInterface
    private interface RestCall<T> {
        T execute();
    }
}
