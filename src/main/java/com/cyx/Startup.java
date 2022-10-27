package com.cyx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.chengyuxing.common.console.Color;
import com.github.chengyuxing.common.console.Printer;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Stream;

public class Startup {
    static final Logger log = LoggerFactory.getLogger("npm_publish_app");

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {

            Set<String> caches = new HashSet<>();
            Set<String> unpublished = new HashSet<>();

            String userHome = System.getProperty("user.home");

            File publishedCache = new File(userHome + File.separator + "npm_published.cache");
            if (publishedCache.exists()) {
                try (Stream<String> ls = Files.lines(publishedCache.toPath(), StandardCharsets.UTF_8)) {
                    ls.forEach(caches::add);
                }
            }

            File unpublishedCache = new File(userHome + File.separator + "npm_unpublished.cache");
            if (unpublishedCache.exists()) {
                try (Stream<String> ls = Files.lines(unpublishedCache.toPath(), StandardCharsets.UTF_8)) {
                    ls.forEach(unpublished::add);
                }
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.write(publishedCache.toPath(), caches, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
                    Files.write(unpublishedCache.toPath(), unpublished, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));

            ObjectMapper json = new ObjectMapper();
            ObjectWriter writer = json.writerWithDefaultPrettyPrinter();
            POSIX posix = POSIXFactory.getPOSIX();
            Runtime runtime = Runtime.getRuntime();
            try (Stream<Path> pathStream = Files.find(Paths.get(args[0]), 100, (p, a) -> !a.isDirectory() && p.toString().endsWith("package.json"))) {
                pathStream.forEach(p -> {
                    try {
                        File packageFile = p.toFile();
                        log.info("pushing {}", packageFile);
                        @SuppressWarnings("unchecked") Map<String, Object> pkgObj = json.readValue(packageFile, Map.class);
                        boolean changed = false;
                        if (pkgObj.containsKey("name") && pkgObj.containsKey("version")) {

                            String nv = pkgObj.get("name") + "@" + pkgObj.get("version");

                            if (!caches.contains(nv)) {
                                if (pkgObj.containsKey("scripts")) {
                                    pkgObj.put("scripts", Collections.emptyMap());
                                    changed = true;
                                }
                                if (pkgObj.containsKey("publishConfig")) {
                                    pkgObj.put("publishConfig", Collections.emptyMap());
                                    changed = true;
                                }
                                if (changed) {
                                    writer.writeValue(packageFile, pkgObj);
                                }
                                posix.chdir(p.getParent().toString());
                                Thread.sleep(100);
                                Process process = runtime.exec("npm publish");
                                if (process.isAlive()) {
                                    new Thread(() -> {
                                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(process.getInputStream())))) {
                                            String line;
                                            boolean read = false;
                                            while ((line = reader.readLine()) != null) {
                                                read = true;
                                                if (line.contains("+")) {
                                                    caches.add(nv);
                                                    unpublished.remove(nv);
                                                }
                                                Printer.println(line, Color.DARK_PURPLE);
                                            }
                                            if (!read) {
                                                unpublished.add(nv);
                                                Printer.println("- " + nv + " (unpublished)", Color.SILVER);
                                            }
                                        } catch (Exception e) {
                                            log.error(e.toString());
                                        }

                                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(process.getErrorStream())))) {
                                            String line;
                                            while ((line = reader.readLine()) != null) {
                                                log.debug(line);
                                            }
                                        } catch (Exception e) {
                                            log.error(e.toString());
                                        }
                                        process.destroy();
                                    }).start();
                                }
                                Thread.sleep(1000);
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        log.error(e.toString());
                    }
                });
                System.out.println("publish done!");
                System.out.println(">>>> see log: " + userHome + File.separator + "npm_publish.log");
                System.exit(0);
            }
        } else {
            System.out.println("arg1 'node_modules' root path is required!");
        }
    }
}
