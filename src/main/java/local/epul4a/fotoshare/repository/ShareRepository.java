package local.epul4a.fotoshare.repository;

import local.epul4a.fotoshare.model.Share;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRepository extends JpaRepository<Share, Long> {
}
