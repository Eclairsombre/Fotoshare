package local.epul4a.fotoshare.repository;

import local.epul4a.fotoshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {
}