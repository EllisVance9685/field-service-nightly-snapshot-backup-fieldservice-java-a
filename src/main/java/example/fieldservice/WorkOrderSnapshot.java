package example.fieldservice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WorkOrderSnapshot(
        LocalDate businessDate,
        Instant capturedAt,
        List<WorkOrder> workOrders) {

    public record WorkOrder(
            String workOrderId,
            DispatchStatus dispatchStatus,
            String technicianId,
            List<String> photoObjectKeys,
            FollowUp followUp) {}

    public record FollowUp(boolean required, String note) {}

    public enum DispatchStatus {
        ASSIGNED, EN_ROUTE, ON_SITE, COMPLETED
    }
}

