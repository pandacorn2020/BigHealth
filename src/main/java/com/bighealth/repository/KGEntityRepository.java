package com.bighealth.repository;

import com.bighealth.entity.KGEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KGEntityRepository extends JpaRepository<KGEntity, String> {
}