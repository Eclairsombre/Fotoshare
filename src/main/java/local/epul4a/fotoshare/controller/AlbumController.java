package local.epul4a.fotoshare.controller;

import local.epul4a.fotoshare.dto.AlbumResponseDto;
import local.epul4a.fotoshare.dto.PhotoResponseDto;
import local.epul4a.fotoshare.model.Album;
import local.epul4a.fotoshare.model.PERMISSION;
import local.epul4a.fotoshare.model.Photo;
import local.epul4a.fotoshare.model.User;
import local.epul4a.fotoshare.repository.UserRepository;
import local.epul4a.fotoshare.service.AlbumService;
import local.epul4a.fotoshare.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/albums")
@PreAuthorize("isAuthenticated()")
public class AlbumController {

    @Autowired
    private AlbumService albumService;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public String listAlbums(Model model) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        List<Album> albums = albumService.getAlbumsByOwner(userId);
        String username = getCurrentUsername();
        List<AlbumResponseDto> albumDtos = albums.stream()
                .map(album -> {
                    Long photoCount = albumService.countPhotosInAlbum(album.getId());
                    List<Photo> photos = albumService.getPhotosInAlbum(album.getId(), userId);
                    String coverUrl = photos.isEmpty() ? null : "/photos/" + photos.get(0).getId() + "/file";
                    return AlbumResponseDto.fromEntity(album, username, photoCount, coverUrl);
                })
                .collect(Collectors.toList());
        model.addAttribute("albums", albumDtos);
        return "albums/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        return "albums/create";
    }

    @PostMapping("/create")
    public String createAlbum(@RequestParam("name") String name,
                             @RequestParam(value = "description", required = false) String description,
                             RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            Album album = albumService.createAlbum(name, description, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Album créé avec succès !");
            return "redirect:/albums/" + album.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/albums/create";
        }
    }

    @PreAuthorize("@securityService.canAccessAlbum(authentication, #id)")
    @GetMapping("/{id}")
    public String viewAlbum(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        Optional<Album> albumOpt = albumService.getAlbumIfAccessible(id, userId);
        if (albumOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Album introuvable ou accès non autorisé");
            return "redirect:/albums";
        }
        Album album = albumOpt.get();
        String ownerUsername = userRepository.findById(album.getOwner_id())
                .map(User::getUsername)
                .orElse("Inconnu");
        List<Photo> photos = albumService.getPhotosInAlbum(id, userId);
        List<PhotoResponseDto> photoDtos = photos.stream()
                .map(photo -> {
                    PERMISSION permission = photoService.getEffectivePermission(photo.getId(), userId);
                    return PhotoResponseDto.fromEntity(photo, ownerUsername, permission);
                })
                .collect(Collectors.toList());
        Long photoCount = albumService.countPhotosInAlbum(id);
        String coverUrl = photos.isEmpty() ? null : "/photos/" + photos.get(0).getId() + "/file";
        AlbumResponseDto albumDto = AlbumResponseDto.fromEntity(album, ownerUsername, photoCount, coverUrl);
        List<Photo> userPhotos = photoService.getPhotosByOwner(userId);
        List<PhotoResponseDto> userPhotoDtos = userPhotos.stream()
                .filter(p -> !photos.stream().anyMatch(ap -> ap.getId().equals(p.getId())))
                .map(photo -> PhotoResponseDto.fromEntity(photo, ownerUsername, PERMISSION.ADMIN))
                .collect(Collectors.toList());
        model.addAttribute("album", albumDto);
        model.addAttribute("photos", photoDtos);
        model.addAttribute("availablePhotos", userPhotoDtos);
        model.addAttribute("isOwner", album.getOwner_id().equals(userId));
        return "albums/view";
    }

    @PreAuthorize("@securityService.canEditAlbum(authentication, #id)")
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        Optional<Album> albumOpt = albumService.getAlbumIfAccessible(id, userId);
        if (albumOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Album introuvable");
            return "redirect:/albums";
        }
        Album album = albumOpt.get();
        if (!album.getOwner_id().equals(userId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vous n'êtes pas le propriétaire de cet album");
            return "redirect:/albums";
        }
        model.addAttribute("album", album);
        return "albums/edit";
    }

    @PreAuthorize("@securityService.canEditAlbum(authentication, #id)")
    @PostMapping("/{id}/edit")
    public String updateAlbum(@PathVariable Long id,
                             @RequestParam("name") String name,
                             @RequestParam(value = "description", required = false) String description,
                             RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            albumService.updateAlbum(id, name, description, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Album mis à jour avec succès !");
        } catch (SecurityException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/albums/" + id;
    }

    @PreAuthorize("@securityService.canDeleteAlbum(authentication, #id)")
    @PostMapping("/{id}/delete")
    public String deleteAlbum(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            albumService.deleteAlbum(id, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Album supprimé avec succès");
        } catch (SecurityException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/albums/" + id;
        }
        return "redirect:/albums";
    }

    @PreAuthorize("@securityService.isAlbumOwner(authentication, #albumId)")
    @PostMapping("/{albumId}/photos/add")
    public String addPhotoToAlbum(@PathVariable Long albumId,
                                  @RequestParam("photoId") Long photoId,
                                  RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            albumService.addPhotoToAlbum(albumId, photoId, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Photo ajoutée à l'album");
        } catch (SecurityException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/albums/" + albumId;
    }

    @PreAuthorize("@securityService.isAlbumOwner(authentication, #albumId)")
    @PostMapping("/{albumId}/photos/{photoId}/remove")
    public String removePhotoFromAlbum(@PathVariable Long albumId,
                                       @PathVariable Long photoId,
                                       RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return "redirect:/login";
        }
        try {
            albumService.removePhotoFromAlbum(albumId, photoId, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Photo retirée de l'album");
        } catch (SecurityException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/albums/" + albumId;
    }

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

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getName();
    }
}
