package local.epul4a.fotoshare.repository;

import local.epul4a.fotoshare.model.Album;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AlbumRepository extends JpaRepository<Album, Long> {
}
