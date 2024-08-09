package edu.yu.cs.com3800;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class JavaRunnerTest {
    @Test
    public void linTest(){
        File f=new File("SampleJavaClass.java");
        FileWriter fw= null;
        try {
            String sampleJavaCode = "public class SampleJavaClass {\n" +
                    "    public String run() {\n" +
                    "        return \"Hello, World!\";\n" +
                    "    }\n" +
                    "}\n";
            JavaRunner jr=new JavaRunner();
            System.out.println(new String(new FileInputStream(f).readAllBytes()));
            String s=jr.compileAndRun(new ByteArrayInputStream(sampleJavaCode.getBytes()));
            System.out.println(s);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }
    @Test
    public void GPTTest(){
        String sampleJavaCode = "public class SampleJavaClass {\n" +
                "    public String run() {\n" +
                "        return \"Hello, World!\";\n" +
                "    }\n" +
                "}\n";
        InputStream sampleInputStream = new ByteArrayInputStream(sampleJavaCode.getBytes());
        try {
            JavaRunner javaRunner = new JavaRunner();
            String result = javaRunner.compileAndRun(sampleInputStream);
            System.out.println("Result: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}