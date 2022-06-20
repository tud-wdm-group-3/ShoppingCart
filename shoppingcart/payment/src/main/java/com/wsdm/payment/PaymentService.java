package com.wsdm.payment;

import com.wsdm.payment.utils.ExistingOrders;
import com.wsdm.payment.utils.NameUtils;
import com.wsdm.payment.utils.Partitioner;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.support.StandardEvaluationContext;
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
public class PaymentService {

    @Autowired
    Environment env;

    private String myReplicaId = NameUtils.getHostname();
    public String getMyReplicaId() {
        return myReplicaId;
    }

    final PaymentRepository paymentRepository;

    /**
     * Maps orderId to deferredResult.
     *
     */
    private Map<Integer, DeferredResult<ResponseEntity>> pendingPaymentResponses = new HashMap<>();

    @Autowired
    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
        // System.out.println("Payment service started with replica-id " + myReplicaId);
    }

    public List<Payment> dump() {
        return paymentRepository.findAll();
    }

    
    public void changePayment(Integer userId, Integer orderId, Double amount, DeferredResult<ResponseEntity> response, boolean cancellation) {
        if (!ExistingOrders.orderExists(userId, orderId)) {
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

    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "toPaymentResponse",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
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
        // System.out.println("Received payment response " + response);
        if (replicaId.contains(myReplicaId) && !payment.getProcessedPaymentKeys().contains(paymentKey)) {
            if (type.contains("pay")) {
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
            } else if (type.contains("cancel")) {
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

    private void sendChangePaymentToOrder(int orderId, int userId, String type, double amount) {
        int paymentKey = getKey();
        int partition = Partitioner.getPartition(orderId, Partitioner.Service.ORDER, env);
        Map<String, Object> data = Map.of("orderId", orderId, "userId", userId, "type", type, "amount", amount, "replicaId", myReplicaId, "paymentKey", paymentKey);
        fromPaymentTemplate.send("fromPaymentPaid", partition, orderId, data);
    }

    public Object getPaymentStatus(Integer userId, Integer orderId) {
        if (ExistingOrders.orderExists(userId, orderId)) {
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
        return payment.getUserId(env);
    }

    public Optional<Payment> findUser(Integer userId) {
        return paymentRepository.findById(Payment.getLocalId(userId, env));
    }

    public boolean addFunds(Integer userId, Double amount) {
        if (amount <= 0) {
            return false;
        }
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

    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "toPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getPaymentTransaction(Map<String, Object> request) {
        // System.out.println("Received payment transaction " + request);
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");
        double cost = (double) request.get("totalCost");

        Payment payment = getPaymentWithError(userId);
        boolean paid = pay(payment, orderId, cost);
        int partition = Partitioner.getPartition(orderId, Partitioner.Service.ORDER, env);
        Map<String, Object> data = Map.of("orderId", orderId, "enoughCredit", paid);
        fromPaymentTemplate.send("fromPaymentTransaction", partition, orderId, data);
    }

    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "toPaymentRollback",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "true")}))
    protected void getPaymentRollback(Map<String, Object> request) {
        // System.out.println("Received payment rollback " + request);
        int orderId = (int) request.get("orderId");
        int userId = (int) request.get("userId");

        double refund = (double) request.get("refund");
        Payment payment = getPaymentWithError(userId);

        cancel(payment, orderId, refund);
    }

    /**
     * Used to initialize cache of orderIds, so false relativeToCurrent.
     * @param request
     */
    @KafkaListener(groupId = "#{__listener.myReplicaId}", topicPartitions = @TopicPartition(topic = "toPaymentOrderExists",
            partitionOffsets = {@PartitionOffset(partition = "${PARTITION}", initialOffset = "-1", relativeToCurrent = "false")}))
    protected void receiveOrderExists(Map<String, Object> request) {
        // System.out.println("Received order exists " + request);
        int orderId = (int) request.get("orderId");
        String method = (String) request.get("method");
        int userId = (int) request.get("userId");

        if (method.contains("create")) {
            // We do not care if user does not exist, will fail later anyway
            ExistingOrders.addOrder(userId, orderId);
        } else if (method.contains("delete")){
            ExistingOrders.removeOrder(userId, orderId);
        }
    }

    private boolean isPaid(Payment payment, int orderId) {
        return payment.getOrderIdToPaidAmount().containsKey(orderId);
    }

    
    public boolean pay(Payment payment, int orderId, double cost) {
        double credit = payment.getCredit();
        boolean enoughCredit = credit >= cost;
        if (enoughCredit) {
            if (!isPaid(payment, orderId)) {
                Map<Integer, Double> orderIdToPaid = payment.getOrderIdToPaidAmount();
                orderIdToPaid.put(orderId, cost);
                payment.setOrderIdToPaidAmount(orderIdToPaid);
                payment.setCredit(credit - cost);
                paymentRepository.save(payment);
                return true;
            }
        }
        return false;
    }

    private boolean cancel(Payment payment, int orderId, double amount) {
        if (isPaid(payment, orderId)) {
            Map<Integer, Double> orderIdToPaidAmount = payment.getOrderIdToPaidAmount();
            double refund = orderIdToPaidAmount.remove(orderId);
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
