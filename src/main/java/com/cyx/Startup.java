package com.cyx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.chengyuxing.common.DataRow;
import com.github.chengyuxing.common.console.Color;
import com.github.chengyuxing.common.console.Printer;
import com.github.chengyuxing.sql.BakiDao;
import com.github.chengyuxing.sql.XQLFileManager;
import com.zaxxer.hikari.HikariDataSource;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Startup {
    static final Logger log = LoggerFactory.getLogger("npm_publish_app");

    public static void main(String[] args) throws IOException {
        String userHome = System.getProperty("user.home");
        if (args.length > 0) {
            System.out.println("init...");
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:sqlite:" + userHome + File.separator + "npm_publish_cache.db");
            BakiDao bakiDao = new BakiDao(ds);
            bakiDao.setCheckParameterType(false);
            XQLFileManager xqlFileManager = new XQLFileManager();
            xqlFileManager.setDelimiter(";;");
            xqlFileManager.add("data", "data.sql");
            xqlFileManager.init();
            bakiDao.setXqlFileManager(xqlFileManager);

            bakiDao.batchExecute(xqlFileManager.get("data.create_table").split(";"));

            List<NpmRecord> allCache;
            try (Stream<DataRow> s = bakiDao.query("&data.all").stream()) {
                allCache = s.map(d -> d.toEntity(NpmRecord.class))
                        .collect(Collectors.toList());
            }

            Runtime.getRuntime().addShutdownHook(new Thread(ds::close));

            ObjectMapper json = new ObjectMapper();
            ObjectWriter writer = json.writerWithDefaultPrettyPrinter();
            POSIX posix = POSIXFactory.getPOSIX();
            Runtime runtime = Runtime.getRuntime();
            try (Stream<Path> pathStream = Files.find(Paths.get(args[0]), 10, (p, a) -> !a.isDirectory() && p.toString().endsWith("package.json"))) {
                pathStream.filter(p -> !p.toString().endsWith("dist" + File.separator + "package.json"))
                        .forEach(p -> {
                            try {
                                File packageFile = p.toFile();
                                @SuppressWarnings("unchecked") Map<String, Object> pkgObj = json.readValue(packageFile, Map.class);
                                if (pkgObj.containsKey("name") && pkgObj.containsKey("version")) {
                                    String name = pkgObj.get("name").toString();
                                    String version = pkgObj.get("version").toString();
                                    int idx = allCache.indexOf(new NpmRecord(name, version));
                                    if (idx == -1 || allCache.get(idx).getPublish().equals(0)) {
                                        NpmRecord current = idx == -1 ? new NpmRecord(name, version) : allCache.get(idx);
                                        boolean changed = false;
                                        log.info("pushing {}", packageFile);
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
                                        String registry = "";
                                        if (args.length > 1) {
                                            registry = " --registry " + args[1];
                                        }
                                        Process process = runtime.exec("npm publish" + registry);
                                        // Process process = runtime.exec("pwd");
                                        new Thread(() -> {
                                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(process.getInputStream())))) {
                                                String line;
                                                while ((line = reader.readLine()) != null) {
                                                    if (line.contains("+")) {
                                                        NpmRecord npmRecord = new NpmRecord(name, version);
                                                        npmRecord.setPublish(1);
                                                        if (current.getId() == null) {
                                                            bakiDao.insert("record").save(DataRow.fromEntity(npmRecord));
                                                        } else {
                                                            bakiDao.update("record", "id = :id").save(DataRow.fromEntity(npmRecord));
                                                        }
                                                        Printer.println(line, Color.DARK_PURPLE);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                log.error(e.toString());
                                            }

                                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(process.getErrorStream())))) {
                                                String line;
                                                boolean isExistingReason = false;
                                                while ((line = reader.readLine()) != null) {
                                                    if (line.contains("publish over existing version")) {
                                                        isExistingReason = true;
                                                        Printer.println("- " + name + "@" + version + " (Cannot publish over existing version)", Color.SILVER);
                                                    }
                                                    log.debug(line);
                                                }
                                                if (!isExistingReason) {
                                                    Printer.println("- " + name + "@" + version, Color.SILVER);
                                                }
                                                int published = isExistingReason ? 1 : 0;
                                                NpmRecord record = new NpmRecord(name, version);
                                                record.setPublish(published);
                                                if (current.getId() == null) {
                                                    bakiDao.insert("record").save(DataRow.fromEntity(record));
                                                } else {
                                                    bakiDao.update("record", "id = :id").save(DataRow.fromEntity(record));
                                                }
                                            } catch (Exception e) {
                                                log.error(e.toString());
                                            }
                                            process.destroy();
                                        }).start();
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
            System.out.println("npm publish app\n" +
                    "cache file is " + userHome + File.separator + "npm_publish_cache.db (do not delete!)");
            Printer.println("arg1:\tnode_modules root path\te.g. ...myapp/node_modules\n" +
                    "arg2:\tyour npm registry\te.g. http://localhost:4873", Color.CYAN);
        }
    }
}
