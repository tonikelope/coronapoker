import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/** Publishes a fully closed distribution JAR without exposing partial bytes. */
final class AtomicJarPublisher {

    private AtomicJarPublisher() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "Expected staged and destination JAR paths");
        }
        Path staged = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path destination = Path.of(arguments[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(staged) || Files.size(staged) == 0L) {
            throw new IOException("Staged JAR is missing or empty: " + staged);
        }
        try (JarFile jar = new JarFile(staged.toFile(), true)) {
            if (jar.getManifest() == null) {
                throw new IOException("Staged JAR has no manifest: " + staged);
            }
        }
        Files.createDirectories(destination.getParent());
        try {
            Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException("Distribution directory does not support "
                    + "atomic JAR publication: " + destination, unsupported);
        }
    }
}
