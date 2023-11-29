package tech.pegasys.teku.infrastructure.async;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public interface ExecutorServiceFactory {

    ExecutorService createExecutor(String name, int maxThreads, int maxQueueSize, int threadPriority);

    ScheduledExecutorService createScheduledExecutor(String name);

}
