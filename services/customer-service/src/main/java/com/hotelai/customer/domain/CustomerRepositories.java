package com.hotelai.customer.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;

interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByIdentityId(UUID identityId);
    Optional<CustomerProfile> findFirstByEmailIgnoreCaseAndStatus(
            String email, CustomerProfile.Status status);

    @Query("""
            select c from CustomerProfile c
            where c.status = com.hotelai.customer.domain.CustomerProfile.Status.ACTIVE
              and (:query = '' or lower(c.fullName) like lower(concat('%', :query, '%'))
                   or lower(c.email) like lower(concat('%', :query, '%'))
                   or c.phone like concat('%', :query, '%'))
            order by c.updatedAt desc
            """)
    Page<CustomerProfile> search(@Param("query") String query, Pageable pageable);
}

interface CustomerNoteRepository extends JpaRepository<CustomerNote, UUID> {
    Page<CustomerNote> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}

interface MergeProposalRepository extends JpaRepository<MergeProposal, UUID> {
    Page<MergeProposal> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<MergeProposal> findByStatusOrderByCreatedAtDesc(MergeProposal.Status status, Pageable pageable);
}
interface CustomerAuditRepository extends JpaRepository<CustomerAudit, UUID> {}
interface ProcessedIdentityEventRepository extends JpaRepository<ProcessedIdentityEvent, String> {}
interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.occurredAt")
    List<OutboxEvent> findPending(Pageable pageable);
}
