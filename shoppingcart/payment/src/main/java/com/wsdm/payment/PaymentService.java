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

import java.net.InetAddress;
import java.util.*;

@Service
@Transactional(isolation = Isolation.SERIALIZABLE)
public class PaymentService {

    @Value("${PARTITION_ID}")
    private int myPaymentInstanceId;

    private String myReplicaId;

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
     *
     */
    private Map<Integer, DeferredResult<ResponseEntity>> pendingPaymentResponses = new HashMap<>();

    @Autowired
    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;

        try {
            myReplicaId = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        System.out.println("Payment service started with replica-id " + myReplicaId);
    }

    public void changePayment(Integer userId, Integer orderId, Integer amount, DeferredResult<ResponseEntity> response, boolean cancellation) {
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

        if ((!cancellation && isPaid(payment, orderId)) || (cancellation && !isPaid(payment, orderId))) {
            response.setResult(ResponseEntity.notFound().build());
            return;
        }

        int paymentKey = getKey();
        if (!cancellation) {
            if (payment.getCredit() < amount) {
                response.setResult(ResponseEntity.badRequest().build());
                return;
            } else {
                pendingPaymentResponses.put(orderId, response);
                sendChangePaymentToOrder(orderId, userId, "pay", amount);

                // We must pay now because we are sure credit is enough now
                pay(payment, orderId, amount);
            }
        } else {
            pendingPaymentResponses.put(orderId, response);
            sendChangePaymentToOrder(orderId, userId, "cancel", -1);
            // We do not cancel now, because rollbacking a cancellation is much more difficult
        }
    }

    @KafkaListener(groupId = "${random.uuid}", topicPartitions = @TopicPartition(topic = "toPaymentResponse",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION_ID}", initialOffset = "0", relativeToCurrent = "true")}))
    public void paymentResponse(Map<String, Object> response) {
        String replicaId = (String) response.get("replicaId");
        int orderId = (int) response.get("orderId");
        int userId = (int) response.get("userId");
        boolean result = (boolean) response.get("result");
        String type = (String) response.get("type");
        int paymentKey = (int) response.get("paymentKey");
        Payment payment = getPaymentWithError(userId);

        // It must be processed by this replica, because only the replica can know whether he still has the
        // pending response.
        if (replicaId == myReplicaId && !payment.getProcessedPaymentKeys().contains(paymentKey)) {
            System.out.println("Received payment response " + response);
            if (type == "pay") {
                // Check if the payment went through
                if (isPaid(payment, orderId)) {
                    if (result) {
                        respondToUser(orderId, true);
                    } else {
                        // rollback the amount paid
                        getPaymentRollback(response);
                        respondToUser(orderId, false);
                    }
                } else if (result) {
                    // order thinks we paid, but we failed to save
                    sendChangePaymentToOrder(orderId, userId, "cancel", -1);
                }
            } else if (type == "cancel") {
                if (result) {
                    cancel(payment, orderId, -1);
                    respondToUser(orderId, true);
                } else {
                    respondToUser(orderId, false);
                }
            }
            Set<Integer> processedPaymentKeys = payment.getProcessedPaymentKeys();
            processedPaymentKeys.add(paymentKey);
            payment.setProcessedPaymentKeys(processedPaymentKeys);
            paymentRepository.save(payment);
        }
    }

    private void respondToUser(int orderId, boolean ok) {
        if (pendingPaymentResponses.containsKey(orderId)) {
            if (ok) {
                pendingPaymentResponses.remove(orderId).setResult(ResponseEntity.ok().build());
            } else {
                pendingPaymentResponses.remove(orderId).setResult(ResponseEntity.badRequest().build());
            }
        }
    }

    private void sendChangePaymentToOrder(int orderId, int userId, String type, int amount) {
        int paymentKey = getKey();
        int partition = Partitioner.getPartition(orderId, numOrderInstances);
        Map<String, Object> data = Map.of("orderId", orderId, "userId", userId, "type", type, "amount", amount, "myReplicaId", myReplicaId, "paymentKey", paymentKey);
        fromPaymentTemplate.send("fromPaymentPaid", partition, orderId, data);
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
        Payment payment = new Payment();

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

    private static Random rand = new Random();
    private static int getKey() {
        return rand.nextInt(1000000);
    }

}
