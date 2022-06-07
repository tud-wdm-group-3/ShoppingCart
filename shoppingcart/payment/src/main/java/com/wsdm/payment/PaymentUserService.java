package com.wsdm.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentUserService {

    private final PaymentUserRepository paymentUserRepository;

    private Map<Integer, Map<String, Integer>> orderStatuses = new HashMap<>();

    @Autowired
    public PaymentUserService(PaymentUserRepository paymentUserRepository) {
        this.paymentUserRepository = paymentUserRepository;
    }

    public boolean makePayment(Integer userId, Integer orderId, Integer amount) {
        if (!orderStatuses.containsKey(orderId)) {
            throw new IllegalStateException("Order with Id " + orderId + " does not exist");
        }

        Optional<PaymentUser> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }

        PaymentUser paymentUser = optPaymentUser.get();
        if (paymentUser.getCredit() >= amount){
            paymentUser.setCredit(paymentUser.getCredit() - amount);
            paymentUserRepository.save(paymentUser);

            orderStatuses.put(orderId,  Map.of("userId", userId, "totalCost", amount, "paid", 1));

            int partition = orderId % Environment.numOrderInstances;
            fromPaymentTemplate.send("fromPaymentUpdate", partition, orderId);

            return true;
        }
        return false;
    }

    public boolean cancelPayment(Integer userId, Integer orderId) {
        if (!orderStatuses.containsKey(orderId)) {
            throw new IllegalStateException("Order with Id " + orderId + " does not exist");
        }

        if (orderStatuses.get(orderId).get("paid") == 0) {
            throw new IllegalStateException("Order with Id " + orderId + " is not paid yet.");
        }

        Optional<PaymentUser> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }

        PaymentUser paymentUser = optPaymentUser.get();
        int refund = orderStatuses.get(orderId).get("totalCost");
        int credit = paymentUser.getCredit();
        paymentUser.setCredit(credit + refund);
        paymentUserRepository.save(paymentUser);

        // TODO: stock rollbacks

        orderStatuses.remove(orderId);
        return true;
    }

    public Object getPaymentStatus(Integer userId, Integer orderId) {
        if (orderStatuses.containsKey(orderId)) {
            if (userId == orderStatuses.get(orderId).get("userId")) {
                boolean paid = orderStatuses.get(orderId).get("paid") == 1;
                Map<String, Boolean> response = Map.of("paid", paid);
                return response;
            } else {
                DeferredResult<ResponseEntity> response = new DeferredResult<>();
                response.setResult(ResponseEntity.notFound().build());
                return response;
            }
        } else {
            DeferredResult<ResponseEntity> response = new DeferredResult<>();
            response.setResult(ResponseEntity.notFound().build());
            return response;
        }
    }

    public Integer registerUser() {
        PaymentUser paymentUser = PaymentUser.builder()
                .credit(0)
                .build();

        // Convert local to global id
        paymentUserRepository.save(paymentUser);
        int globalId = paymentUser.getLocalId() * Environment.numPaymentInstances + Environment.myPaymentInstanceId;
        paymentUser.setUserId(globalId);
        paymentUserRepository.save(paymentUser);

        return globalId;
    }

    public Optional<PaymentUser> findUser(Integer userId) {
        int localId = (userId - Environment.myPaymentInstanceId) / Environment.numPaymentInstances;
        return paymentUserRepository.findById(localId);
    }

    public boolean addFunds(Integer userId, Integer amount) {
        Optional<PaymentUser> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            return false;
        }
        PaymentUser paymentUser = optPaymentUser.get();
        paymentUser.setCredit(paymentUser.getCredit() + amount);
        paymentUserRepository.save(paymentUser);
        return true;
    }


    @Autowired
    private KafkaTemplate<Integer, Object> fromPaymentTemplate;

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getPaymentTransaction(ConsumerRecord<Integer, Map<String, Object>> request) {
        int orderId = request.key();
        int userId = (int) request.value().get("userId");
        int cost = (int) request.value().get("totalCost");
        System.out.println(request.value());
        Optional<PaymentUser> optPaymentUser = findUser(userId);
        if (!optPaymentUser.isPresent()) {
            throw new IllegalStateException("unknown user");
        }
        PaymentUser paymentUser = optPaymentUser.get();
        System.out.println(paymentUser);
        int credit = paymentUser.getCredit();
        boolean enoughCredit = credit >= cost;
        if (enoughCredit) {
            System.out.println("enough");
            paymentUser.setCredit(credit - cost);
            paymentUserRepository.save(paymentUser);
            orderStatuses.put(orderId,  Map.of("userId", userId, "totalCost", cost, "paid", 1));
        }
        System.out.println(paymentUser);
        int partition = orderId % Environment.numOrderInstances;
        Map<String, Object> data = Map.of("orderId", orderId, "enoughCredit", enoughCredit);
        fromPaymentTemplate.send("fromPaymentTransaction", partition, orderId, data);
    }

    @Transactional
    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentOrderExists",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void orderExists(ConsumerRecord<Integer, Map<String, Integer>> request) {
        int orderId = request.key();
        int method = request.value().get("method");
        int userId = request.value().get("userId");
        int totalCost = request.value().get("totalCost");

        // method = 0 for add
        if (method == 0 ) {
            orderStatuses.put(orderId, Map.of("userId", userId, "totalCost", totalCost, "paid", 0));
        } else {
            orderStatuses.remove(orderId);
        }
    }

    @Transactional
    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentRollback",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getPaymentRollback(ConsumerRecord<Integer, Map<String, Object>> request) {
        int userId = (int) request.value().get("userId");
        int refund = (int) request.value().get("refund");
        PaymentUser user = paymentUserRepository.getById(userId);
        int credit = user.getCredit();
        user.setCredit(credit + refund);
        paymentUserRepository.save(user);
    }
}
