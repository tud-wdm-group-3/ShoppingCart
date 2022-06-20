package com.wsdm.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findOrdersByInCheckoutAndReplicaHandlingCheckout(boolean inCheckout, String replicaHandlingCheckout);
    List<Order> findOrdersByOrderBroadcastedIsNot(Order.OrderBroadcasted orderBroadcasted);
}
