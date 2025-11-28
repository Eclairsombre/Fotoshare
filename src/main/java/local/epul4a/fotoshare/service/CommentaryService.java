package local.epul4a.fotoshare.service;

import local.epul4a.fotoshare.repository.CommentaryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service

public class CommentaryService {
    @Autowired
    private CommentaryRepository commentaryRepository;
}
