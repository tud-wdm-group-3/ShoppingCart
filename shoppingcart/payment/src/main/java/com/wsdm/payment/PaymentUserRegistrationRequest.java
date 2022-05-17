package com.wsdm.payment;

public record PaymentUserRegistrationRequest(
        String firstName,
        String lastName,
        String email) {

}
