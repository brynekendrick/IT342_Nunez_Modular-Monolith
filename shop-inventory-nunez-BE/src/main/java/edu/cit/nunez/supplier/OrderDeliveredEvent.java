package edu.cit.nunez.supplier;

public record OrderDeliveredEvent(String productId, int unitsToRestock) {

}
