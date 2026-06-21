package com.studydeck.application.service;

import com.studydeck.application.common.PageRequest;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SourceDocument;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.ExportAccountUseCase;
import com.studydeck.domain.port.in.ExportDeckUseCase;
import com.studydeck.domain.port.in.ListDecksQuery;
import com.studydeck.domain.port.in.ListDocumentsQuery;
import com.studydeck.domain.port.in.ValidateImportUseCase;
import com.studydeck.domain.port.out.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service implementing GDPR data portability.
 *
 * <p>Aggregates the caller's account, deck exports (reusing {@link ExportDeckUseCase}), and source
 * documents into a single {@link ExportAccountUseCase.Result}.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.BeanConfiguration}.
 */
public final class ExportAccountService implements ExportAccountUseCase {

  private static final int PAGE_SIZE = 100;

  private final UserAccountRepository userAccountRepository;
  private final ListDecksQuery listDecksQuery;
  private final ExportDeckUseCase exportDeckUseCase;
  private final ListDocumentsQuery listDocumentsQuery;

  public ExportAccountService(
      UserAccountRepository userAccountRepository,
      ListDecksQuery listDecksQuery,
      ExportDeckUseCase exportDeckUseCase,
      ListDocumentsQuery listDocumentsQuery) {
    this.userAccountRepository = userAccountRepository;
    this.listDecksQuery = listDecksQuery;
    this.exportDeckUseCase = exportDeckUseCase;
    this.listDocumentsQuery = listDocumentsQuery;
  }

  @Override
  public Result execute(OwnerId ownerId) {
    UserAccount account =
        userAccountRepository
            .findById(ownerId)
            .orElseThrow(() -> new NotFoundException("UserAccount", ownerId.toString()));

    List<ValidateImportUseCase.ImportPayload> deckPayloads = exportAllDecks(ownerId);
    List<SourceDocument> documents = listAllDocuments(ownerId);

    return new Result(account, deckPayloads, documents, Instant.now());
  }

  // ---------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------

  private List<ValidateImportUseCase.ImportPayload> exportAllDecks(OwnerId ownerId) {
    List<ValidateImportUseCase.ImportPayload> payloads = new ArrayList<>();
    int page = 0;
    while (true) {
      var query = new ListDecksQuery.Query(ownerId, true, null, PageRequest.of(page, PAGE_SIZE));
      var result = listDecksQuery.execute(query);
      for (Deck deck : result.content()) {
        payloads.add(
            exportDeckUseCase.execute(new ExportDeckUseCase.Command(ownerId, deck.getId())));
      }
      if (!result.hasNext()) {
        break;
      }
      page++;
    }
    return payloads;
  }

  private List<SourceDocument> listAllDocuments(OwnerId ownerId) {
    if (listDocumentsQuery == null) {
      return List.of();
    }
    List<SourceDocument> all = new ArrayList<>();
    int offset = 0;
    while (true) {
      var result = listDocumentsQuery.execute(ownerId, null, offset, PAGE_SIZE);
      all.addAll(result.items());
      if (all.size() >= result.total()) {
        break;
      }
      offset += PAGE_SIZE;
    }
    return all;
  }
}
