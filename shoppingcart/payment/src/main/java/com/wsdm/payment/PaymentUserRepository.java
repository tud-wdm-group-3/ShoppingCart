package com.wsdm.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentUserRepository extends JpaRepository<PaymentUser, Integer> {

}
