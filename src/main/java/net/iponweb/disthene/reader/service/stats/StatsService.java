package net.iponweb.disthene.reader.service.stats;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.UniformReservoir;
import com.google.common.util.concurrent.AtomicDouble;
import net.iponweb.disthene.reader.config.StatsConfiguration;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.DataOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Andrei Ivanov
 */
public class StatsService {

    private final Logger logger = Logger.getLogger(StatsService.class);

    private final StatsConfiguration statsConfiguration;

    private final ConcurrentMap<String, StatsRecord> stats = new ConcurrentHashMap<>();

    private final StatsRecord globalStats = new StatsRecord();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public StatsService(StatsConfiguration statsConfiguration) {
        this.statsConfiguration = statsConfiguration;

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                flush();
            }
        }, 60 - ((System.currentTimeMillis() / 1000L) % 60), statsConfiguration.getInterval(), TimeUnit.SECONDS);
    }

    private StatsRecord getStatsRecord(String tenant) {
        StatsRecord statsRecord = stats.get(tenant);
        if (statsRecord == null) {
            StatsRecord newStatsRecord = new StatsRecord();
            statsRecord = stats.putIfAbsent(tenant, newStatsRecord);
            if (statsRecord == null) {
                statsRecord = newStatsRecord;
            }
        }

        return statsRecord;
    }

    public void incRenderRequests(String tenant) {
        getStatsRecord(tenant).incRenderRequests();
        globalStats.incRenderRequests();
    }

    public void incRenderPointsRead(String tenant, int inc) {
        getStatsRecord(tenant).incRenderPointsRead(inc);
        globalStats.incRenderPointsRead(inc);
    }

    public void incRenderPathsRead(String tenant, int inc) {
        getStatsRecord(tenant).incRenderPathsRead(inc);
        globalStats.incRenderPathsRead(inc);
    }

    public void incPathsRequests(String tenant) {
        getStatsRecord(tenant).incPathsRequests();
        globalStats.incPathsRequests();
    }

    public void incThrottleTime(String tenant, double value) {
        getStatsRecord(tenant).incThrottled(value);
        globalStats.incThrottled(value);
    }

    public void incTimedOutRequests(String tenant) {
        getStatsRecord(tenant).incTimedOutRequests();
        globalStats.incTimedOutRequests();
    }

    public void addResponseTime(String tenant, long millis) {
        getStatsRecord(tenant).addResponseTime(millis);
        globalStats.addResponseTime(millis);
    }

    private synchronized void flush() {
        Map<String, StatsSnapshot> statsToFlush = new HashMap<>();

        for (ConcurrentMap.Entry<String, StatsRecord> entry : stats.entrySet()) {
            statsToFlush.put(entry.getKey(), entry.getValue().reset());
        }

        long timestamp = DateTime.now(DateTimeZone.UTC).withSecondOfMinute(0).withMillisOfSecond(0).getMillis() / 1000L;

        try {
            Socket connection = new Socket(statsConfiguration.getCarbonHost(), statsConfiguration.getCarbonPort());
            DataOutputStream dos = new DataOutputStream(connection.getOutputStream());

            for (Map.Entry<String, StatsSnapshot> entry : statsToFlush.entrySet()) {
                String tenant = entry.getKey();
                StatsSnapshot statsSnapshot = entry.getValue();

                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".render_requests " + statsSnapshot.getRenderRequests() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".render_paths_read " + statsSnapshot.getRenderPathsRead() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".render_points_read " + statsSnapshot.getRenderPointsRead() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".paths_requests " + statsSnapshot.getPathsRequests() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".throttled " + statsSnapshot.getThrottled() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".timed_out_requests " + statsSnapshot.getTimedOutRequests() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");

                // response response time
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".response.percentiles.50 " + statsSnapshot.getResponseTimesMedian() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".response.percentiles.75 " + statsSnapshot.getResponseTimes75thPercentile() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".response.percentiles.95 " + statsSnapshot.getResponseTimes95thPercentile() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".response.percentiles.99 " + statsSnapshot.getResponseTimes99thPercentile() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
                dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.tenants." + tenant + ".response.total " + statsSnapshot.getTotalResponseTime() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            }

            StatsSnapshot statsSnapshot = globalStats.reset();

            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.render_requests " + statsSnapshot.getRenderRequests() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.render_paths_read " + statsSnapshot.getRenderPathsRead() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.render_points_read " + statsSnapshot.getRenderPointsRead() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.paths_requests " + statsSnapshot.getPathsRequests() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.throttled " + statsSnapshot.getThrottled() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.timed_out_requests " + statsSnapshot.getTimedOutRequests() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.response.total " + statsSnapshot.getTotalResponseTime() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");

            // response response time
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.response.percentiles.50 " + statsSnapshot.getResponseTimesMedian() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.response.percentiles.75 " + statsSnapshot.getResponseTimes75thPercentile() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.response.percentiles.95 " + statsSnapshot.getResponseTimes95thPercentile() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");
            dos.writeBytes(statsConfiguration.getHostname() + ".disthene-reader.response.percentiles.99 " + statsSnapshot.getResponseTimes99thPercentile() + " " + timestamp + " " + statsConfiguration.getTenant() + "\n");

            dos.flush();
            connection.close();
        } catch (Exception e) {
            logger.error("Failed to send stats", e);
        }
    }

    public synchronized void shutdown() {
        scheduler.shutdown();
    }

    private static class StatsRecord {
        private final AtomicLong renderRequests = new AtomicLong(0);
        private final AtomicLong renderPathsRead = new AtomicLong(0);
        private final AtomicLong renderPointsRead = new AtomicLong(0);
        private final AtomicLong pathsRequests = new AtomicLong(0);
        private final AtomicDouble throttled = new AtomicDouble(0);
        private final AtomicLong timedOutRequests = new AtomicLong(0);
        private Histogram responseTimes = new Histogram(new UniformReservoir());
        private final AtomicLong totalResponseTime = new AtomicLong(0);

        public StatsRecord() {
        }

        /**
         * Resets the stats to zeroes and returns a snapshot of the record
         * @return snapshot of the record
         */
        public StatsSnapshot reset() {
            long renderRequestsSnapshot = renderRequests.getAndSet(0);
            long renderPathsReadSnapshot = renderPathsRead.getAndSet(0);
            long renderPointsReadSnapshot = renderPointsRead.getAndSet(0);
            long pathsRequestsSnapshot = pathsRequests.getAndSet(0);
            double throttledSnapshot = throttled.getAndSet(0);
            long timedOutRequestsSnapshot = timedOutRequests.getAndSet(0);
            Snapshot responseTimesSnapshot = responseTimes.getSnapshot();
            responseTimes = new Histogram(new UniformReservoir());
            long totalResponseTimeSnapshot = totalResponseTime.getAndSet(0);

            return new StatsSnapshot(renderRequestsSnapshot, renderPathsReadSnapshot, renderPointsReadSnapshot, pathsRequestsSnapshot, throttledSnapshot, timedOutRequestsSnapshot, responseTimesSnapshot, totalResponseTimeSnapshot);
        }

        public void incRenderRequests() {
            renderRequests.addAndGet(1);
        }

        public void incRenderPathsRead(int inc) {
            renderPathsRead.addAndGet(inc);
        }

        public void incRenderPointsRead(int inc) {
            renderPointsRead.addAndGet(inc);
        }

        public void incPathsRequests() {
            pathsRequests.addAndGet(1);
        }

        public void incThrottled(double value) {
            throttled.addAndGet(value);
        }

        public void incTimedOutRequests() {
            timedOutRequests.addAndGet(1);
        }

        public void addResponseTime(long millis) {
            responseTimes.update(millis);
            totalResponseTime.addAndGet(millis);
        }
    }

    private static class StatsSnapshot {
        private final long renderRequests;
        private final long renderPathsRead;
        private final long renderPointsRead;
        private final long pathsRequests;
        private final double throttled;
        private final long timedOutRequests;
        private final Snapshot responseTimes;
        private final long totalResponseTime;

        public StatsSnapshot(long renderRequests, long renderPathsRead, long renderPointsRead, long pathsRequests, double throttled, long timedOutRequests, Snapshot responseTimes, long totalResponseTime) {
            this.renderRequests = renderRequests;
            this.renderPathsRead = renderPathsRead;
            this.renderPointsRead = renderPointsRead;
            this.pathsRequests = pathsRequests;
            this.throttled = throttled;
            this.timedOutRequests = timedOutRequests;
            this.responseTimes = responseTimes;
            this.totalResponseTime = totalResponseTime;
        }

        public long getRenderRequests() {
            return renderRequests;
        }

        public long getRenderPathsRead() {
            return renderPathsRead;
        }

        public long getRenderPointsRead() {
            return renderPointsRead;
        }

        public long getPathsRequests() {
            return pathsRequests;
        }

        public double getThrottled() {
            return throttled;
        }

        public long getTimedOutRequests() {
            return timedOutRequests;
        }

        public long getTotalResponseTime() {
            return totalResponseTime;
        }

        public double getResponseTimesMedian() {
            return responseTimes.getMedian();
        }

        public double getResponseTimes75thPercentile() {
            return responseTimes.get75thPercentile();
        }

        public double getResponseTimes95thPercentile() {
            return responseTimes.get95thPercentile();
        }

        public double getResponseTimes99thPercentile() {
            return responseTimes.get99thPercentile();
        }
    }

}
