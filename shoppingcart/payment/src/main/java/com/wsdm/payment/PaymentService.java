package com.wsdm.payment;

import com.wsdm.payment.utils.Partitioner;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.*;

@Service
@Transactional(isolation = Isolation.SERIALIZABLE)
public class PaymentService {

    @Value("${PARTITION_ID}")
    private int myPaymentInstanceId;

    private int numStockInstances = 2;

    private int numPaymentInstances = 2;

    private int numOrderInstances = 2;

    final PaymentRepository paymentRepository;

    /**
     * A log of current order statuses
     */
    private Map<Integer, Set<Integer>> existingOrders = new HashMap<>();

    /**
     * Maps orderId to deferredResult.
     */
    private Map<Integer, DeferredResult<ResponseEntity>> pendingPaymentResponses = new HashMap<>();

    @Autowired
    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public void makePayment(Integer userId, Integer orderId, Integer amount, DeferredResult<ResponseEntity> response) {
        if (!orderExists(userId, orderId)) {
            response.setResult(ResponseEntity.notFound().build());
            return;
        }
        Optional<Payment> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            response.setResult(ResponseEntity.notFound().build());
            return;
        }
        Payment payment = optPaymentUser.get();

        if (isPaid(payment, orderId)) {
            response.setResult(ResponseEntity.notFound().build());
            return;
        }

        if (payment.getCredit() < amount) {
            response.setResult(ResponseEntity.badRequest().build());
            return;
        } else {
            pendingPaymentResponses.put(orderId, response);

            int partition = Partitioner.getPartition(orderId, numOrderInstances);
            Map<String, Object> data = Map.of("orderId", orderId, "userId", userId, "amount", amount);
            fromPaymentTemplate.send("fromPaymentPaid", partition, orderId, data);

            pay(payment, orderId, amount);
        }
    }

    @KafkaListener(groupId = "${random.uuid}", topicPartitions = @TopicPartition(topic = "toPaymentWasOk",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION_ID}", initialOffset = "0", relativeToCurrent = "true")}))
    public void paymentWasOk(Map<String, Object> response) {
        System.out.println("Received payment response " + response);
        int orderId = (int) response.get("orderId");
        boolean result = (boolean) response.get("result");

        if (pendingPaymentResponses.containsKey(orderId)) {
            if (result) {
                // payment was fine, so we can put to paid
                pendingPaymentResponses.remove(orderId).setResult(ResponseEntity.ok().build());
            } else {
                // rollback the amount paid
                getPaymentRollback(response);
                pendingPaymentResponses.remove(orderId).setResult(ResponseEntity.badRequest().build());
            }
        }
    }

    public boolean cancelPayment(Integer userId, Integer orderId) {
        if (!orderExists(userId, orderId)) {
            return false;
        }
        Optional<Payment> optPaymentUser = findUser(userId);
        if (optPaymentUser.isEmpty()) {
            return false;
        }
        Payment payment = optPaymentUser.get();

        if (!isPaid(payment, orderId)) {
            return false;
        }

        int partition = Partitioner.getPartition(orderId, numOrderInstances);
        Map<String, Object> data = Map.of("orderId", orderId, "userId", userId);
        fromPaymentTemplate.send("fromPaymentCancelled", partition, orderId, data);

        cancel(payment, orderId, -1);

        return true;
    }

    public Object getPaymentStatus(Integer userId, Integer orderId) {
        if (orderExists(userId, orderId)) {
            Optional<Payment> optPayment = findUser(userId);
            if (!optPayment.isPresent()) {
                return false;
            }
            Map<String, Boolean> response = Map.of("paid", isPaid(optPayment.get(), orderId));
            return response;
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
        int globalId = payment.getLocalId() * numPaymentInstances + myPaymentInstanceId;
        payment.setUserId(globalId);
        paymentRepository.save(payment);

        return globalId;
    }

    public Optional<Payment> findUser(Integer userId) {
        int localId = (userId - myPaymentInstanceId) / numPaymentInstances;
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

    @KafkaListener(groupId = "${random.uuid}", topicPartitions = @TopicPartition(topic = "toPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION_ID}", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getPaymentTransaction(Map<String, Object> request) {
        System.out.println("Received payment transaction " + request);
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");
        int cost = (int) request.get("totalCost");

        Payment payment = getPaymentWithError(userId);
        boolean paid = pay(payment, orderId, cost);

        System.out.println(payment);
        int partition = orderId % numOrderInstances;
        Map<String, Object> data = Map.of("orderId", orderId, "enoughCredit", paid);
        fromPaymentTemplate.send("fromPaymentTransaction", partition, orderId, data);
    }

    @KafkaListener(groupId = "${random.uuid}", topicPartitions = @TopicPartition(topic = "toPaymentRollback",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION_ID}", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getPaymentRollback(Map<String, Object> request) {
        System.out.println("Received payment rollback " + request);
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");
        int refund = (int) request.get("refund");
        Payment payment = paymentRepository.getById(userId);

        cancel(payment, orderId, refund);
    }

    /**
     * Used to initialize cache of orderIds, so false relativeToCurrent.
     * @param request
     */
    @KafkaListener(groupId = "${random.uuid}", topicPartitions = @TopicPartition(topic = "toPaymentOrderExists",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION_ID}", initialOffset = "0", relativeToCurrent = "false")}))
    protected void orderExists(Map<String, Integer> request) {
        System.out.println("Received order exists " + request);
        int orderId = request.get("orderId");
        int method = request.get("method");
        int userId = request.get("userId");

        // method = 0 for add
        if (method == 0 ) {
            // We do not care if user does not exist, will fail later anyway
            if (!existingOrders.containsKey(userId)) {
                existingOrders.put(userId, new HashSet<>());
            }
            existingOrders.get(userId).add(orderId);
        } else {
            assert(orderExists(userId, orderId));
            existingOrders.get(userId).remove(orderId);
        }
    }

    private boolean orderExists(int userId, int orderId) {
        return existingOrders.containsKey(userId) && existingOrders.get(userId).contains(orderId);
    }

    private boolean isPaid(Payment payment, int orderId) {
        return payment.getOrderIdToPaidAmount().containsKey(orderId);
    }

    private boolean pay(Payment payment, int orderId, int cost) {
        int credit = payment.getCredit();
        boolean enoughCredit = credit >= cost;
        if (enoughCredit) {
            if (!isPaid(payment, orderId)) {
                Map<Integer, Integer> orderIdToPaid = payment.getOrderIdToPaidAmount();
                orderIdToPaid.put(orderId, cost);
                payment.setOrderIdToPaidAmount(orderIdToPaid);
                payment.setCredit(credit - cost);
                paymentRepository.save(payment);
                return true;
            }
        }
        return false;
    }

    private boolean cancel(Payment payment, int orderId, int amount) {
        if (isPaid(payment, orderId)) {
            Map<Integer, Integer> orderIdToPaidAmount = payment.getOrderIdToPaidAmount();
            int refund = orderIdToPaidAmount.remove(orderId);
            assert(amount != -1 || amount == refund);
            payment.setCredit(payment.getCredit() + refund);
            payment.setOrderIdToPaidAmount(orderIdToPaidAmount);
            paymentRepository.save(payment);
            return true;
        }
        return false;
    }

    private Payment getPaymentWithError(int userId) {
        Optional<Payment> optPayment = findUser(userId);
        if (!optPayment.isPresent()) {
            throw new IllegalStateException("User with userId " + userId + " does not exist.");
        }
        return optPayment.get();
    }

}
