package com.wsdm.payment;

import org.springframework.beans.factory.annotation.Autowired;
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

    public PaymentUser findUser(Integer userId) {
        PaymentUser paymentUser = paymentUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("user with Id " + userId + " does not exist"));
        return paymentUser;
//        Optional<PaymentUser> paymentUser = paymentUserRepository.findById(userId);
//        if (paymentUser.isPresent()) {
//            System.out.println("asdf");
//        }
//        return paymentUser;
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

//    @Transactional  // the entity goes into a managed state, you dont have to write JPQL queries
//    public void updateStudent(Long studentId, String name, String email) {
//        Student student = studentRepository.findById(studentId)
//                .orElseThrow(() -> new IllegalStateException(
//                        "student with id " + studentId + " does not exist"));
//        if (name != null && name.length() > 0 && !Objects.equals(student.getName(), name)) {
//            student.setName(name);
//        }
//        if (email != null && email.length() > 0 && !Objects.equals(student.getEmail(), email)) {
//            Optional<Student> studentOptional = studentRepository
//                    .findStudentByEmail(email);
//            if (studentOptional.isPresent()) {
//                throw new IllegalStateException("email taken");
//            }
//            student.setEmail(email);
//        }
//    }
}
