package com.wsdm.payment;

import org.springframework.stereotype.Service;

@Service
public record PaymentUserService(PaymentUserRepository paymentUserRepository) {
    public void registerUser(PaymentUserRegistrationRequest request) {
        PaymentUser paymentUser = PaymentUser.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .build();

        paymentUserRepository.save(paymentUser);
    }
}
