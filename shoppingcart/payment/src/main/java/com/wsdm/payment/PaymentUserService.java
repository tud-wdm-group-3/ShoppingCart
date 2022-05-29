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
        paymentUserRepository.save(paymentUser);
        return paymentUser.getId();
    }

    public Optional<PaymentUser> findUser(Integer userId) {
        return paymentUserRepository.findById(userId);
    }

    @Transactional
    public boolean addFunds(Integer userId, Integer amount) {
        Optional<PaymentUser> paymentUser = paymentUserRepository.findById(userId);
        if (paymentUser.isEmpty()) {
            throw new IllegalStateException("user with Id " + userId + " does not exist");
        }
        paymentUser.get().setCredit(paymentUser.get().getCredit() + amount);
        return true;  // TODO return false when fail for some reason
    }


    @Autowired
    private KafkaTemplate<Integer, Boolean> fromPaymentTemplate;

    @Transactional
    @KafkaListener(topicPartitions = @TopicPartition(topic = "toPaymentTransaction",
            partitionOffsets = {@PartitionOffset(partition = "${myPaymentInstanceId}", initialOffset = "0")}))
    protected void getPaymentTransaction(ConsumerRecord<Integer, Pair<Integer, Integer>> request) {
        int orderId = request.key();
        int userId = request.value().getFirst();
        int cost = request.value().getSecond();
        PaymentUser user = paymentUserRepository.getById(userId);
        int credit = user.getCredit();
        boolean result = credit >= cost;
        if (result) {
            user.setCredit(credit - cost);
        }
        int partition = orderId % Environment.numOrderInstances;
        fromPaymentTemplate.send("fromPaymentTransaction", partition, orderId, result);
    }
}
