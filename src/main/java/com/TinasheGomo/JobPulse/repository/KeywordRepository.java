package com.TinasheGomo.JobPulse.repository;

import com.TinasheGomo.JobPulse.entity.Keyword;
import com.TinasheGomo.JobPulse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, UUID> {
    List<Keyword> findByUserOrderByCreatedAtDesc(User user);
}
