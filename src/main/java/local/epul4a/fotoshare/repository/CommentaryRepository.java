package local.epul4a.fotoshare.repository;

import local.epul4a.fotoshare.model.Commentary;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CommentaryRepository  extends JpaRepository<Commentary, Long> {
}
