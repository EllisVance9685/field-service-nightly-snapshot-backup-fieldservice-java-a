package example.fieldservice;

import java.time.Clock;
import java.util.List;

public final class FieldServiceSnapshotJob {
    private FieldServiceSnapshotJob() {}

    public static void main(String[] args) throws Exception {
        SnapshotConfig config = SnapshotConfig.load();
        NightlySnapshotService service = new NightlySnapshotService(
                config, new InfraiStorageClient(config.apiKey()), Clock.system(config.zone()));

        List<WorkOrderSnapshot.WorkOrder> workOrders = List.of(
                new WorkOrderSnapshot.WorkOrder(
                        "WO-1048", WorkOrderSnapshot.DispatchStatus.COMPLETED, "TECH-17",
                        List.of("work-orders/WO-1048/arrival.jpg", "work-orders/WO-1048/completion.jpg"),
                        new WorkOrderSnapshot.FollowUp(false, "Meter seal recorded")),
                new WorkOrderSnapshot.WorkOrder(
                        "WO-1051", WorkOrderSnapshot.DispatchStatus.ON_SITE, "TECH-09",
                        List.of("work-orders/WO-1051/arrival.jpg"),
                        new WorkOrderSnapshot.FollowUp(true, "Customer signature due")));

        NightlySnapshotService.SnapshotReceipt receipt = service.run(workOrders);
        System.out.printf("snapshot stored: bucket=%s key=%s workOrders=%d dispatch=%s%n",
                receipt.bucket(), receipt.objectKey(), receipt.workOrderCount(), receipt.dispatchCounts());
    }
}

