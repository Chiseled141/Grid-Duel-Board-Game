package deckduel.client.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/** Runs test code on the JavaFX Application Thread and waits for it; starts the toolkit once. */
final class FxTesting {
    private FxTesting() { }

    static <T> T call(Callable<T> work) throws Exception {
        FxKit.start();
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(work.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        try { return result.get(20, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw (Error) failure.getCause();
        }
    }

    interface Work { void run() throws Exception; }

    static void run(Work work) throws Exception { call(() -> { work.run(); return null; }); }
}
