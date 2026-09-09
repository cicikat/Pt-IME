# Third-party components

The root PolyForm Noncommercial license applies only to original Pt JadeBoard code and documentation. It does **not** change or restrict the licenses of the independently distributed components below. Their original licenses and notices remain applicable, including their commercial-use rights where granted.

| Component | License / source |
|---|---|
| `app/src/main/assets/lexicon.db` dictionary data | Derived from [rime-ice](https://github.com/iDvel/rime-ice), GPL-3.0-only. See `docs/licenses/rime-ice-GPL-3.0.txt`. The exact cached YAML inputs and conversion script are distributed in `Pt-JadeBoard-v1.0.0-lexicon-source.zip` alongside the APK. Original source headers/attributions are preserved. This dictionary data is excluded from the root PolyForm license. |
| sherpa-onnx 1.13.7 AAR | [Upstream release/source](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.7), Apache-2.0; `docs/licenses/sherpa-onnx-LICENSE.txt`. Native libraries in the AAR are not modified. |
| Streaming Zipformer bilingual zh-en model | [Pinned upstream model](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/tree/98590b7ed6443e77b714204da2757d75e1a642f4), upstream model card declares Apache-2.0; card in `docs/licenses/speech-model-card.md`. Download URLs and hashes in `tools/voice_assets.json`. Model files are unmodified. |
| ONNX Runtime (bundled native runtime) | [Microsoft ONNX Runtime](https://github.com/microsoft/onnxruntime), MIT; `docs/licenses/onnxruntime-LICENSE.txt`. |
| AndroidX / Jetpack Compose / Room | [Android Open Source Project](https://android.googlesource.com/platform/frameworks/support/), Apache-2.0. |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | [JetBrains Kotlin](https://github.com/JetBrains/kotlin), Apache-2.0. |
| OkHttp / Okio | [Square](https://github.com/square/okhttp), Apache-2.0. |

Architecture references such as FlorisBoard and HeliBoard are acknowledgements, not relicensing of those projects. The rime dictionary and native speech libraries are separate third-party assets, not code claimed as original JadeBoard code. Redistribution of a modified build must preserve each component's terms and provide corresponding source where required.

The APK carries a copy of these notices and license texts in `assets/legal/`; full original program source is available at the release tag. The separate dictionary source archive must accompany distributions of the provided dictionary.
