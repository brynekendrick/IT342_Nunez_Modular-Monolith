package edu.cit.nunez.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface InventoryRepository extends JpaRepository<Inventory, String> {}