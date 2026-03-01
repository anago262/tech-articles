public class SlowApp {
    public static void main(String[] args) throws Exception {
        System.out.println("Running... (press Ctrl+C to stop)");
        Thread.sleep(Long.MAX_VALUE);
    }
}
