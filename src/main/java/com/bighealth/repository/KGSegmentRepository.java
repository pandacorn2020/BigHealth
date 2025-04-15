package com.bighealth.repository;

import com.bighealth.entity.KGSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KGSegmentRepository extends JpaRepository<KGSegment, Long> {
}