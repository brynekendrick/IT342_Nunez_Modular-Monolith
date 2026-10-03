package edu.cit.nunez.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    /** Prefer this over findByReason — multi-line orders share the same reason/external ref. */
    List<Order> findAllByReason(String reason);
}
