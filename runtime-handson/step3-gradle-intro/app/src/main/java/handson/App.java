package handson;

public class App {
    public String getGreeting() {
        return "Hello from Gradle!";
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
    }
}
