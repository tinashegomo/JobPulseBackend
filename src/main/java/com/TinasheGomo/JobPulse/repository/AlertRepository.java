package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByUser(User user);
    List<Alert> findByUserAndActiveTrue(User user);
    List<Alert> findByActiveTrue();
}