package com.studydeck.application.service;

import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.port.in.ListNoteTypesQuery;
import java.util.List;

/**
 * Application service implementing {@link ListNoteTypesQuery}.
 *
 * <p>Returns static descriptors for all P0-supported note types. No persistence required.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 */
public final class NoteTypeService implements ListNoteTypesQuery {

  @Override
  public List<NoteTypeDescriptor> execute() {
    return List.of(
        new NoteTypeDescriptor(
            NoteType.BASIC,
            "Basic flashcard",
            "A single front-and-back card. Generates one card per note.",
            List.of(
                new FieldDescriptor("front", true, 1000, "The question side of the card"),
                new FieldDescriptor("back", true, 5000, "The answer side of the card"))),
        new NoteTypeDescriptor(
            NoteType.REVERSED,
            "Reversed flashcard",
            "Same as Basic but also generates a reverse card (back→front). Generates two cards per"
                + " note.",
            List.of(
                new FieldDescriptor("front", true, 1000, "The primary question side"),
                new FieldDescriptor("back", true, 5000, "The primary answer side"))),
        new NoteTypeDescriptor(
            NoteType.CLOZE,
            "Cloze deletion",
            "Fill-in-the-blank note. Generates one card per distinct {{cN::...}} deletion number.",
            List.of(
                new FieldDescriptor(
                    "text",
                    true,
                    5000,
                    "Text with {{cN::deletion}} markers (e.g. '{{c1::Java}} runs on {{c2::JVM}}')"))),
        new NoteTypeDescriptor(
            NoteType.MULTIPLE_CHOICE,
            "Multiple choice",
            "Question with 4-5 options and one or more correct answers. Generates one card.",
            List.of(
                new FieldDescriptor("question", true, 2000, "The question text"),
                new FieldDescriptor(
                    "options",
                    true,
                    0,
                    "Array of 4-5 options each with key (A-Z) and text (max 1000 chars)"),
                new FieldDescriptor(
                    "correctOptionKeys", true, 0, "Array of correct option keys (at least 1)"),
                new FieldDescriptor(
                    "explanation", false, 2000, "Optional explanation of the correct answer"))),
        new NoteTypeDescriptor(
            NoteType.FREE_TEXT,
            "Free text",
            "Open-ended question with expected answer and optional grading guidance. Generates one"
                + " card.",
            List.of(
                new FieldDescriptor("prompt", true, 2000, "The open-ended question or prompt"),
                new FieldDescriptor(
                    "expectedAnswer", true, 5000, "The reference or expected answer"),
                new FieldDescriptor(
                    "gradingGuidance", false, 2000, "Optional rubric or grading criteria"))));
  }
}
