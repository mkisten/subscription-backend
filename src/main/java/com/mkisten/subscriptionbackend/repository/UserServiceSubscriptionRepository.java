package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.ServiceCode;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserServiceSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserServiceSubscriptionRepository extends JpaRepository<UserServiceSubscription, Long> {
    Optional<UserServiceSubscription> findByUserAndServiceCode(User user, ServiceCode serviceCode);
    List<UserServiceSubscription> findByUser(User user);
    List<UserServiceSubscription> findByServiceCode(ServiceCode serviceCode);
    List<UserServiceSubscription> findByServiceCodeAndSubscriptionEndDateBefore(ServiceCode serviceCode, LocalDate date);
    List<UserServiceSubscription> findByServiceCodeAndSubscriptionEndDateAfter(ServiceCode serviceCode, LocalDate date);
}
