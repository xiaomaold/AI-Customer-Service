package com.airag.modules.integration.businesscenter.client;

import com.airag.modules.integration.businesscenter.dto.CreateOrderCommand;
import com.airag.modules.integration.businesscenter.dto.CreateWorkOrderCommand;
import com.airag.modules.integration.businesscenter.dto.OrderRecord;
import com.airag.modules.integration.businesscenter.dto.ProductRecord;
import com.airag.modules.integration.businesscenter.dto.WorkOrderRecord;

public interface BusinessCenterClient {

    OrderRecord createOrder(CreateOrderCommand command);

    OrderRecord getOrder(String orderNo);

    ProductRecord getProduct(String productNo);

    WorkOrderRecord createWorkOrder(CreateWorkOrderCommand command);

    WorkOrderRecord getWorkOrder(String workOrderNo);
}
