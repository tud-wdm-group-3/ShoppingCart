package com.wdsm.orderwrapper;

import com.wsdm.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderWrapperService {

    private final OldOrderClient orderClient;

    public String createOrder(int userId, int partitionId){
        return orderClient.createOrder(userId,partitionId);
    }

    public void removeOrder(int orderId,int partitionId){
        orderClient.removeOrder(orderId,partitionId);
    }

    public Order findOrder(int orderId, int partitionId){
        return orderClient.findOrder(orderId,partitionId);
    }

    public void addItemInOrder(int orderId,int itemId,int partitionId){
        orderClient.addItemInOrder(orderId,itemId,partitionId);
    }

    public void removeItemFromOrder(int orderId,int itemId,int partitionId){
        orderClient.removeItemFromOrder(orderId,itemId,partitionId);
    }

    public ResponseEntity checkoutOrder(int orderId, int partitionId){
        return orderClient.checkoutOrder(orderId,partitionId);
    }
}
