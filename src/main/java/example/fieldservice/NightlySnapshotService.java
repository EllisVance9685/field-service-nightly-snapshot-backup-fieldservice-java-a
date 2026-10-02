package example.fieldservice;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NightlySnapshotService {
    private final SnapshotConfig config;
    private final InfraiStorageClient storage;
    private final Clock clock;

    public NightlySnapshotService(SnapshotConfig config, InfraiStorageClient storage, Clock clock) {
        this.config = config;
        this.storage = storage;
        this.clock = clock;
    }

    public SnapshotReceipt run(List<WorkOrderSnapshot.WorkOrder> workOrders) throws Exception {
        LocalDate businessDate = LocalDate.now(clock.withZone(config.zone()));
        String objectKey = objectKey(config.prefix(), businessDate);
        WorkOrderSnapshot snapshot = new WorkOrderSnapshot(businessDate, clock.instant(), List.copyOf(workOrders));
        byte[] document = encode(snapshot).getBytes(StandardCharsets.UTF_8);

        storage.ensureBucket(config.bucket());
        String uploadUrl = storage.presignPut(config.bucket(), objectKey, document.length, objectKey);
        storage.putSigned(uploadUrl, document, "application/json");
        return new SnapshotReceipt(config.bucket(), objectKey, workOrders.size(), statusCounts(workOrders));
    }

    static String objectKey(String prefix, LocalDate date) {
        return prefix + "/" + date + "/work-orders.json";
    }

    static String encode(WorkOrderSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append("{\"businessDate\":").append(quote(snapshot.businessDate().toString()));
        json.append(",\"capturedAt\":").append(quote(snapshot.capturedAt().toString()));
        json.append(",\"workOrders\":[");
        for (int i = 0; i < snapshot.workOrders().size(); i++) {
            if (i > 0) json.append(',');
            WorkOrderSnapshot.WorkOrder order = snapshot.workOrders().get(i);
            json.append("{\"workOrderId\":").append(quote(order.workOrderId()));
            json.append(",\"dispatchStatus\":").append(quote(order.dispatchStatus().name()));
            json.append(",\"technicianId\":").append(quote(order.technicianId()));
            json.append(",\"photoObjectKeys\":[");
            for (int p = 0; p < order.photoObjectKeys().size(); p++) {
                if (p > 0) json.append(',');
                json.append(quote(order.photoObjectKeys().get(p)));
            }
            json.append("]");
            json.append(",\"followUp\":{\"required\":").append(order.followUp().required());
            json.append(",\"note\":").append(quote(order.followUp().note())).append("}}");
        }
        return json.append("]}").toString();
    }

    private static Map<WorkOrderSnapshot.DispatchStatus, Long> statusCounts(
            List<WorkOrderSnapshot.WorkOrder> workOrders) {
        Map<WorkOrderSnapshot.DispatchStatus, Long> counts = new EnumMap<>(WorkOrderSnapshot.DispatchStatus.class);
        for (WorkOrderSnapshot.WorkOrder order : workOrders) {
            counts.merge(order.dispatchStatus(), 1L, Long::sum);
        }
        return Map.copyOf(counts);
    }

    private static String quote(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    public record SnapshotReceipt(
            String bucket,
            String objectKey,
            int workOrderCount,
            Map<WorkOrderSnapshot.DispatchStatus, Long> dispatchCounts) {}
}
