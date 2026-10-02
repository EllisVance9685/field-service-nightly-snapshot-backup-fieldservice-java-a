package example.fieldservice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class NightlySnapshotDecisionTest {
    public static void main(String[] args) {
        WorkOrderSnapshot snapshot = new WorkOrderSnapshot(
                LocalDate.parse("2026-09-30"), Instant.parse("2026-09-30T23:00:00Z"),
                List.of(new WorkOrderSnapshot.WorkOrder(
                        "WO-77", WorkOrderSnapshot.DispatchStatus.COMPLETED, "TECH-4",
                        List.of("work-orders/WO-77/proof.jpg"),
                        new WorkOrderSnapshot.FollowUp(true, "Compliance review"))));

        String key = NightlySnapshotService.objectKey("nightly", snapshot.businessDate());
        String document = NightlySnapshotService.encode(snapshot);
        check(key.equals("nightly/2026-09-30/work-orders.json"), "business date must determine the stable key");
        check(document.contains("\"dispatchStatus\":\"COMPLETED\""), "dispatch status must be retained");
        check(document.contains("work-orders/WO-77/proof.jpg"), "photo evidence key must be retained");
        check(document.contains("\"required\":true"), "follow-up decision must be retained");
        System.out.println("PASS nightly key and audit fields are deterministic");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

