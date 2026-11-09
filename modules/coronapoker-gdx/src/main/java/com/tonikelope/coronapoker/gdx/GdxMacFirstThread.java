/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Makes the portable JAR satisfy GLFW's macOS first-thread requirement. */
final class GdxMacFirstThread {

    private static final String MARKER = "coronapoker.macos.firstThread";

    private GdxMacFirstThread() { }

    static boolean relaunchIfRequired(String[] applicationArgs) {
        String os = System.getProperty("os.name", "");
        List<String> vmArgs = ManagementFactory.getRuntimeMXBean()
                .getInputArguments();
        if (!requiresRelaunch(os, vmArgs,
                Boolean.getBoolean(MARKER))) {
            return false;
        }
        try {
            applicationProcess(applicationArgs).start();
            return true;
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot relaunch CoronaPoker on the macOS first thread",
                    failure);
        }
    }

    static ProcessBuilder applicationProcess(String[] applicationArgs) throws IOException {
        Path artifact;
        try {
            artifact = Path.of(GdxLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath();
        } catch (java.net.URISyntaxException failure) {
            throw new IOException("Cannot find the CoronaPoker application", failure);
        }
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        boolean javaw = ProcessHandle.current().info().command()
                .map(path -> Path.of(path).getFileName().toString().equalsIgnoreCase("javaw.exe"))
                .orElse(false);
        return new ProcessBuilder(command(bin.resolve(javaw ? "javaw.exe" : "java"),
                artifact, System.getProperty("java.class.path", ""),
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                Arrays.asList(applicationArgs), isMac(System.getProperty("os.name"))))
                .inheritIO();
    }

    static boolean requiresRelaunch(String osName, List<String> vmArgs,
            boolean marker) {
        return isMac(osName) && !marker
                && vmArgs.stream().noneMatch("-XstartOnFirstThread"::equals);
    }

    private static boolean isMac(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        return os.startsWith("mac") || os.startsWith("darwin");
    }

    static List<String> command(Path javaHome, Path artifact,
            String classPath, List<String> vmArgs,
            List<String> applicationArgs) {
        return command(javaHome.resolve("bin").resolve("java"), artifact,
                classPath, vmArgs, applicationArgs, true);
    }

    static List<String> command(Path javaExecutable, Path artifact,
            String classPath, List<String> vmArgs,
            List<String> applicationArgs, boolean firstThread) {
        ArrayList<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        if (firstThread) command.add("-XstartOnFirstThread");
        vmArgs.stream()
                .filter(argument -> !"-XstartOnFirstThread".equals(argument))
                .filter(argument -> !argument.startsWith("-agentlib:jdwp"))
                .filter(argument -> !argument.startsWith("-javaagent:"))
                .filter(argument -> !argument.startsWith("-D" + MARKER + "="))
                .forEach(command::add);
        if (firstThread) command.add("-D" + MARKER + "=true");
        if (Files.isRegularFile(artifact)
                && artifact.getFileName().toString().endsWith(".jar")) {
            command.add("-jar");
            command.add(artifact.toString());
        } else {
            command.add("-cp");
            command.add(classPath);
            command.add(GdxLauncher.class.getName());
        }
        command.addAll(applicationArgs);
        return List.copyOf(command);
    }
}
