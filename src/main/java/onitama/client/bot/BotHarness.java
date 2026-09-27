package onitama.client.bot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Orchestrates a load-test run: starts N bots as threads, waits for them to
 * finish, aggregates the statistics and writes two CSV files into the results
 * directory ({@code latencies.csv} per-move round-trips, {@code summary.csv}
 * one row per run). The humans run the experiments and record the numbers in
 * the report — this harness only measures and reports what actually happened.
 */
public final class BotHarness {

    /** Aggregated outcome of one harness run. */
    public record Summary(int botCount, int gamesRequested, int gamesCompleted,
                          int failedBots, int movesMeasured, long wallMillis,
                          double gamesPerMinute, double meanRttMillis,
                          double p50RttMillis, double p95RttMillis, double maxRttMillis) {
    }

    private final Path resultsDir;

    /** Creates the harness; CSV files land in {@code resultsDir}. */
    public BotHarness(Path resultsDir) {
        this.resultsDir = resultsDir;
    }

    /**
     * Runs the bot fleet. {@code bots} must be even (they pair up); each pair
     * plays {@code games} full games against each other.
     */
    public Summary run(int bots, int games, String host, int port, Long seed)
            throws IOException, InterruptedException {
        BlockingQueue<String> roomCodes = new LinkedBlockingQueue<>();
        List<LoadTestBot> fleet = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        long startedAt = System.currentTimeMillis();
        for (int i = 0; i < bots; i++) {
            LoadTestBot bot = new LoadTestBot(i, host, port, games,
                    seed == null ? System.nanoTime() : seed, roomCodes);
            Thread thread = new Thread(bot, "onitama-bot-" + i);
            fleet.add(bot);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long wallMillis = System.currentTimeMillis() - startedAt;

        List<Double> allRtts = fleet.stream()
                .flatMap(bot -> bot.moveRtts().stream())
                .sorted(Comparator.naturalOrder())
                .toList();
        // Every finished game is observed by both players of the pair, so the
        // per-bot counts add up to twice the number of completed games.
        int gamesCompleted = (int) Math.round(
                fleet.stream().mapToInt(LoadTestBot::gamesCompleted).sum() / 2.0);
        int failedBots = (int) fleet.stream().filter(bot -> bot.failure() != null).count();

        writeLatencies(fleet);
        Summary summary = new Summary(bots, bots / 2 * games, gamesCompleted, failedBots,
                allRtts.size(), wallMillis,
                wallMillis == 0 ? 0 : gamesCompleted * 60_000.0 / wallMillis,
                mean(allRtts), percentile(allRtts, 0.50), percentile(allRtts, 0.95),
                allRtts.isEmpty() ? 0 : allRtts.get(allRtts.size() - 1));
        writeSummary(summary, host, port, games, seed);
        return summary;
    }

    private void writeLatencies(List<LoadTestBot> fleet) throws IOException {
        Files.createDirectories(resultsDir);
        try (BufferedWriter out = Files.newBufferedWriter(
                resultsDir.resolve("latencies.csv"), StandardCharsets.UTF_8)) {
            out.write("botId,moveSeq,rttMillis");
            out.newLine();
            for (LoadTestBot bot : fleet) {
                int sequence = 0;
                for (Double rtt : bot.moveRtts()) {
                    out.write(bot.id() + "," + (sequence++) + "," + rtt);
                    out.newLine();
                }
            }
        }
    }

    private void writeSummary(Summary summary, String host, int port, int games, Long seed)
            throws IOException {
        Files.createDirectories(resultsDir);
        boolean newFile = !Files.exists(resultsDir.resolve("summary.csv"));
        try (BufferedWriter out = Files.newBufferedWriter(
                resultsDir.resolve("summary.csv"), StandardCharsets.UTF_8,
                newFile ? java.nio.file.StandardOpenOption.CREATE
                        : java.nio.file.StandardOpenOption.APPEND)) {
            if (newFile) {
                out.write("runAt,host,port,bots,gamesPerPair,gamesRequested,gamesCompleted,"
                        + "failedBots,movesMeasured,wallMillis,gamesPerMinute,"
                        + "meanRttMillis,p50RttMillis,p95RttMillis,maxRttMillis,seed");
                out.newLine();
            }
            out.write(LocalDateTime.now() + "," + host + "," + port + ","
                    + summary.botCount() + "," + games + ","
                    + summary.gamesRequested() + "," + summary.gamesCompleted() + ","
                    + summary.failedBots() + "," + summary.movesMeasured() + ","
                    + summary.wallMillis() + "," + String.format("%.2f", summary.gamesPerMinute())
                    + "," + String.format("%.2f", summary.meanRttMillis()) + ","
                    + String.format("%.2f", summary.p50RttMillis()) + ","
                    + String.format("%.2f", summary.p95RttMillis()) + ","
                    + String.format("%.2f", summary.maxRttMillis()) + ","
                    + (seed == null ? "" : seed));
            out.newLine();
        }
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** Nearest-rank percentile of a sorted list. */
    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
