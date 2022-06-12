package com.wsdm.payment;

import com.wsdm.payment.persistentlog.LogRepository;
import com.wsdm.payment.persistentlog.PersistentMap;
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

    /**
     * A log of current order statuses
     */
    private Map<Integer, Map> orderStatuses;

    /**
     * Maps orderId to deferredResult.
     */
    private Map<Integer, DeferredResult<ResponseEntity>> pendingPaymentResponses = new HashMap<>();

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, LogRepository logRepository) {
        this.paymentRepository = paymentRepository;

        orderStatuses = new PersistentMap<Map>("orderStatuses", logRepository, Map.class);
    }

    public void makePayment(Integer userId, Integer orderId, Integer amount, DeferredResult<ResponseEntity> response) {
        if (!orderStatuses.containsKey(orderId)) {
            response.setResult(ResponseEntity.notFound().build());
            return;
        }

        Optional<Payment> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            response.setResult(ResponseEntity.notFound().build());
            return;
        }

        Payment payment = optPaymentUser.get();

        int credit = payment.getCredit();
        boolean enoughCredit = credit >= amount;
        if (!enoughCredit) {
            response.setResult(ResponseEntity.badRequest().build());
            return;
        } else {
            payment.setCredit(credit - amount);
            paymentRepository.save(payment);
            Map<String, Object> curOrderStatus = orderStatuses.get(orderId);
            curOrderStatus.put("amountPaid", amount);
            orderStatuses.put(orderId, curOrderStatus);

            pendingPaymentResponses.put(orderId, response);
            int partition = orderId % Environment.numOrderInstances;

            Map<String, Object> data = Map.of("orderId", orderId, "userId", userId, "amount", amount);
            fromPaymentTemplate.send("fromPaymentPaid", partition, orderId, data);
        }
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentWasOk",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    public void paymentWasOk(Map<String, Object> response) {
        int orderId = (int) response.get("orderId");
        boolean result = (boolean) response.get("result");

        if (result) {
            // payment was fine, so we can put to paid
            Map<String, Object> curOrderStatus = orderStatuses.get(orderId);
            curOrderStatus.put("paid", true);
            orderStatuses.put(orderId, curOrderStatus);
            pendingPaymentResponses.get(orderId).setResult(ResponseEntity.ok().build());
        } else {
            // rollback the amount paid
            getPaymentRollback(response);
            pendingPaymentResponses.get(orderId).setResult(ResponseEntity.badRequest().build());
        }
    }

    public boolean cancelPayment(Integer userId, Integer orderId) {
        if (!orderStatuses.containsKey(orderId)) {
            throw new IllegalStateException("Order with Id " + orderId + " does not exist or is already checked out.");
        }

        if (!(boolean) orderStatuses.get(orderId).get("paid")) {
            throw new IllegalStateException("Order with Id " + orderId + " is not paid yet.");
        }

        Optional<Payment> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }

        Payment payment = optPaymentUser.get();
        int refund = (int) orderStatuses.get(orderId).get("amountPaid");
        int credit = payment.getCredit();
        payment.setCredit(credit + refund);
        paymentRepository.save(payment);

        orderStatuses.remove(orderId);
        return true;
    }



    public Object getPaymentStatus(Integer userId, Integer orderId) {
        if (orderStatuses.containsKey(orderId)) {
            if (userId == orderStatuses.get(orderId).get("userId")) {
                boolean paid = (boolean) orderStatuses.get(orderId).get("paid");
                Map<String, Boolean> response = Map.of("paid", paid);
                return response;
            } else {
                return ResponseEntity.notFound().build();
            }
        } else {
            return ResponseEntity.notFound().build();
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
            orderStatuses.put(orderId,  Map.of("userId", userId, "amountPaid", cost, "paid", true));
        }
        System.out.println(payment);
        int partition = orderId % Environment.numOrderInstances;
        Map<String, Object> data = Map.of("orderId", orderId, "enoughCredit", enoughCredit);
        fromPaymentTemplate.send("fromPaymentTransaction", partition, orderId, data);
    }

    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentRollback",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getPaymentRollback(Map<String, Object> request) {
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");
        int refund = (int) request.get("refund");
        Payment user = paymentRepository.getById(userId);
        orderStatuses.put(orderId,  Map.of("userId", userId, "amountPaid", 0, "paid", false));
        int credit = user.getCredit();
        user.setCredit(credit + refund);
        paymentRepository.save(user);
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
            orderStatuses.put(orderId, Map.of("userId", userId,"paid", false, "amountPaid", 0));
        } else {
            orderStatuses.remove(orderId);
        }
    }
}
