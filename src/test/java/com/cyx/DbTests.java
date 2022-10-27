package com.cyx;

import com.github.chengyuxing.sql.Args;
import com.github.chengyuxing.sql.Baki;
import com.github.chengyuxing.sql.BakiDao;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class DbTests {
    static Baki baki;

    @BeforeClass
    public static void init() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:sqlite:/users/chengyuxing/Downloads/npm_publish_cache.db");
        BakiDao bakiDao = new BakiDao(ds);
        bakiDao.setCheckParameterType(false);
        baki = bakiDao;
    }

    @Test
    public void test() throws Exception {
        List<Args<Object>> rows = Files.lines(Paths.get("/Users/chengyuxing/npm_unpublished.cache"))
                .map(l -> {
                    int atI = l.lastIndexOf("@");
                    String name = l.substring(0, atI);
                    String version = l.substring(atI + 1);
                    return Args.create("name", name, "version", version, "publish", 0);
                }).collect(Collectors.toList());

        baki.insert("record").fast().save(rows);
    }
}
