package com.localmediakit.media;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The creator's showcase: the work itself, which the page could not show before.
 *
 * <p>Same shape as the rate card and the collaborations, deliberately -- it is
 * an ordered list owned by a kit that freezes into the snapshot at publish, and
 * three of those should not be three different designs.
 */
@Service
public class MediaService {

    private final MediaItemRepository repository;
    private final MediaKitAccess access;
    private final int maxPerKit;

    public MediaService(MediaItemRepository repository,
                        MediaKitAccess access,
                        @Value("${app.media.max-per-kit:12}") int maxPerKit) {
        this.repository = repository;
        this.access = access;
        this.maxPerKit = maxPerKit;
    }

    @Transactional
    public MediaResponse create(String userEmail, Long kitId, MediaRequest request) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        if (repository.countByMediaKitId(kit.getId()) >= maxPerKit) {
            throw new TooManyMediaItemsException(maxPerKit);
        }
        MediaItem item = new MediaItem(
                kit.getId(), request.title().trim(), request.url().trim(),
                request.thumbnailUrl(), request.platform(), request.note(),
                request.displayOrderOrDefault());
        repository.save(item);
        return MediaResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> list(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return listForKit(kit.getId()).stream().map(MediaResponse::from).toList();
    }

    @Transactional
    public MediaResponse update(String userEmail, Long kitId, Long itemId, MediaRequest request) {
        MediaItem item = requireOwnedItem(userEmail, kitId, itemId);
        item.update(request.title().trim(), request.url().trim(), request.thumbnailUrl(),
                request.platform(), request.note(), request.displayOrderOrDefault());
        return MediaResponse.from(item);
    }

    @Transactional
    public void delete(String userEmail, Long kitId, Long itemId) {
        repository.delete(requireOwnedItem(userEmail, kitId, itemId));
    }

    /** Display order; also what the publish flow freezes into the snapshot. */
    @Transactional(readOnly = true)
    public List<MediaItem> listForKit(Long kitId) {
        return repository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(kitId);
    }

    /** Two-level guard: the kit must be the caller's, the item must be the kit's. */
    private MediaItem requireOwnedItem(String userEmail, Long kitId, Long itemId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return repository.findByIdAndMediaKitId(itemId, kit.getId())
                .orElseThrow(MediaItemNotFoundException::new);
    }
}
