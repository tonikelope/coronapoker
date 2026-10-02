/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
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
            Path artifact = Path.of(GdxLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath();
            List<String> command = command(Path.of(
                    System.getProperty("java.home")), artifact,
                    System.getProperty("java.class.path", ""), vmArgs,
                    Arrays.asList(applicationArgs));
            new ProcessBuilder(command).inheritIO().start();
            return true;
        } catch (IOException | URISyntaxException failure) {
            throw new IllegalStateException(
                    "Cannot relaunch CoronaPoker on the macOS first thread",
                    failure);
        }
    }

    static boolean requiresRelaunch(String osName, List<String> vmArgs,
            boolean marker) {
        String os = osName == null ? ""
                : osName.toLowerCase(Locale.ROOT);
        return (os.startsWith("mac") || os.startsWith("darwin"))
                && !marker
                && vmArgs.stream().noneMatch("-XstartOnFirstThread"::equals);
    }

    static List<String> command(Path javaHome, Path artifact,
            String classPath, List<String> vmArgs,
            List<String> applicationArgs) {
        ArrayList<String> command = new ArrayList<>();
        command.add(javaHome.resolve("bin").resolve("java").toString());
        command.add("-XstartOnFirstThread");
        vmArgs.stream()
                .filter(argument -> !"-XstartOnFirstThread".equals(argument))
                .filter(argument -> !argument.startsWith("-agentlib:jdwp"))
                .filter(argument -> !argument.startsWith("-javaagent:"))
                .forEach(command::add);
        command.add("-D" + MARKER + "=true");
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
