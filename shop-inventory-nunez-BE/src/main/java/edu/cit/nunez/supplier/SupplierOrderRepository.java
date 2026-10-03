package edu.cit.nunez.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);

    List<SupplierOrder> findByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);

    Optional<SupplierOrder> findByPoNumber(String poNumber);
}