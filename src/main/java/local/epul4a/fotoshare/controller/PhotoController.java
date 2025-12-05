package local.epul4a.fotoshare.controller;

import local.epul4a.fotoshare.dto.CommentaryResponseDto;
import local.epul4a.fotoshare.dto.PhotoResponseDto;
import local.epul4a.fotoshare.dto.PhotoUploadDto;
import local.epul4a.fotoshare.dto.ShareRequestDto;
import local.epul4a.fotoshare.dto.ShareResponseDto;
import local.epul4a.fotoshare.model.*;
import local.epul4a.fotoshare.repository.UserRepository;
import local.epul4a.fotoshare.service.CommentaryService;
import local.epul4a.fotoshare.service.FileValidationService;
import local.epul4a.fotoshare.service.PhotoService;
import local.epul4a.fotoshare.service.ShareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * Contrôleur pour la gestion des photos.
 */
@Controller
@RequestMapping("/photos")
public class PhotoController {
    @Autowired
    private PhotoService photoService;
    @Autowired
    private ShareService shareService;
    @Autowired
    private CommentaryService commentaryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FileValidationService fileValidationService;
    private static final int PAGE_SIZE = 12;
    /**
     * Affiche la liste des photos accessibles à l'utilisateur avec pagination.
     */
    @GetMapping
    public String listPhotos(@RequestParam(defaultValue = "0") int page, Model model) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Photo> photosPage = photoService.getAccessiblePhotosPaged(userId, pageable);
        List<PhotoResponseDto> photoDtos = photosPage.getContent().stream()
                .map(photo -> {
                    String ownerUsername = userRepository.findById(photo.getOwner_id())
                            .map(User::getUsername)
                            .orElse("Inconnu");
                    PERMISSION permission = photoService.getEffectivePermission(photo.getId(), userId);
                    return PhotoResponseDto.fromEntity(photo, ownerUsername, permission);
                })
                .collect(Collectors.toList());
        model.addAttribute("photos", photoDtos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", photosPage.getTotalPages());
        model.addAttribute("totalItems", photosPage.getTotalElements());
        model.addAttribute("hasNext", photosPage.hasNext());
        model.addAttribute("hasPrevious", photosPage.hasPrevious());
        return "photos/list";
    }
    /**
     * Affiche les photos de l'utilisateur connecté avec pagination.
     */
    @GetMapping("/my")
    public String myPhotos(@RequestParam(defaultValue = "0") int page, Model model) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<Photo> photosPage = photoService.getPhotosByOwnerPaged(userId, pageable);
        List<PhotoResponseDto> photoDtos = photosPage.getContent().stream()
                .map(photo -> {
                    String ownerUsername = getCurrentUsername();
                    return PhotoResponseDto.fromEntity(photo, ownerUsername, PERMISSION.ADMIN);
                })
                .collect(Collectors.toList());
        model.addAttribute("photos", photoDtos);
        model.addAttribute("isOwnerView", true);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", photosPage.getTotalPages());
        model.addAttribute("totalItems", photosPage.getTotalElements());
        model.addAttribute("hasNext", photosPage.hasNext());
        model.addAttribute("hasPrevious", photosPage.hasPrevious());
        return "photos/list";
    }
    /**
     * Affiche le formulaire d'upload.
     */
    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("photoUpload", new PhotoUploadDto());
        model.addAttribute("visibilities", VISIBILITY.values());
        model.addAttribute("maxFileSize", fileValidationService.getMaxFileSize() / (1024 * 1024));
        return "photos/upload";
    }
    /**
     * Traite l'upload d'une photo.
     */
    @PostMapping("/upload")
    public String uploadPhoto(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "title", required = false) String title,
                             @RequestParam(value = "description", required = false) String description,
                             @RequestParam(value = "visibility", defaultValue = "PRIVATE") VISIBILITY visibility,
                             RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            Photo photo = photoService.uploadPhoto(file, title, description, visibility, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Photo uploadée avec succès !");
            return "redirect:/photos/" + photo.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/photos/upload";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur lors de l'upload: " + e.getMessage());
            return "redirect:/photos/upload";
        }
    }
    /**
     * Affiche les détails d'une photo.
     */
    @GetMapping("/{id}")
    public String viewPhoto(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        Optional<Photo> photoOpt = photoService.getPhoto(id, userId);
        if (photoOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Photo introuvable ou accès non autorisé");
            return "redirect:/photos";
        }
        Photo photo = photoOpt.get();
        String ownerUsername = userRepository.findById(photo.getOwner_id())
                .map(User::getUsername)
                .orElse("Inconnu");
        PERMISSION permission = photoService.getEffectivePermission(id, userId);
        PhotoResponseDto photoDto = PhotoResponseDto.fromEntity(photo, ownerUsername, permission);
        List<Commentary> comments = commentaryService.getCommentsForPhoto(id, userId);
        List<CommentaryResponseDto> commentDtos = comments.stream()
                .map(comment -> {
                    String authorUsername = userRepository.findById(comment.getAuthor_id())
                            .map(User::getUsername)
                            .orElse("Inconnu");
                    boolean canDelete = userId != null && (comment.getAuthor_id().equals(userId) || permission == PERMISSION.ADMIN);
                    boolean canEdit = userId != null && comment.getAuthor_id().equals(userId);
                    return CommentaryResponseDto.fromEntity(comment, authorUsername, canDelete, canEdit);
                })
                .collect(Collectors.toList());
        model.addAttribute("photo", photoDto);
        model.addAttribute("comments", commentDtos);
        model.addAttribute("commentCount", comments.size());
        model.addAttribute("canEdit", permission == PERMISSION.ADMIN);
        model.addAttribute("canComment", permission == PERMISSION.COMMENT || permission == PERMISSION.ADMIN);
        model.addAttribute("isOwner", userId != null && userId.equals(photo.getOwner_id()));
        return "photos/view";
    }
    /**
     * Sert le fichier image d'une photo.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> servePhoto(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        try {
            Optional<Photo> photoOpt = photoService.getPhoto(id, userId);
            if (photoOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            Photo photo = photoOpt.get();
            Resource file = photoService.getPhotoFile(id, userId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(photo.getContent_type()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + photo.getOriginal_filename() + "\"")
                    .body(file);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    /**
     * Sert la miniature d'une photo.
     */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> serveThumbnail(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        try {
            Optional<Photo> photoOpt = photoService.getPhoto(id, userId);
            if (photoOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            Photo photo = photoOpt.get();
            Resource thumbnail = photoService.getThumbnailFile(id, userId);
            if (thumbnail == null) {
                Resource file = photoService.getPhotoFile(id, userId);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.getContent_type()))
                        .body(file);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(photo.getContent_type()))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(thumbnail);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    /**
     * Affiche le formulaire de modification d'une photo.
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        Optional<Photo> photoOpt = photoService.getPhoto(id, userId);
        if (photoOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Photo introuvable");
            return "redirect:/photos";
        }
        PERMISSION permission = photoService.getEffectivePermission(id, userId);
        if (permission != PERMISSION.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vous n'avez pas le droit de modifier cette photo");
            return "redirect:/photos/" + id;
        }
        model.addAttribute("photo", photoOpt.get());
        model.addAttribute("visibilities", VISIBILITY.values());
        return "photos/edit";
    }
    /**
     * Traite la modification d'une photo.
     */
    @PostMapping("/{id}/edit")
    public String updatePhoto(@PathVariable Long id,
                             @RequestParam(value = "title", required = false) String title,
                             @RequestParam(value = "description", required = false) String description,
                             @RequestParam(value = "visibility", required = false) VISIBILITY visibility,
                             RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            photoService.updatePhoto(id, title, description, visibility, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Photo mise à jour avec succès !");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur: " + e.getMessage());
        }
        return "redirect:/photos/" + id;
    }

    @PreAuthorize("@securityService.canDeletePhoto(authentication, #id)")
    @PostMapping("/{id}/delete")
    public String deletePhoto(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            photoService.deletePhoto(id, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Photo supprimée avec succès");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/photos/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur: " + e.getMessage());
            return "redirect:/photos/" + id;
        }
        return "redirect:/photos/my";
    }
    /**
     * Affiche le formulaire de partage d'une photo.
     */
    @GetMapping("/{id}/share")
    public String showShareForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        Optional<Photo> photoOpt = photoService.getPhoto(id, userId);
        if (photoOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Photo introuvable");
            return "redirect:/photos";
        }
        PERMISSION permission = photoService.getEffectivePermission(id, userId);
        if (permission != PERMISSION.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vous n'avez pas le droit de partager cette photo");
            return "redirect:/photos/" + id;
        }
        List<Share> shares = shareService.getSharesForPhoto(id, userId);
        List<ShareResponseDto> shareDtos = shares.stream()
                .map(share -> {
                    String username = userRepository.findById(share.getUser_id())
                            .map(User::getUsername)
                            .orElse("Inconnu");
                    return ShareResponseDto.fromEntity(share, username);
                })
                .collect(Collectors.toList());
        model.addAttribute("photo", photoOpt.get());
        model.addAttribute("shares", shareDtos);
        model.addAttribute("shareRequest", new ShareRequestDto());
        model.addAttribute("permissions", PERMISSION.values());
        return "photos/share";
    }
    /**
     * Traite le partage d'une photo.
     */
    @PostMapping("/{id}/share")
    public String sharePhoto(@PathVariable Long id,
                            @RequestParam("username") String targetUsername,
                            @RequestParam("permission") PERMISSION permission,
                            RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            shareService.sharePhotoByUsername(id, targetUsername, permission, userId);
            redirectAttributes.addFlashAttribute("successMessage",
                "Photo partagée avec " + targetUsername + " (permission: " + permission + ")");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur: " + e.getMessage());
        }
        return "redirect:/photos/" + id + "/share";
    }
    /**
     * Révoque un partage.
     */
    @PreAuthorize("@securityService.isPhotoOwner(authentication, #photoId)")
    @PostMapping("/{photoId}/share/{userId}/revoke")
    public String revokeShare(@PathVariable Long photoId,
                             @PathVariable Long userId,
                             RedirectAttributes redirectAttributes) {
        Long requesterId = getCurrentUserId();
        if (requesterId == null) {
            return "redirect:/login";
        }
        try {
            shareService.revokeShare(photoId, userId, requesterId);
            redirectAttributes.addFlashAttribute("successMessage", "Partage révoqué avec succès");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur: " + e.getMessage());
        }
        return "redirect:/photos/" + photoId + "/share";
    }

    @PreAuthorize("@securityService.canCommentOnPhoto(authentication, #id)")
    @PostMapping("/{id}/comments")
    public String addComment(@PathVariable Long id,
                            @RequestParam("text") String text,
                            RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            commentaryService.addComment(id, text, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Commentaire ajouté avec succès");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur: " + e.getMessage());
        }
        return "redirect:/photos/" + id;
    }

    @PreAuthorize("@securityService.canDeleteComment(authentication, #commentId)")
    @PostMapping("/{photoId}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long photoId,
                               @PathVariable Long commentId,
                               RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            commentaryService.deleteComment(commentId, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Commentaire supprimé");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur: " + e.getMessage());
        }
        return "redirect:/photos/" + photoId;
    }

    @PreAuthorize("@securityService.canEditComment(authentication, #commentId)")
    @PostMapping("/{photoId}/comments/{commentId}/edit")
    public String editComment(@PathVariable Long photoId,
                             @PathVariable Long commentId,
                             @RequestParam("text") String text,
                             RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            commentaryService.updateComment(commentId, text, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Commentaire modifié");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Erreur: " + e.getMessage());
        }
        return "redirect:/photos/" + photoId;
    }
    /**
     * Récupère l'ID de l'utilisateur actuellement connecté.
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }
    /**
     * Récupère le nom d'utilisateur actuellement connecté.
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getName();
    }
}
