package com.cyx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.chengyuxing.common.DataRow;
import com.github.chengyuxing.common.console.Color;
import com.github.chengyuxing.common.console.Printer;
import com.github.chengyuxing.common.utils.StringUtil;
import com.github.chengyuxing.sql.BakiDao;
import com.github.chengyuxing.sql.XQLFileManager;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Startup {
    static final Logger log = LoggerFactory.getLogger("npm_publish_app");
    static final boolean isWindows = StringUtil.containsIgnoreCase(System.getProperty("os.name"), "windows");

    public static void main(String[] args) throws IOException {
        String userHome = System.getProperty("user.home");
        if (args.length > 0) {
            System.out.println("init...");
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:sqlite:" + userHome + File.separator + "npm_publish_cache.db");
            BakiDao bakiDao = new BakiDao(ds);
            XQLFileManager xqlFileManager = new XQLFileManager();
            xqlFileManager.setDelimiter(";;");
            xqlFileManager.add("data", "data.sql");
            xqlFileManager.init();
            bakiDao.setXqlFileManager(xqlFileManager);

            bakiDao.of("").executeBatch(xqlFileManager.get("data.create_table").split(";"));

            List<NpmRecord> allCache;
            try (Stream<DataRow> s = bakiDao.query("&data.all").stream()) {
                allCache = s.map(d -> d.toEntity(NpmRecord.class))
                        .collect(Collectors.toList());
            }

            Runtime.getRuntime().addShutdownHook(new Thread(ds::close));

            ObjectMapper json = new ObjectMapper();
            ObjectWriter writer = json.writerWithDefaultPrettyPrinter();
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
                                        Thread.sleep(300);

                                        String cmd = getCmd(args, p);
                                        Process process = runtime.exec(cmd);

                                        log.info("pushing {} by execute '{}'", packageFile, cmd);

                                        boolean pushed = false;

                                        // + name@version
                                        InputStream infoInput = process.getInputStream();
                                        // npm notice ...
                                        // npm notice ...
                                        InputStream errorInput = process.getErrorStream();

                                        // if no content, it will be dead in this step.
                                        if (infoInput.available() > 0) {
                                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(infoInput)))) {
                                                String line;
                                                while ((line = reader.readLine()) != null) {
                                                    if (line.contains("+")) {
                                                        NpmRecord npmRecord = new NpmRecord(name, version);
                                                        npmRecord.setPublish(1);
                                                        if (current.getId() == null) {
                                                            bakiDao.insert("record").saveEntity(npmRecord);
                                                        } else {
                                                            bakiDao.update("record", "id = :id").saveEntity(npmRecord);
                                                        }
                                                        Printer.println(line, Color.DARK_PURPLE);
                                                        pushed = true;
                                                        break;
                                                    }
                                                }
                                            } catch (Exception e) {
                                                log.error(e.toString());
                                            }
                                        }
                                        // not really fail, maybe inputStream is empty.
                                        if (!pushed) {
                                            pushed = true;
                                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(errorInput)))) {
                                                String line;
                                                StringJoiner sb = new StringJoiner("\n");
                                                while ((line = reader.readLine()) != null) {
                                                    if (line.contains("ERR!")) {
                                                        pushed = false;
                                                        sb.add(line);
                                                    }
                                                    log.debug(line);
                                                }
                                                if (pushed) {
                                                    Printer.println("+ " + name + "@" + version, Color.DARK_PURPLE);
                                                } else {
                                                    String error = sb.toString();
                                                    String reason = "";

                                                    if (error.contains("EPUBLISHCONFLICT") || error.contains("over existing version")) {
                                                        reason = " (Cannot publish over existing version)";
                                                    }

                                                    Printer.println("- " + name + "@" + version + reason, Color.SILVER);
                                                    if (reason.isEmpty()) {
                                                        Printer.println(sb.toString(), Color.SILVER);
                                                    }
                                                }
                                                int published = pushed ? 1 : 0;
                                                NpmRecord record = new NpmRecord(name, version);
                                                record.setPublish(published);
                                                if (current.getId() == null) {
                                                    bakiDao.insert("record").saveEntity(record);
                                                } else {
                                                    bakiDao.update("record", "id = :id").saveEntity(record);
                                                }
                                            } catch (Exception e) {
                                                log.error(e.toString());
                                            }
                                        }
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

    private static String getCmd(String[] args, Path p) {
        String registry = "";
        if (args.length > 1) {
            registry = " --registry " + args[1];
        }
        String cmdPrefix = "";
        if (isWindows) {
            cmdPrefix = "cmd /c ";
        }
        return cmdPrefix + "npm publish " + p.getParent().toString() + registry;
    }
}
