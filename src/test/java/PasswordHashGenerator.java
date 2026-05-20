import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 원하는 비밀번호 — 여러 개 만들고 싶으면 추가
        String[] passwords = {"test1234", "test5678", "test9999"};

        for (String pw : passwords) {
            String hashed = encoder.encode(pw);
            System.out.println("원본: " + pw);
            System.out.println("해시: " + hashed);
            System.out.println("---");
        }
    }
}