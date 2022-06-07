package com.wsdm.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
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
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }
        Payment payment = optPaymentUser.get();
        payment.setCredit(payment.getCredit() + amount);
        paymentRepository.save(payment);
        return true;  // TODO return false when fail for some reason
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
        Optional<Payment> optPayment = findUser(userId);
        if (!optPayment.isPresent()) {
            throw new IllegalStateException("unknown user");
        }
        Payment payment = optPayment.get();
        System.out.println(payment);
        int credit = payment.getCredit();
        boolean enoughCredit = credit >= cost;
        if (enoughCredit) {
            System.out.println("enough");
            payment.setCredit(credit - cost);
            paymentRepository.save(payment);
        }
        System.out.println(payment);
        int partition = orderId % Environment.numOrderInstances;
        Map<String, Object> data = Map.of("orderId", orderId, "enoughCredit", enoughCredit);
        fromPaymentTemplate.send("fromPaymentTransaction", partition, orderId, data);
    }

    @Transactional
    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentRollback",
            partitionOffsets = {@PartitionOffset(partition = "0", initialOffset = "0", relativeToCurrent = "true")}))
    protected void getPaymentRollback(ConsumerRecord<Integer, Map<String, Object>> request) {
        int userId = (int) request.value().get("userId");
        int refund = (int) request.value().get("refund");
        Payment user = paymentRepository.getById(userId);
        int credit = user.getCredit();
        // TODO: call make payment
        user.setCredit(credit + refund);
    }
}
