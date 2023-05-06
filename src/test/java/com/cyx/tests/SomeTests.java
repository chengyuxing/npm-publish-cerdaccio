package com.cyx.tests;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SomeTests {
    @Test
    public void test1() {
        Path path = Paths.get("/Users/chengyuxing/Downloads/jdk-8u371-windows-x64.exe");
        System.out.println(path.endsWith(Paths.get("Downloads", "jdk-8u371-windows-x64.exe")));
    }
}
