package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.AiProviderConfig;
import com.studydeck.domain.model.KeyHint;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAiProvider;
import com.studydeck.domain.model.UserAiProviderId;
import com.studydeck.domain.port.in.DeleteUserAiProviderUseCase;
import com.studydeck.domain.port.in.GetActiveUserAiProviderQuery;
import com.studydeck.domain.port.in.ListUserAiProvidersQuery;
import com.studydeck.domain.port.in.SaveUserAiProviderUseCase;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.CryptoPort;
import com.studydeck.domain.port.out.IdGenerator;
import com.studydeck.domain.port.out.UserAiProviderRepository;
import java.util.List;
import java.util.Optional;

/**
 * Application service implementing all four AI provider input ports.
 *
 * <p>This is the ONLY transient-plaintext boundary: plaintext keys are decrypted here for immediate
 * use and never stored anywhere else. The domain aggregate holds ciphertext only.
 *
 * <p>Framework-free — no Spring, no Jakarta imports (ArchUnit enforces application layer purity).
 */
public class UserAiProviderService
    implements SaveUserAiProviderUseCase,
        ListUserAiProvidersQuery,
        DeleteUserAiProviderUseCase,
        GetActiveUserAiProviderQuery {

  private final UserAiProviderRepository repository;
  private final CryptoPort cryptoPort;
  private final IdGenerator idGenerator;
  private final ClockPort clock;

  public UserAiProviderService(
      UserAiProviderRepository repository,
      CryptoPort cryptoPort,
      IdGenerator idGenerator,
      ClockPort clock) {
    this.repository = repository;
    this.cryptoPort = cryptoPort;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  // -----------------------------------------------------------------------
  // SaveUserAiProviderUseCase
  // -----------------------------------------------------------------------

  @Override
  public SaveUserAiProviderUseCase.Result save(SaveUserAiProviderUseCase.Command cmd) {
    UserAiProvider provider;

    if (cmd.idOrNull() == null) {
      // CREATE — must encrypt key (throws CryptoUnavailableException if disabled)
      String ciphertext = cryptoPort.encrypt(cmd.plaintextApiKey());
      String hint = KeyHint.compute(cmd.plaintextApiKey());
      var now = clock.now();
      provider =
          UserAiProvider.create(
              new UserAiProviderId(idGenerator.generate()),
              cmd.ownerId(),
              cmd.label(),
              cmd.baseUrl(),
              cmd.model(),
              ciphertext,
              hint,
              false,
              now,
              now);
    } else {
      // UPDATE — find existing or throw NotFoundException
      provider =
          repository
              .findByIdAndOwner(cmd.idOrNull(), cmd.ownerId())
              .orElseThrow(
                  () -> new NotFoundException("UserAiProvider", cmd.idOrNull().toString()));

      provider.updateConfig(cmd.label(), cmd.baseUrl(), cmd.model(), clock.now());

      // Only re-encrypt if a new key was supplied
      if (cmd.plaintextApiKey() != null && !cmd.plaintextApiKey().isBlank()) {
        String newCiphertext = cryptoPort.encrypt(cmd.plaintextApiKey());
        String newHint = KeyHint.compute(cmd.plaintextApiKey());
        provider.withCiphertext(newCiphertext, newHint);
      }
    }

    // At-most-one-active: deactivate all, then mark this one active
    if (cmd.setActive()) {
      repository.deactivateAllForOwner(cmd.ownerId());
      provider.activate();
    }

    repository.save(provider);

    return new SaveUserAiProviderUseCase.Result(
        provider.getId(),
        provider.getLabel(),
        provider.getBaseUrl(),
        provider.getModel(),
        provider.getKeyHint(),
        provider.isActive(),
        provider.getCreatedAt(),
        provider.getUpdatedAt());
  }

  // -----------------------------------------------------------------------
  // ListUserAiProvidersQuery
  // -----------------------------------------------------------------------

  @Override
  public List<ListUserAiProvidersQuery.Masked> list(OwnerId ownerId) {
    return repository.findAllByOwner(ownerId).stream()
        .map(
            p ->
                new ListUserAiProvidersQuery.Masked(
                    p.getId(),
                    p.getLabel(),
                    p.getBaseUrl(),
                    p.getModel(),
                    p.getKeyHint(),
                    p.isActive(),
                    p.getCreatedAt(),
                    p.getUpdatedAt()))
        .toList();
  }

  // -----------------------------------------------------------------------
  // DeleteUserAiProviderUseCase
  // -----------------------------------------------------------------------

  @Override
  public void execute(DeleteUserAiProviderUseCase.Command cmd) {
    // Verify ownership first — throws NotFoundException for cross-owner or absent
    repository
        .findByIdAndOwner(cmd.providerId(), cmd.ownerId())
        .orElseThrow(() -> new NotFoundException("UserAiProvider", cmd.providerId().toString()));
    repository.deleteByIdAndOwner(cmd.providerId(), cmd.ownerId());
  }

  // Alias for backward-compat in tests
  public void delete(DeleteUserAiProviderUseCase.Command cmd) {
    execute(cmd);
  }

  // -----------------------------------------------------------------------
  // GetActiveUserAiProviderQuery
  // -----------------------------------------------------------------------

  @Override
  public Optional<AiProviderConfig> execute(OwnerId ownerId) {
    return repository
        .findActiveByOwner(ownerId)
        .map(
            p ->
                new AiProviderConfig(
                    p.getBaseUrl(), cryptoPort.decrypt(p.getApiKeyCiphertext()), p.getModel()));
  }

  // Alias used in test — delegate to execute()
  public Optional<AiProviderConfig> getActive(OwnerId ownerId) {
    return execute(ownerId);
  }
}
