package com.studydeck.domain.port.in;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.OwnerId;

/** Input port — permanently deletes a single Card. */
public interface DeleteCardUseCase {

  /**
   * Deletes a Card.
   *
   * @param command (non-null)
   * @throws com.studydeck.application.exception.NotFoundException if the card is not found or its
   *     deck is not owned by the caller
   */
  void execute(Command command);

  /**
   * Command parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param cardId id of the Card to delete (non-null)
   */
  record Command(OwnerId ownerId, CardId cardId) {

    public Command {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (cardId == null) {
        throw new IllegalArgumentException("cardId must not be null");
      }
    }
  }
}
