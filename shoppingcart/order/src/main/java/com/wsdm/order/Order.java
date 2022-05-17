package com.wsdm.order;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.redis.core.RedisHash;

@Data
@Builder
@RedisHash("Order")
public class Order {

    private int id;
    private boolean payment_status;
    private int[] item_ids;
    private int user_id;
    private int total_cost;
}
