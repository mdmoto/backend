import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGen {
    public static void main(String[] args) {
        String md5Of123456 = "e10adc3949ba59abbe56e057f20f883e";
        System.out.println(new BCryptPasswordEncoder().encode(md5Of123456));
    }
}
