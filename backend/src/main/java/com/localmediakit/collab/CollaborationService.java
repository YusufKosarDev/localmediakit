package com.localmediakit.collab;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CollaborationService {

    private final BrandCollaborationRepository repository;
    private final MediaKitAccess access;

    public CollaborationService(BrandCollaborationRepository repository, MediaKitAccess access) {
        this.repository = repository;
        this.access = access;
    }

    @Transactional
    public CollaborationResponse create(String userEmail, Long kitId, CollaborationRequest request) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        BrandCollaboration collab = new BrandCollaboration(
                kit.getId(), request.brandName().trim(), request.campaign(), request.period(),
                request.resultNote(), request.logoUrl(), request.displayOrderOrDefault());
        repository.save(collab);
        return CollaborationResponse.from(collab);
    }

    @Transactional(readOnly = true)
    public List<CollaborationResponse> list(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return listForKit(kit.getId()).stream().map(CollaborationResponse::from).toList();
    }

    @Transactional
    public CollaborationResponse update(String userEmail, Long kitId, Long collabId,
                                        CollaborationRequest request) {
        BrandCollaboration collab = requireOwnedCollab(userEmail, kitId, collabId);
        collab.update(request.brandName().trim(), request.campaign(), request.period(),
                request.resultNote(), request.logoUrl(), request.displayOrderOrDefault());
        return CollaborationResponse.from(collab);
    }

    @Transactional
    public void delete(String userEmail, Long kitId, Long collabId) {
        repository.delete(requireOwnedCollab(userEmail, kitId, collabId));
    }

    /** Showcase order; also used by the publish flow to freeze the list into the snapshot. */
    @Transactional(readOnly = true)
    public List<BrandCollaboration> listForKit(Long kitId) {
        return repository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(kitId);
    }

    /** Two-level guard: the kit must be the caller's, the collab must be the kit's. */
    private BrandCollaboration requireOwnedCollab(String userEmail, Long kitId, Long collabId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return repository.findByIdAndMediaKitId(collabId, kit.getId())
                .orElseThrow(CollaborationNotFoundException::new);
    }
}
