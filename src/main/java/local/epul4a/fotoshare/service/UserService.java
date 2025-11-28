package local.epul4a.fotoshare.service;

import local.epul4a.fotoshare.model.User;
import local.epul4a.fotoshare.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    public User saveUser(User user) {
        return userRepository.save(user);
    }
    public void createUser(String username, String email) {
        userRepository.save(User.builder()
                .email(email)
                .build());
    }
}