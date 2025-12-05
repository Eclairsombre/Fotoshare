package local.epul4a.fotoshare.security;

import local.epul4a.fotoshare.model.Photo;
import local.epul4a.fotoshare.model.User;
import local.epul4a.fotoshare.model.Album;
import local.epul4a.fotoshare.model.Commentary;
import local.epul4a.fotoshare.model.PERMISSION;
import local.epul4a.fotoshare.repository.PhotoRepository;
import local.epul4a.fotoshare.repository.AlbumRepository;
import local.epul4a.fotoshare.repository.CommentaryRepository;
import local.epul4a.fotoshare.repository.UserRepository;
import local.epul4a.fotoshare.repository.ShareRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("securityService")
public class SecurityService {

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private CommentaryRepository commentaryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShareRepository shareRepository;

    public boolean canAccessPhoto(Authentication authentication, Long photoId) {
        if (photoId == null) return false;

        Optional<Photo> photoOpt = photoRepository.findById(photoId);
        if (photoOpt.isEmpty()) return false;

        Photo photo = photoOpt.get();

        if (photo.getVisibility() == local.epul4a.fotoshare.model.VISIBILITY.PUBLIC) {
            return true;
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Long userId = getUserIdFromAuthentication(authentication);
        if (userId == null) return false;

        if (photo.getOwner_id().equals(userId)) {
            return true;
        }

        return shareRepository.findByPhotoIdAndUserId(photoId, userId).isPresent();
    }

    public boolean canEditPhoto(Authentication authentication, Long photoId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (photoId == null) return false;

        Long userId = getUserIdFromAuthentication(authentication);
        if (userId == null) return false;

        Optional<Photo> photoOpt = photoRepository.findById(photoId);
        if (photoOpt.isEmpty()) return false;

        Photo photo = photoOpt.get();

        if (photo.getOwner_id().equals(userId)) {
            return true;
        }

        return shareRepository.findByPhotoIdAndUserId(photoId, userId)
                .map(share -> share.getPermission() == PERMISSION.ADMIN)
                .orElse(false);
    }

    public boolean canDeletePhoto(Authentication authentication, Long photoId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (photoId == null) return false;

        Long userId = getUserIdFromAuthentication(authentication);
        if (userId == null) return false;

        return photoRepository.findById(photoId)
                .map(photo -> photo.getOwner_id().equals(userId))
                .orElse(false);
    }

    public boolean canCommentOnPhoto(Authentication authentication, Long photoId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (photoId == null) return false;

        Long userId = getUserIdFromAuthentication(authentication);
        if (userId == null) return false;

        Optional<Photo> photoOpt = photoRepository.findById(photoId);
        if (photoOpt.isEmpty()) return false;

        Photo photo = photoOpt.get();

        if (photo.getOwner_id().equals(userId)) {
            return true;
        }

        return shareRepository.findByPhotoIdAndUserId(photoId, userId)
                .map(share -> share.getPermission() == PERMISSION.COMMENT ||
                             share.getPermission() == PERMISSION.ADMIN)
                .orElse(false);
    }

    public boolean canEditComment(Authentication authentication, Long commentId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (commentId == null) return false;

        Long userId = getUserIdFromAuthentication(authentication);
        if (userId == null) return false;

        return commentaryRepository.findById(commentId)
                .map(comment -> comment.getAuthor_id().equals(userId))
                .orElse(false);
    }

    public boolean canDeleteComment(Authentication authentication, Long commentId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (commentId == null) return false;

        Long userId = getUserIdFromAuthentication(authentication);
        if (userId == null) return false;

        Optional<Commentary> commentOpt = commentaryRepository.findById(commentId);
        if (commentOpt.isEmpty()) return false;

        Commentary comment = commentOpt.get();

        if (comment.getAuthor_id().equals(userId)) {
            return true;
        }

        return photoRepository.findById(comment.getPhoto_id())
                .map(photo -> photo.getOwner_id().equals(userId))
                .orElse(false);
    }

    public boolean canAccessAlbum(Authentication authentication, Long albumId) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (albumId == null) return false;

        Long userId = getUserIdFromAuthentication(authentication);
        if (userId == null) return false;

        return albumRepository.findById(albumId)
                .map(album -> album.getOwner_id().equals(userId))
                .orElse(false);
    }

    public boolean canEditAlbum(Authentication authentication, Long albumId) {
        return canAccessAlbum(authentication, albumId);
    }

    public boolean canDeleteAlbum(Authentication authentication, Long albumId) {
        return canAccessAlbum(authentication, albumId);
    }

    public boolean canSharePhoto(Authentication authentication, Long photoId) {
        return canDeletePhoto(authentication, photoId);
    }

    public boolean isPhotoOwner(Authentication authentication, Long photoId) {
        return canDeletePhoto(authentication, photoId);
    }

    public boolean isAlbumOwner(Authentication authentication, Long albumId) {
        return canAccessAlbum(authentication, albumId);
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String username = authentication.getName();
        if ("anonymousUser".equals(username)) {
            return null;
        }

        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }
}

