package com.wsdm.payment;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentUserService {

    private final PaymentUserRepository paymentUserRepository;

    @Autowired
    public PaymentUserService(PaymentUserRepository paymentUserRepository) {
        this.paymentUserRepository = paymentUserRepository;
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
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }
        PaymentUser paymentUser = optPaymentUser.get();
        paymentUser.setCredit(paymentUser.getCredit() + amount);
        paymentUserRepository.save(paymentUser);
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
        }
        System.out.println(paymentUser);
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
        PaymentUser user = paymentUserRepository.getById(userId);
        int credit = user.getCredit();
        // TODO: call make payment
        user.setCredit(credit + refund);
    }
}
