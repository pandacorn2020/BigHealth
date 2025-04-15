package com.bighealth.repository;

import com.bighealth.entity.KGCommunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KGCommunityRepository extends JpaRepository<KGCommunity, String> {
}