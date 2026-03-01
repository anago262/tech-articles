package handson;

public class App {
    public String getGreeting() {
        return "Hello from Java 11 compatible code!";
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
        System.out.println("Java version: " + System.getProperty("java.version"));
    }
}
