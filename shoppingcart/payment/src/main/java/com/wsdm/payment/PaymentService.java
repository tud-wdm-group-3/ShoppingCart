package com.wsdm.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private Map<Integer, Map<String, Integer>> orderStatuses = new HashMap<>();

    @Autowired
    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public boolean makePayment(Integer userId, Integer orderId, Integer amount) {
        if (!orderStatuses.containsKey(orderId)) {
            throw new IllegalStateException("Order with Id " + orderId + " does not exist");
        }

        Optional<Payment> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }

        Payment payment = optPaymentUser.get();
        if (payment.getCredit() >= amount){
            payment.setCredit(payment.getCredit() - amount);
            paymentRepository.save(payment);

            orderStatuses.put(orderId,  Map.of("userId", userId, "totalCost", amount, "paid", 1));

            int partition = orderId % Environment.numOrderInstances;
            fromPaymentTemplate.send("fromPaymentUpdate", partition, orderId);

            return true;
        }
        return false;
    }

    public boolean cancelPayment(Integer userId, Integer orderId) {
        if (!orderStatuses.containsKey(orderId)) {
            throw new IllegalStateException("Order with Id " + orderId + " does not exist or is already checked out.");
        }

        if (orderStatuses.get(orderId).get("paid") == 0) {
            throw new IllegalStateException("Order with Id " + orderId + " is not paid yet.");
        }

        Optional<Payment> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }

        Payment payment = optPaymentUser.get();
        int refund = orderStatuses.get(orderId).get("totalCost");
        int credit = payment.getCredit();
        payment.setCredit(credit + refund);
        paymentRepository.save(payment);

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
        Payment payment = Payment.builder()
                .credit(0)
                .build();

        // Convert local to global id
        paymentRepository.save(payment);
        int globalId = payment.getLocalId() * Environment.numPaymentInstances + Environment.myPaymentInstanceId;
        payment.setUserId(globalId);
        paymentRepository.save(payment);

        return globalId;
    }

    public Optional<Payment> findUser(Integer userId) {
        int localId = (userId - Environment.myPaymentInstanceId) / Environment.numPaymentInstances;
        return paymentRepository.findById(localId);
    }

    public boolean addFunds(Integer userId, Integer amount) {
        Optional<Payment> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            return false;
        }
        Payment payment = optPaymentUser.get();
        payment.setCredit(payment.getCredit() + amount);
        paymentRepository.save(payment);
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
        Optional<Payment> optPaymentUser = findUser(userId);
        if (!optPaymentUser.isPresent()) {
            throw new IllegalStateException("unknown user");
        }
        Payment payment = optPaymentUser.get();
        System.out.println(payment);
        int credit = payment.getCredit();
        boolean enoughCredit = credit >= cost;
        if (enoughCredit) {
            System.out.println("enough");
            payment.setCredit(credit - cost);
            paymentRepository.save(payment);
            orderStatuses.put(orderId,  Map.of("userId", userId, "totalCost", cost, "paid", 1));
        }
        System.out.println(payment);
        int partition = orderId % Environment.numOrderInstances;
        Map<String, Object> data = Map.of("orderId", orderId, "enoughCredit", enoughCredit);
        fromPaymentTemplate.send("fromPaymentTransaction", partition, orderId, data);
    }


    /**
     * Used to initialize cache of orderIds, so false relativeToCurrent.
     * @param request
     */
    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentOrderExists",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "false")}))
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


    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentRollback",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getPaymentRollback(ConsumerRecord<Integer, Map<String, Object>> request) {
        int userId = (int) request.value().get("userId");
        int refund = (int) request.value().get("refund");
        Payment user = paymentRepository.getById(userId);
        int credit = user.getCredit();
        user.setCredit(credit + refund);
        paymentRepository.save(user);
    }
}
