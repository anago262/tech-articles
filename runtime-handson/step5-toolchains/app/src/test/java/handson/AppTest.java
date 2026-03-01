package handson;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test
    void greetingIsNotNull() {
        assertNotNull(new App().getGreeting());
    }

    @Test
    void runsOnExpectedJavaVersion() {
        String version = System.getProperty("java.version");
        System.out.println("Test is running on Java: " + version);
        // Toolchains が Java 11 を指定しているなら、ここで 11 が表示されるはず
        assertTrue(version.startsWith("11"), "Expected Java 11, but got: " + version);
    }
}
