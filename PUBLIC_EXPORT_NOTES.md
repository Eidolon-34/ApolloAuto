# Public snapshot differences

This source snapshot is based on the v108 radio candidate, with two public-export-only changes:

- The custom E1D1C TTF is omitted because redistribution provenance was not established. Its saved preference falls back to Android system sans. Kenney fonts remain with the pack's CC0 license.
- The MusicDNA-adapted spiral renderer is omitted. Its gallery entry is removed; saved DNA_SPIRAL selections render Apollo's original helix instead. Other original DNA aesthetics remain. This avoids misrepresenting a CC BY-NC-SA adaptation as Apache-only code.

The private working checkout and delivered v108 APK remain unchanged. Public source is therefore not byte-identical to that APK. This snapshot does not include that APK, historical commits/contact metadata, QA screenshots, machine paths, signing material, or local data.

Existing copyright and third-party attribution files remain. This is a focused publication review, not a comprehensive legal certification. Existing Android Auto voice-search lint finding remains unresolved.

Validation: this separate export passed 87 JVM tests (zero failures/errors/skips) and debug APK assembly. No hardware execution. Export-specific lint was not rerun; baseline lint retains the documented Android Auto voice-search error.
