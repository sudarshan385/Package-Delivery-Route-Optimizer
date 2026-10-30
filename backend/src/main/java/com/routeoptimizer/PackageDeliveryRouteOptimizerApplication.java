package com.routeoptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.IOException;

@org.springframework.cache.annotation.EnableCaching
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class PackageDeliveryRouteOptimizerApplication {

    private static RandomAccessFile lockRaf;
    private static FileLock lock;
    private static FileChannel channel;
    private static org.springframework.context.ConfigurableApplicationContext context;

    public static void main(String[] args) {
        try {
            File lockFile = new File("target/route-optimizer-instance.lock");
            if (lockFile.getParentFile() != null) {
                lockFile.getParentFile().mkdirs();
            }
            lockRaf = new RandomAccessFile(lockFile, "rw");
            channel = lockRaf.getChannel();
            lock = channel.tryLock();
            
            if (lock == null) {
                System.err.println("=====================================================");
                System.err.println("RouteOptimizer backend is already running on port 8080.");
                System.err.println("=====================================================");
                System.exit(0);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (context != null && context.isRunning()) {
                        context.close();
                    }
                    if (lock != null && lock.isValid()) {
                        lock.release();
                    }
                    if (channel != null && channel.isOpen()) {
                        channel.close();
                    }
                    if (lockRaf != null) {
                        lockRaf.close();
                    }
                } catch (IOException ignored) {
                }
            }));
        } catch (IOException e) {
            System.err.println("Error acquiring instance lock: " + e.getMessage());
            System.exit(1);
        }

        context = SpringApplication.run(PackageDeliveryRouteOptimizerApplication.class, args);
    }
}
