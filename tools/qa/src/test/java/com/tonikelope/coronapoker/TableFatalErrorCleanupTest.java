package com.tonikelope.coronapoker;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
public class TableFatalErrorCleanupTest {
 @Test public void fatalFailureClosesOnlyTableResources() {
  TableFailure f=TableFailure.capture(new RuntimeException("fatal"),2,true);
  assertTrue(f.closeTable()); assertTrue(f.forceRecovery()); assertFalse(f.exitJvm());
 }
 @Test public void crupierFatalCatchUsesLocalContainmentInsteadOfJvmExit() throws Exception {
  String source=Files.readString(Paths.get("..","..","src/main/java/com/tonikelope/coronapoker/Crupier.java"));
  assertTrue(source.contains("containTableFailure(ex);"));
  assertFalse(source.contains("System.exit(1);"));
 }
}
