package com.bighealth.repository;

import com.bighealth.entity.KGRelationship;
import com.bighealth.entity.KGRelationshipKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KGRelationshipRepository extends JpaRepository<KGRelationship, KGRelationshipKey> {
}
