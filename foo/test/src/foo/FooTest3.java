package foo;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class FooTest3 {
    @Test
    public void testGreet() throws InterruptedException {
        // Sleep so that test workers from different modules overlap in the thread pool.
        Thread.sleep(500);
        assertTrue(Foo.greet("world").contains("foo"));
    }

    @Test
    public void testPrefix() throws InterruptedException {
        Thread.sleep(500);
        assertTrue(Foo.greet("world").startsWith("Hello"));
    }
}
