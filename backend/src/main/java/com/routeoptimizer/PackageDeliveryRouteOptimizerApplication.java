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

    private static FileLock lock;
    private static FileChannel channel;

    public static void main(String[] args) {
        try {
            File lockFile = new File("target/route-optimizer-instance.lock");
            if (lockFile.getParentFile() != null) {
                lockFile.getParentFile().mkdirs();
            }
            channel = new RandomAccessFile(lockFile, "rw").getChannel();
            lock = channel.tryLock();
            
            if (lock == null) {
                System.err.println("=====================================================");
                System.err.println("RouteOptimizer backend is already running on port 8080.");
                System.err.println("=====================================================");
                System.exit(0);
            }
        } catch (IOException e) {
            System.err.println("Error acquiring instance lock: " + e.getMessage());
            System.exit(1);
        }

        SpringApplication.run(PackageDeliveryRouteOptimizerApplication.class, args);
    }
}
