package com.localmediakit.ratecard;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RateCardService {

    private final RateCardItemRepository repository;
    private final MediaKitAccess access;

    public RateCardService(RateCardItemRepository repository, MediaKitAccess access) {
        this.repository = repository;
        this.access = access;
    }

    @Transactional
    public RateCardResponse create(String userEmail, Long kitId, RateCardRequest request) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        RateCardItem item = new RateCardItem(
                kit.getId(), request.serviceName().trim(), request.priceAmount(),
                request.currencyOrDefault(), request.note(), request.displayOrderOrDefault());
        repository.save(item);
        return RateCardResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<RateCardResponse> list(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return listForKit(kit.getId()).stream().map(RateCardResponse::from).toList();
    }

    @Transactional
    public RateCardResponse update(String userEmail, Long kitId, Long itemId, RateCardRequest request) {
        RateCardItem item = requireOwnedItem(userEmail, kitId, itemId);
        item.update(request.serviceName().trim(), request.priceAmount(),
                request.currencyOrDefault(), request.note(), request.displayOrderOrDefault());
        return RateCardResponse.from(item);
    }

    @Transactional
    public void delete(String userEmail, Long kitId, Long itemId) {
        repository.delete(requireOwnedItem(userEmail, kitId, itemId));
    }

    /** Display order; also used by the publish flow to freeze the list into the snapshot. */
    @Transactional(readOnly = true)
    public List<RateCardItem> listForKit(Long kitId) {
        return repository.findByMediaKitIdOrderByDisplayOrderAscIdAsc(kitId);
    }

    /** Two-level guard: the kit must be the caller's, the item must be the kit's. */
    private RateCardItem requireOwnedItem(String userEmail, Long kitId, Long itemId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return repository.findByIdAndMediaKitId(itemId, kit.getId())
                .orElseThrow(RateCardItemNotFoundException::new);
    }
}
