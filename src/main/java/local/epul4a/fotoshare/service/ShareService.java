package local.epul4a.fotoshare.service;

import local.epul4a.fotoshare.repository.AlbumRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service

public class ShareService {
    @Autowired
    private AlbumRepository albumRepository;
}
